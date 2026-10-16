package app.sawatt.core.ble

/** A power/cadence reading accepted from the bike. */
data class BikeSample(val powerW: Int, val cadenceRpm: Int)

/**
 * Turns raw Cycling Power notifications into power + cadence, with these rules:
 *
 *  - notifications closer than 500 ms to the previously accepted one are dropped;
 *  - cadence = 60 * Δrevolutions * 1024 / Δeventtime (event time wraps at 65536);
 *  - when the crank counter did not advance, the previous cadence is kept for one more
 *    packet, and reported as 0 on the second;
 *  - a cadence above 300 rpm is treated as a glitch and the previous value is kept;
 *  - the very first packet only primes the counters and produces no sample.
 *
 * Wrap-around of the revolution counter and of the event time (65536) is handled.
 */
class CadenceCalculator(private val minIntervalMs: Long = 500) {
    private var lastAcceptedAt = Long.MIN_VALUE
    private var primed = false
    private var lastRevs = 0
    private var lastEventTime = 0
    private var cadence = 0
    private var staleCount = 0

    fun reset() {
        lastAcceptedAt = Long.MIN_VALUE
        primed = false
        lastRevs = 0
        lastEventTime = 0
        cadence = 0
        staleCount = 0
    }

    fun onMeasurement(nowMs: Long, m: PowerMeasurement): BikeSample? {
        if (lastAcceptedAt != Long.MIN_VALUE && nowMs - lastAcceptedAt < minIntervalMs) return null
        lastAcceptedAt = nowMs

        val revs = m.crankRevolutions
        val eventTime = m.lastCrankEventTime
        if (revs == null || eventTime == null) {
            // No crank data at all: report power only.
            return BikeSample(m.powerW, cadence)
        }

        if (!primed) {
            primed = true
            lastRevs = revs
            lastEventTime = eventTime
            return null
        }

        val dRevs = (revs - lastRevs) and 0xFFFF
        val dTime = (eventTime - lastEventTime) and 0xFFFF
        if (dRevs > 0 && dTime > 0) {
            val computed = Math.round(60.0 * dRevs * (1024.0 / dTime)).toInt()
            if (computed <= MAX_PLAUSIBLE_CADENCE) cadence = computed
            staleCount = 0
        } else if (staleCount < 1) {
            staleCount++
        } else {
            cadence = 0
            staleCount = 0
        }
        lastRevs = revs
        lastEventTime = eventTime
        return BikeSample(m.powerW, cadence)
    }

    companion object {
        const val MAX_PLAUSIBLE_CADENCE = 300
    }
}
