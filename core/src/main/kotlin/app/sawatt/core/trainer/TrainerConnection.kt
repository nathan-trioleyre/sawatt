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
 * Point d'accès unique au vélo (ou home trainer) et à la ceinture cardio.
 *
 * Toute la communication avec les capteurs passe par cette interface : l'implémentation
 * Bluetooth vit dans le module Android, et une implémentation simulée la remplace pour les tests et le
 * mode démo. La logique des séances ne consomme que [bikeSamples] et [heartRates].
 */
interface TrainerConnection {
    /** Vrai pendant une recherche d'appareils. */
    val scanning: StateFlow<Boolean>

    /** Appareils trouvés par la dernière recherche, du plus proche au plus lointain. */
    val discovered: StateFlow<List<DiscoveredDevice>>

    /** Vélo sélectionné, ou null. */
    val bike: StateFlow<DeviceLink?>

    /** Ceinture cardio sélectionnée, ou null. */
    val heartRateSensor: StateFlow<DeviceLink?>

    /** Mesures puissance + cadence du vélo. */
    val bikeSamples: Flow<BikeSample>

    /** Fréquence cardiaque en battements par minute. */
    val heartRates: Flow<Int>

    /** Faux si le transport sous-jacent (ex. Bluetooth) est désactivé. */
    fun isEnabled(): Boolean

    fun startScan(durationMs: Long = 30_000)

    fun stopScan()

    /** [keepScanning] permet de reconnecter un vélo mémorisé sans arrêter la recherche de la ceinture. */
    fun connect(device: DiscoveredDevice, keepScanning: Boolean = false)

    fun disconnect(type: DeviceType)

    fun disconnectAll()
}
