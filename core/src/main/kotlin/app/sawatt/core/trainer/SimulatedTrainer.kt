package app.sawatt.core.trainer

import app.sawatt.core.ble.BikeSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Home trainer et ceinture cardio simulés, sans aucun matériel : sert aux tests unitaires et
 * au mode démo de l'application.
 *
 * Le cycliste virtuel tend vers [targetPowerW] (180 W par défaut) avec un peu de bruit ; sa
 * cadence et sa fréquence cardiaque suivent la puissance. Les durées passent par `delay`, donc
 * les tests peuvent tout piloter en temps virtuel.
 */
class SimulatedTrainer(
    private val scope: CoroutineScope,
    private val random: Random = Random.Default,
    private val sampleIntervalMs: Long = 500,
    private val scanDelayMs: Long = 1_000,
) : TrainerConnection {

    /** Puissance visée par le cycliste virtuel ; null = 180 W. */
    @Volatile
    var targetPowerW: Int? = null

    private val _scanning = MutableStateFlow(false)
    override val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _discovered = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    override val discovered: StateFlow<List<DiscoveredDevice>> = _discovered.asStateFlow()

    private val _bike = MutableStateFlow<DeviceLink?>(null)
    override val bike: StateFlow<DeviceLink?> = _bike.asStateFlow()

    private val _heartRateSensor = MutableStateFlow<DeviceLink?>(null)
    override val heartRateSensor: StateFlow<DeviceLink?> = _heartRateSensor.asStateFlow()

    private val _bikeSamples = MutableSharedFlow<BikeSample>(extraBufferCapacity = 64)
    override val bikeSamples: SharedFlow<BikeSample> = _bikeSamples.asSharedFlow()

    private val _heartRates = MutableSharedFlow<Int>(extraBufferCapacity = 64)
    override val heartRates: SharedFlow<Int> = _heartRates.asSharedFlow()

    private var scanJob: Job? = null
    private var bikeJob: Job? = null
    private var heartJob: Job? = null

    // État du cycliste virtuel, partagé entre le vélo et la ceinture.
    private var power = 150.0
    private var cadence = 85.0
    private var heartRate = 110.0

    override fun isEnabled() = true

    override fun startScan(durationMs: Long) {
        if (_scanning.value) return
        _scanning.value = true
        scanJob = scope.launch {
            delay(minOf(scanDelayMs, durationMs))
            _discovered.value = listOf(BIKE, HEART_RATE)
            _scanning.value = false
        }
    }

    override fun stopScan() {
        scanJob?.cancel()
        _scanning.value = false
    }

    override fun connect(device: DiscoveredDevice, keepScanning: Boolean) {
        if (!keepScanning) stopScan()
        disconnect(device.type)
        val link = DeviceLink(device, LinkState.CONNECTED, firmware = "simulated")
        when (device.type) {
            DeviceType.BIKE -> {
                _bike.value = link
                bikeJob = scope.launch {
                    while (isActive) {
                        delay(sampleIntervalMs)
                        step()
                        _bikeSamples.tryEmit(BikeSample(power.roundToInt(), cadence.roundToInt()))
                    }
                }
            }
            DeviceType.HEART_RATE -> {
                _heartRateSensor.value = link
                heartJob = scope.launch {
                    while (isActive) {
                        delay(sampleIntervalMs)
                        _heartRates.tryEmit(heartRate.roundToInt())
                    }
                }
            }
        }
    }

    override fun disconnect(type: DeviceType) {
        when (type) {
            DeviceType.BIKE -> {
                bikeJob?.cancel()
                _bike.value = null
            }
            DeviceType.HEART_RATE -> {
                heartJob?.cancel()
                _heartRateSensor.value = null
            }
        }
    }

    override fun disconnectAll() = DeviceType.entries.forEach(::disconnect)

    /** Fait évoluer le cycliste virtuel d'un pas vers la cible. */
    private fun step() {
        val target = (targetPowerW ?: DEFAULT_POWER_W).toDouble()
        power = (power + (target - power) * 0.15 + random.nextDouble(-12.0, 12.0)).coerceAtLeast(0.0)
        cadence = (cadence + (85 + (target - 180) / 20 - cadence) * 0.2 + random.nextDouble(-3.0, 3.0)).coerceIn(0.0, 140.0)
        heartRate += ((100 + target / 3) - heartRate) * 0.03 + random.nextDouble(-1.0, 1.0)
    }

    companion object {
        const val DEFAULT_POWER_W = 180
        val BIKE = DiscoveredDevice("SIM:BIKE", "Simulated trainer", -40, DeviceType.BIKE)
        val HEART_RATE = DiscoveredDevice("SIM:HR", "Simulated heart rate", -50, DeviceType.HEART_RATE)
    }
}
