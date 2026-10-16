package app.sawatt.ble

import app.sawatt.core.ble.BikeSample
import app.sawatt.core.trainer.DeviceLink
import app.sawatt.core.trainer.DeviceType
import app.sawatt.core.trainer.DiscoveredDevice
import app.sawatt.core.trainer.TrainerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Delegates to the real or the simulated connection depending on [useSimulated] (the "Simulated
 * bike" setting). The rest of the app only sees a single [TrainerConnection].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwitchingTrainerConnection(
    private val scope: CoroutineScope,
    private val real: TrainerConnection,
    private val simulated: TrainerConnection,
    private val useSimulated: StateFlow<Boolean>,
) : TrainerConnection {

    private val current: TrainerConnection get() = if (useSimulated.value) simulated else real

    private fun <T> switch(initial: T, select: (TrainerConnection) -> StateFlow<T>): StateFlow<T> =
        useSimulated.flatMapLatest { select(if (it) simulated else real) }
            .stateIn(scope, SharingStarted.Eagerly, initial)

    override val scanning: StateFlow<Boolean> = switch(false) { it.scanning }
    override val discovered: StateFlow<List<DiscoveredDevice>> = switch(emptyList()) { it.discovered }
    override val bike: StateFlow<DeviceLink?> = switch(null) { it.bike }
    override val heartRateSensor: StateFlow<DeviceLink?> = switch(null) { it.heartRateSensor }
    override val bikeSamples: Flow<BikeSample> = useSimulated.flatMapLatest { if (it) simulated.bikeSamples else real.bikeSamples }
    override val heartRates: Flow<Int> = useSimulated.flatMapLatest { if (it) simulated.heartRates else real.heartRates }

    init {
        // When the mode changes, release the devices held by the other implementation.
        scope.launch {
            useSimulated.drop(1).collect { simulatedNow ->
                val previous = if (simulatedNow) real else simulated
                previous.stopScan()
                previous.disconnectAll()
            }
        }
    }

    override fun isEnabled() = current.isEnabled()
    override fun startScan(durationMs: Long) = current.startScan(durationMs)
    override fun stopScan() = current.stopScan()
    override fun connect(device: DiscoveredDevice, keepScanning: Boolean) = current.connect(device, keepScanning)
    override fun disconnect(type: DeviceType) = current.disconnect(type)
    override fun disconnectAll() = current.disconnectAll()
}
