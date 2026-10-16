package app.sawatt.core.history

import app.sawatt.core.workout.WorkoutRecord
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.IsoFields

enum class PeriodKind { WEEK, MONTH, YEAR }

/** Identifies a calendar period. For weeks, [year] is the ISO week-based year. */
data class PeriodKey(val kind: PeriodKind, val year: Int, val number: Int)

data class PeriodStats(
    val key: PeriodKey,
    val workouts: Int,
    val durationSec: Long,
    val distanceKm: Double,
    val kcal: Double,
    /** Duration-weighted average power over all workouts in the period. */
    val avgPowerW: Double,
    val maxPowerW: Int,
    val avgCadenceRpm: Double,
    val maxCadenceRpm: Int,
    val avgHeartRate: Double?,
    val maxHeartRate: Int?,
    val zoneSeconds: List<Int>,
)

data class Totals(
    val workouts: Int,
    val durationSec: Long,
    val distanceKm: Double,
    val kcal: Double,
    val longestSec: Int,
)

/**
 * Agrégation de l'historique par semaine / mois / année.
 *
 * Les semaines ISO sont rattachées à l'année de la semaine ISO (une séance du 30 décembre peut
 * appartenir à la semaine 1 de l'année suivante), et les moyennes sont pondérées par la durée
 * des séances plutôt que de faire la moyenne des moyennes.
 */
object HistoryStats {
    fun totals(records: List<WorkoutRecord>) = Totals(
        workouts = records.size,
        durationSec = records.sumOf { it.summary.durationSec.toLong() },
        distanceKm = records.sumOf { it.summary.distanceKm },
        kcal = records.sumOf { it.summary.kcal },
        longestSec = records.maxOfOrNull { it.summary.durationSec } ?: 0,
    )

    /** Periods newest first. */
    fun byPeriod(
        records: List<WorkoutRecord>,
        kind: PeriodKind,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<PeriodStats> =
        records.groupBy { keyOf(it.startEpochMs, kind, zone) }
            .map { (key, group) -> aggregate(key, group) }
            .sortedWith(compareByDescending<PeriodStats> { it.key.year }.thenByDescending { it.key.number })

    fun keyOf(epochMs: Long, kind: PeriodKind, zone: ZoneId): PeriodKey {
        val date = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
        return when (kind) {
            PeriodKind.WEEK -> PeriodKey(kind, date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
            PeriodKind.MONTH -> PeriodKey(kind, date.year, date.monthValue)
            PeriodKind.YEAR -> PeriodKey(kind, date.year, 0)
        }
    }

    private fun aggregate(key: PeriodKey, group: List<WorkoutRecord>): PeriodStats {
        val duration = group.sumOf { it.summary.durationSec.toLong() }
        fun weighted(f: (WorkoutRecord) -> Double) =
            if (duration == 0L) 0.0 else group.sumOf { f(it) * it.summary.durationSec } / duration
        val withHr = group.filter { it.summary.avgHeartRate != null }
        val hrDuration = withHr.sumOf { it.summary.durationSec.toLong() }
        val zones = IntArray(5)
        group.forEach { r -> r.summary.zoneSeconds.forEachIndexed { i, s -> if (i < 5) zones[i] += s } }
        return PeriodStats(
            key = key,
            workouts = group.size,
            durationSec = duration,
            distanceKm = group.sumOf { it.summary.distanceKm },
            kcal = group.sumOf { it.summary.kcal },
            avgPowerW = weighted { it.summary.avgPowerW },
            maxPowerW = group.maxOf { it.summary.maxPowerW },
            avgCadenceRpm = weighted { it.summary.avgCadenceRpm },
            maxCadenceRpm = group.maxOf { it.summary.maxCadenceRpm },
            avgHeartRate = if (hrDuration == 0L) null
            else withHr.sumOf { it.summary.avgHeartRate!! * it.summary.durationSec } / hrDuration,
            maxHeartRate = withHr.mapNotNull { it.summary.maxHeartRate }.maxOrNull(),
            zoneSeconds = zones.toList(),
        )
    }
}
