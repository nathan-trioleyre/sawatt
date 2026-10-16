package app.sawatt.ble

import app.sawatt.core.ble.BikeSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Fake bike + heart-rate strap (mode démo).
 * Rides close to the current target power when a structured workout is running.
 */
class BikeSimulator(private val scope: CoroutineScope, private val targetPower: () -> Int?) {
    private val _bikeSamples = MutableSharedFlow<BikeSample>(extraBufferCapacity = 16)
    val bikeSamples: SharedFlow<BikeSample> = _bikeSamples.asSharedFlow()

    private val _heartRates = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    val heartRates: SharedFlow<Int> = _heartRates.asSharedFlow()

    private var job: Job? = null

    val running: Boolean get() = job?.isActive == true

    fun start() {
        if (running) return
        job = scope.launch {
            var power = 150.0
            var cadence = 85.0
            var hr = 110.0
            while (isActive) {
                val target = (targetPower() ?: 180).toDouble()
                power += (target - power) * 0.15 + Random.nextDouble(-12.0, 12.0)
                cadence += (85 + (target - 180) / 20 - cadence) * 0.2 + Random.nextDouble(-3.0, 3.0)
                hr += ((100 + target / 3) - hr) * 0.03 + Random.nextDouble(-1.0, 1.0)
                _bikeSamples.tryEmit(BikeSample(power.coerceAtLeast(0.0).roundToInt(), cadence.coerceIn(0.0, 140.0).roundToInt()))
                _heartRates.tryEmit(hr.roundToInt())
                delay(500)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
