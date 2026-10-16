package app.sawatt.core.workout

import kotlinx.serialization.Serializable

/** Une seconde de pédalage. */
data class Sample(
    val powerW: Int,
    val cadenceRpm: Int,
    /** 0 when no heart-rate sensor value is available. */
    val heartRate: Int,
    val speedKmh: Double,
    val kcalPerHour: Double,
)

/** Aggregated statistics over a run of 1 s samples (an interval or a whole session). */
@Serializable
data class StatsSummary(
    val durationSec: Int = 0,
    val avgPowerW: Double = 0.0,
    val maxPowerW: Int = 0,
    val avgCadenceRpm: Double = 0.0,
    val maxCadenceRpm: Int = 0,
    /** Null when no heart-rate data was recorded. */
    val avgHeartRate: Double? = null,
    val maxHeartRate: Int? = null,
    val distanceKm: Double = 0.0,
    val kcal: Double = 0.0,
    /** Seconds spent in each of the five power zones (see RideMath.powerZone). */
    val zoneSeconds: List<Int> = List(5) { 0 },
)

/**
 * Running accumulator. Averages include zeros for power and cadence (les secondes en roue libre
 * comptent); heart rate ignores seconds without a reading.
 */
class StatsAccumulator {
    private var count = 0
    private var powerSum = 0L
    private var powerMax = 0
    private var cadenceSum = 0L
    private var cadenceMax = 0
    private var hrSum = 0L
    private var hrCount = 0
    private var hrMax = 0
    private var distanceKm = 0.0
    private var kcal = 0.0
    private val zones = IntArray(5)

    fun add(s: Sample, zone: Int) {
        count++
        powerSum += s.powerW
        if (s.powerW > powerMax) powerMax = s.powerW
        cadenceSum += s.cadenceRpm
        if (s.cadenceRpm > cadenceMax) cadenceMax = s.cadenceRpm
        if (s.heartRate > 0) {
            hrSum += s.heartRate
            hrCount++
            if (s.heartRate > hrMax) hrMax = s.heartRate
        }
        // 1 s at speed / energy rate.
        distanceKm += s.speedKmh / 3600.0
        kcal += s.kcalPerHour / 3600.0
        zones[zone.coerceIn(0, 4)]++
    }

    fun summary() = StatsSummary(
        durationSec = count,
        avgPowerW = if (count == 0) 0.0 else powerSum.toDouble() / count,
        maxPowerW = powerMax,
        avgCadenceRpm = if (count == 0) 0.0 else cadenceSum.toDouble() / count,
        maxCadenceRpm = cadenceMax,
        avgHeartRate = if (hrCount == 0) null else hrSum.toDouble() / hrCount,
        maxHeartRate = if (hrCount == 0) null else hrMax,
        distanceKm = distanceKm,
        kcal = kcal,
        zoneSeconds = zones.toList(),
    )
}
