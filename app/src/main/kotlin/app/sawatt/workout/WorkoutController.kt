package app.sawatt.workout

import android.content.Context
import android.os.SystemClock
import app.sawatt.core.ride.RideMath
import app.sawatt.core.trainer.TrainerConnection
import app.sawatt.core.workout.LiveState
import app.sawatt.core.workout.SessionEvent
import app.sawatt.core.workout.WorkoutPlan
import app.sawatt.core.workout.WorkoutRecord
import app.sawatt.core.workout.WorkoutSession
import app.sawatt.data.HistoryRepository
import app.sawatt.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class WorkoutStatus { IDLE, RUNNING, PAUSED }

data class WorkoutUiState(
    val status: WorkoutStatus = WorkoutStatus.IDLE,
    val plan: WorkoutPlan? = null,
    val live: LiveState? = null,
)

/** Latest raw sensor values, shown even when no workout is running. */
data class SensorReadings(val powerW: Int = 0, val cadenceRpm: Int = 0, val heartRate: Int = 0)

/**
 * Owns the running [WorkoutSession]: feeds it sensor data, ticks it once per second of riding
 * time, beeps on interval changes, and stores the result in the history when finished.
 */
class WorkoutController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val trainer: TrainerConnection,
    private val settings: SettingsRepository,
    private val history: HistoryRepository,
) {
    private val beeper = Beeper()
    private var session: WorkoutSession? = null
    private var ticker: Job? = null

    private val _state = MutableStateFlow(WorkoutUiState())
    val state: StateFlow<WorkoutUiState> = _state.asStateFlow()

    private val _readings = MutableStateFlow(SensorReadings())
    val readings: StateFlow<SensorReadings> = _readings.asStateFlow()

    /** The workout that was just finished and saved, for the summary screen. */
    private val _lastFinished = MutableStateFlow<WorkoutRecord?>(null)
    val lastFinished: StateFlow<WorkoutRecord?> = _lastFinished.asStateFlow()

    private var lastBikeAt = 0L
    private var lastHrAt = 0L

    init {
        scope.launch {
            trainer.bikeSamples.collect { s ->
                lastBikeAt = SystemClock.elapsedRealtime()
                _readings.value = _readings.value.copy(powerW = s.powerW, cadenceRpm = s.cadenceRpm)
                if (_state.value.status == WorkoutStatus.RUNNING) session?.onBikeSample(s.powerW, s.cadenceRpm)
            }
        }
        scope.launch {
            trainer.heartRates.collect { bpm ->
                lastHrAt = SystemClock.elapsedRealtime()
                _readings.value = _readings.value.copy(heartRate = bpm)
                if (_state.value.status == WorkoutStatus.RUNNING) session?.onHeartRate(bpm)
            }
        }
        // Zero the idle readings when sensors go quiet (the bike stops notifying when not pedalled).
        scope.launch {
            while (isActive) {
                delay(1_000)
                val now = SystemClock.elapsedRealtime()
                var r = _readings.value
                if (now - lastBikeAt > STALE_MS) r = r.copy(powerW = 0, cadenceRpm = 0)
                if (now - lastHrAt > STALE_MS) r = r.copy(heartRate = 0)
                _readings.value = r
            }
        }
    }

    fun start(plan: WorkoutPlan?) {
        if (_state.value.status != WorkoutStatus.IDLE) return
        val profile = settings.profile.value
        val s = WorkoutSession(
            plan = plan,
            weightKg = profile.weightKg,
            ftpW = RideMath.effectiveFtp(profile),
            startEpochMs = System.currentTimeMillis(),
        )
        session = s
        _lastFinished.value = null
        _state.value = WorkoutUiState(WorkoutStatus.RUNNING, plan, s.snapshot())
        startTicker()
        WorkoutService.start(context)
    }

    fun pause() {
        if (_state.value.status != WorkoutStatus.RUNNING) return
        ticker?.cancel()
        _state.value = _state.value.copy(status = WorkoutStatus.PAUSED)
    }

    fun resume() {
        if (_state.value.status != WorkoutStatus.PAUSED) return
        _state.value = _state.value.copy(status = WorkoutStatus.RUNNING)
        startTicker()
    }

    fun lap() {
        val s = session ?: return
        if (_state.value.status != WorkoutStatus.RUNNING) return
        handle(s.lap())
        publish(s)
    }

    /** Stops the workout and saves it. Returns the saved record, or null if nothing was ridden. */
    fun finish(): WorkoutRecord? {
        val s = session ?: return null
        ticker?.cancel()
        val (record, samples) = s.finish()
        session = null
        _state.value = WorkoutUiState()
        WorkoutService.stop(context)
        if (record.summary.durationSec == 0) return null
        history.save(record, samples)
        _lastFinished.value = record
        return record
    }

    fun discard() {
        ticker?.cancel()
        session = null
        _state.value = WorkoutUiState()
        WorkoutService.stop(context)
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            // Drift-free 1 Hz clock based on the monotonic clock.
            val origin = SystemClock.elapsedRealtime()
            var n = 1L
            while (isActive) {
                val wait = origin + n * 1_000 - SystemClock.elapsedRealtime()
                if (wait > 0) delay(wait)
                val s = session ?: break
                handle(s.tick())
                publish(s)
                n++
            }
        }
    }

    private fun publish(s: WorkoutSession) {
        _state.value = _state.value.copy(live = s.snapshot())
    }

    private fun handle(events: List<SessionEvent>) {
        val app = settings.app.value
        when {
            !app.transitionBeep && !app.countdownBeeps -> return
            app.transitionBeep && events.any { it is SessionEvent.PlanCompleted } -> beeper.planCompleted()
            app.transitionBeep && events.any { it is SessionEvent.IntervalChanged } -> beeper.transition()
            app.countdownBeeps && events.any { it is SessionEvent.Countdown } -> beeper.countdown()
        }
    }

    private companion object {
        const val STALE_MS = 3_000L
    }
}
