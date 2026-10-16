package app.sawatt

import android.app.Application
import android.content.Context
import app.sawatt.ble.BleManager
import app.sawatt.ble.DeviceType
import app.sawatt.data.HistoryRepository
import app.sawatt.data.JsonStore
import app.sawatt.data.PlanRepository
import app.sawatt.data.SettingsRepository
import app.sawatt.workout.WorkoutController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SawattApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Process-wide singletons. The workout outlives screens and navigation. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = JsonStore(context.filesDir)

    val settings = SettingsRepository(store, scope)
    val plans = PlanRepository(store, scope)
    val history = HistoryRepository(store, scope)
    val ble = BleManager(context, scope)
    val workout = WorkoutController(context, scope, ble, settings, history)

    init {
        // Auto-select the last used bike / strap when a scan finds them.
        scope.launch {
            ble.discovered.collect { devices ->
                val app = settings.app.value
                devices.forEach { d ->
                    val (current, preferred) = when (d.type) {
                        DeviceType.BIKE -> ble.bike.value to app.defaultBike
                        DeviceType.HEART_RATE -> ble.heartRateSensor.value to app.defaultHrSensor
                    }
                    if (current == null && d.address == preferred) ble.connect(d, keepScanning = true)
                }
            }
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as SawattApp).container
