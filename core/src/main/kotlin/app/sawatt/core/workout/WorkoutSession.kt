package app.sawatt.core.workout

import app.sawatt.core.ride.RideMath
import kotlinx.serialization.Serializable

@Serializable
enum class IntervalKind {
    /** A block from the workout plan. */
    PLANNED,

    /** A segment split by the Lap button (free ride, or after the plan has finished). */
    LAP,
}

@Serializable
data class IntervalRecord(
    /** 1-based position in the workout. */
    val index: Int,
    val name: String,
    val kind: IntervalKind,
    /** Offset of the first second of this interval from the start of the workout (riding time). */
    val startOffsetSec: Int,
    val targetPowerW: Int? = null,
    val targetCadenceRpm: Int? = null,
    val plannedDurationSec: Int? = null,
    /** True when a planned block was cut short with the Lap button or by finishing the workout. */
    val endedEarly: Boolean = false,
    val stats: StatsSummary,
)

/** Live view of the interval being ridden. */
data class CurrentInterval(
    val index: Int,
    val name: String,
    val kind: IntervalKind,
    val elapsedSec: Int,
    val plannedDurationSec: Int?,
    val targetPowerW: Int?,
    val targetCadenceRpm: Int?,
    val stats: StatsSummary,
) {
    val remainingSec: Int? get() = plannedDurationSec?.let { (it - elapsedSec).coerceAtLeast(0) }
}

data class LiveState(
    val elapsedSec: Int,
    val powerW: Int,
    val cadenceRpm: Int,
    val heartRate: Int,
    val session: StatsSummary,
    val current: CurrentInterval,
    val next: PlannedInterval?,
    val completed: List<IntervalRecord>,
    val plannedCount: Int,
    val planFinished: Boolean,
)

sealed interface SessionEvent {
    /** A new interval started; [finished] is the one that just closed (null if it was empty). */
    data class IntervalChanged(val finished: IntervalRecord?, val started: CurrentInterval) : SessionEvent

    /** The last planned block has been completed; the session continues as a free ride. */
    data object PlanCompleted : SessionEvent
}

/** Second-by-second channel data recorded for charts. */
@Serializable
data class WorkoutSamples(
    val powerW: List<Int>,
    val cadenceRpm: List<Int>,
    val heartRate: List<Int>,
)

@Serializable
data class WorkoutRecord(
    val id: String,
    val startEpochMs: Long,
    val planId: String? = null,
    val planName: String? = null,
    val summary: StatsSummary,
    val intervals: List<IntervalRecord>,
)

/**
 * The workout engine. It is clock-free: the host calls [tick] once per second of riding time
 * (not while paused), and feeds sensor readings in between. Each tick turns the readings
 * received during that second into one [Sample] (mean of the readings) and advances the plan.
 */
