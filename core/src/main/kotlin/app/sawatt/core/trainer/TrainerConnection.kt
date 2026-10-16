package app.sawatt.core.trainer

import app.sawatt.core.ble.BikeSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class DeviceType { BIKE, HEART_RATE }

data class DiscoveredDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val type: DeviceType,
)

enum class LinkState { CONNECTING, CONNECTED, RECONNECTING }

data class DeviceLink(
    val device: DiscoveredDevice,
    val state: LinkState,
    val firmware: String? = null,
)

/**
 * Single access point to the bike (or trainer) and the heart-rate strap.
 *
 * All sensor communication goes through this interface: the Bluetooth implementation lives in the
 * Android module, and a simulated implementation replaces it for tests and the demo mode. Workout
 * logic only consumes [bikeSamples] and [heartRates].
 */
interface TrainerConnection {
    /** True while scanning for devices. */
    val scanning: StateFlow<Boolean>

    /** Devices found by the last scan, nearest first. */
    val discovered: StateFlow<List<DiscoveredDevice>>

    /** Selected bike, or null. */
    val bike: StateFlow<DeviceLink?>

    /** Selected heart-rate strap, or null. */
    val heartRateSensor: StateFlow<DeviceLink?>

    /** Power + cadence readings from the bike. */
    val bikeSamples: Flow<BikeSample>

    /** Heart rate in beats per minute. */
    val heartRates: Flow<Int>

    /** False when the underlying transport (e.g. Bluetooth) is turned off. */
    fun isEnabled(): Boolean

    fun startScan(durationMs: Long = 30_000)

    fun stopScan()

    /** [keepScanning] lets a remembered bike reconnect without stopping the search for the strap. */
    fun connect(device: DiscoveredDevice, keepScanning: Boolean = false)

    fun disconnect(type: DeviceType)

    fun disconnectAll()
}
