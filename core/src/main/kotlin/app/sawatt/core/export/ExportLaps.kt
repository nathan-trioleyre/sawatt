package app.sawatt.core.export

import app.sawatt.core.ride.RideMath
import app.sawatt.core.workout.StatsSummary
import app.sawatt.core.workout.WorkoutRecord
import app.sawatt.core.workout.WorkoutSamples

/** One exported lap: an interval of the workout, as a range of 1 s samples. */
internal data class ExportLap(val name: String, val startSec: Int, val durationSec: Int, val stats: StatsSummary)

/** Laps of a workout: one per interval, or a single lap when the record has no intervals. */
internal fun lapsOf(record: WorkoutRecord, samples: WorkoutSamples): List<ExportLap> {
    val total = samples.powerW.size
    val laps = record.intervals
        .map { ExportLap(it.name, it.startOffsetSec, it.stats.durationSec, it.stats) }
        .filter { it.durationSec > 0 && it.startSec < total }
    return laps.ifEmpty { listOf(ExportLap(record.planName ?: "Workout", 0, total, record.summary)) }
}

/** Speed of second [i] in m/s, from the same virtual speed model as the rest of the app. */
internal fun WorkoutSamples.speedMs(i: Int): Double = RideMath.speedKmh(powerW[i].toDouble()) / 3.6

/** Cumulative distance in metres at the end of each second. */
internal fun WorkoutSamples.cumulativeDistanceM(): DoubleArray {
    val out = DoubleArray(powerW.size)
    var d = 0.0
    for (i in powerW.indices) {
        d += speedMs(i)
        out[i] = d
    }
    return out
}