class WorkoutSession(
    val plan: WorkoutPlan?,
    private val weightKg: Double,
    private val ftpW: Double,
    val startEpochMs: Long,
    val id: String = startEpochMs.toString(),
) {
    private val planned = plan?.flatten().orEmpty()
    private var plannedIndex = 0
    private var planFinished = planned.isEmpty()

    private val sessionStats = StatsAccumulator()
    private val completed = mutableListOf<IntervalRecord>()
    private val powerTrace = ArrayList<Int>()
    private val cadenceTrace = ArrayList<Int>()
    private val hrTrace = ArrayList<Int>()

    private val power = Channel()
    private val cadence = Channel()
    private val heart = Channel()

    private var elapsedSec = 0
    private var lapCounter = 0
    private lateinit var current: OpenInterval

    init {
        current = if (planned.isNotEmpty()) openPlanned(0) else openLap()
    }

    // ---- sensor input -------------------------------------------------------------------

    fun onBikeSample(powerW: Int, cadenceRpm: Int) {
        power.push(powerW.coerceAtLeast(0))
        cadence.push(cadenceRpm.coerceAtLeast(0))
    }

    fun onHeartRate(bpm: Int) {
        if (bpm > 0) heart.push(bpm)
    }

    // ---- clock --------------------------------------------------------------------------

    fun tick(): List<SessionEvent> {
        val p = power.take()
        val c = cadence.take()
        val hr = heart.take()
        val sample = Sample(
            powerW = p,
            cadenceRpm = c,
            heartRate = hr,
            speedKmh = RideMath.speedKmh(p.toDouble()),
            kcalPerHour = RideMath.caloriesPerHour(weightKg, p.toDouble()),
        )
        val zone = RideMath.powerZone(if (ftpW > 0) p / ftpW * 100 else 0.0)
        sessionStats.add(sample, zone)
        current.stats.add(sample, zone)
        current.elapsedSec++
        elapsedSec++
        powerTrace += p
        cadenceTrace += c
        hrTrace += hr

        val planned = current.planned
        if (planned != null && current.elapsedSec >= planned.block.durationSec) {
            return advance(endedEarly = false)
        }
        return emptyList()
    }

    /**
     * Manual lap. During a plan it ends the current block early and moves to the next one;
     * otherwise it splits the free ride. Ignored when the current interval is still empty.
     */
    fun lap(): List<SessionEvent> {
        if (current.elapsedSec == 0) return emptyList()
        return advance(endedEarly = current.planned != null)
    }

    private fun advance(endedEarly: Boolean): List<SessionEvent> {
        val events = mutableListOf<SessionEvent>()
        val finished = close(current, endedEarly)
        if (!planFinished) {
            plannedIndex++
            if (plannedIndex < planned.size) {
                current = openPlanned(plannedIndex)
            } else {
                planFinished = true
                current = openLap()
                events += SessionEvent.PlanCompleted
            }
        } else {
            current = openLap()
        }
        events.add(0, SessionEvent.IntervalChanged(finished, current.toLive()))
        return events
    }

    /** Closes the workout and returns what should be stored in the history. */
    fun finish(): Pair<WorkoutRecord, WorkoutSamples> {
        if (current.elapsedSec > 0) close(current, endedEarly = current.planned != null)
        val record = WorkoutRecord(
            id = id,
            startEpochMs = startEpochMs,
            planId = plan?.id,
            planName = plan?.name,
            summary = sessionStats.summary(),
            intervals = completed.toList(),
        )
        return record to WorkoutSamples(powerTrace.toList(), cadenceTrace.toList(), hrTrace.toList())
    }

    fun snapshot() = LiveState(
        elapsedSec = elapsedSec,
        powerW = power.latest,
        cadenceRpm = cadence.latest,
        heartRate = heart.latest,
        session = sessionStats.summary(),
        current = current.toLive(),
        next = if (!planFinished && plannedIndex + 1 < planned.size) planned[plannedIndex + 1] else null,
        completed = completed.toList(),
        plannedCount = planned.size,
        planFinished = planFinished,
    )

    // ---- internals ----------------------------------------------------------------------

    private class OpenInterval(
        val index: Int,
        val name: String,
        val kind: IntervalKind,
        val startOffsetSec: Int,
        val planned: PlannedInterval?,
    ) {
        val stats = StatsAccumulator()
        var elapsedSec = 0
    }

    private fun openPlanned(i: Int): OpenInterval {
        val p = planned[i]
        return OpenInterval(completed.size + 1, p.label, IntervalKind.PLANNED, elapsedSec, p)
    }

    private fun openLap(): OpenInterval {
        lapCounter++
        val name = if (planned.isEmpty()) "Lap $lapCounter" else "Free ride $lapCounter"
        return OpenInterval(completed.size + 1, name, IntervalKind.LAP, elapsedSec, null)
    }

    private fun close(open: OpenInterval, endedEarly: Boolean): IntervalRecord? {
        if (open.elapsedSec == 0) return null
        val record = IntervalRecord(
            index = completed.size + 1,
            name = open.name,
            kind = open.kind,
            startOffsetSec = open.startOffsetSec,
            targetPowerW = open.planned?.block?.targetPowerW,
            targetCadenceRpm = open.planned?.block?.targetCadenceRpm,
            plannedDurationSec = open.planned?.block?.durationSec,
            endedEarly = endedEarly,
            stats = open.stats.summary(),
        )
        completed += record
        return record
    }

    private fun OpenInterval.toLive() = CurrentInterval(
        index = index,
        name = name,
        kind = kind,
        elapsedSec = elapsedSec,
        plannedDurationSec = planned?.block?.durationSec,
        targetPowerW = planned?.block?.targetPowerW,
        targetCadenceRpm = planned?.block?.targetCadenceRpm,
        stats = stats.summary(),
    )

    /**
     * Readings received during the current second. With no new reading, the previous value is
     * carried for up to [STALE_TICKS] seconds, then the channel drops to 0. Un vélo
     * autoalimenté arrête simplement de notifier when the rider stops, so a stale value must not
     * keep inflating the averages.
     */
    private class Channel {
        private var sum = 0L
        private var count = 0
        private var carried = 0
        private var ticksWithoutData = 0
        var latest = 0
            private set

        fun push(v: Int) {
            sum += v
            count++
            latest = v
        }

        fun take(): Int {
            if (count > 0) {
                carried = Math.round(sum.toDouble() / count).toInt()
                sum = 0
                count = 0
                ticksWithoutData = 0
            } else if (++ticksWithoutData > STALE_TICKS) {
                carried = 0
                latest = 0
            }
            return carried
        }
    }

    companion object {
        const val STALE_TICKS = 3
    }
}
