package app.sawatt

import android.app.Application
import android.content.Context
import app.sawatt.ble.BleManager
import app.sawatt.ble.SwitchingTrainerConnection
import app.sawatt.core.trainer.DeviceType
import app.sawatt.core.trainer.SimulatedTrainer
import app.sawatt.core.trainer.TrainerConnection
import app.sawatt.data.HistoryRepository
import app.sawatt.data.JsonStore
import app.sawatt.data.PlanRepository
import app.sawatt.data.SettingsRepository
import app.sawatt.workout.WorkoutController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    private val simulated = SimulatedTrainer(scope)

    /** Seul point d'accès aux capteurs pour le reste de l'application. */
    val trainer: TrainerConnection = SwitchingTrainerConnection(
        scope = scope,
        real = BleManager(context, scope),
        simulated = simulated,
        useSimulated = settings.app.map { it.simulateBike }.stateIn(scope, SharingStarted.Eagerly, settings.app.value.simulateBike),
    )
    val workout = WorkoutController(context, scope, trainer, settings, history)

    init {
        // Auto-select the last used bike / strap when a scan finds them.
        scope.launch {
            trainer.discovered.collect { devices ->
                val app = settings.app.value
                devices.forEach { d ->
                    val (current, preferred) = when (d.type) {
                        DeviceType.BIKE -> trainer.bike.value to app.defaultBike
                        DeviceType.HEART_RATE -> trainer.heartRateSensor.value to app.defaultHrSensor
                    }
                    if (current == null && d.address == preferred) trainer.connect(d, keepScanning = true)
                }
            }
        }
        // Mode démo : le vélo et la ceinture simulés se connectent d'eux-mêmes.
        scope.launch {
            settings.app.map { it.simulateBike }.distinctUntilChanged().collect { on ->
                if (on) {
                    simulated.connect(SimulatedTrainer.BIKE)
                    simulated.connect(SimulatedTrainer.HEART_RATE)
                }
            }
        }
        // Le cycliste virtuel suit la cible de l'intervalle en cours.
        scope.launch {
            workout.state.collect { simulated.targetPowerW = it.live?.current?.targetPowerW }
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as SawattApp).container
