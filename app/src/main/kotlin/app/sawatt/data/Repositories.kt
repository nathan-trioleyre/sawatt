package app.sawatt.data

import app.sawatt.core.model.AppSettings
import app.sawatt.core.model.UserProfile
import app.sawatt.core.workout.WorkoutPlan
import app.sawatt.core.workout.WorkoutRecord
import app.sawatt.core.workout.WorkoutSamples
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer

class SettingsRepository(private val store: JsonStore, private val scope: CoroutineScope) {
    private val _profile = MutableStateFlow(UserProfile())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    private val _app = MutableStateFlow(AppSettings())
    val app: StateFlow<AppSettings> = _app.asStateFlow()

    /** Whether a profile was ever saved, i.e. the first launch is over; null until read from disk. */
    private val _profileSaved = MutableStateFlow<Boolean?>(null)
    val profileSaved: StateFlow<Boolean?> = _profileSaved.asStateFlow()

    private val writeLock = Mutex()

    init {
        scope.launch {
            val saved = store.read(PROFILE, UserProfile.serializer())
            saved?.let { _profile.value = it }
            _profileSaved.value = saved != null
            store.read(APP, AppSettings.serializer())?.let { _app.value = it }
        }
    }

    fun updateProfile(transform: (UserProfile) -> UserProfile) {
        _profile.update(transform)
        _profileSaved.value = true
        val value = _profile.value
        scope.launch { writeLock.withLock { store.write(PROFILE, UserProfile.serializer(), value) } }
    }

    fun updateApp(transform: (AppSettings) -> AppSettings) {
        _app.update(transform)
        val value = _app.value
        scope.launch { writeLock.withLock { store.write(APP, AppSettings.serializer(), value) } }
    }

    private companion object {
        const val PROFILE = "profile.json"
        const val APP = "settings.json"
    }
}

class PlanRepository(private val store: JsonStore, private val scope: CoroutineScope) {
    private val _plans = MutableStateFlow<List<WorkoutPlan>>(emptyList())
    val plans: StateFlow<List<WorkoutPlan>> = _plans.asStateFlow()
    private val writeLock = Mutex()
    private val serializer = ListSerializer(WorkoutPlan.serializer())

    init {
        scope.launch {
            val stored = store.read(FILE, serializer)
            if (stored == null) {
                // First run: seed the example from the requirements.
                _plans.value = listOf(WorkoutPlan.example())
                persist()
            } else {
                _plans.value = stored
            }
        }
    }

    fun get(id: String): WorkoutPlan? = _plans.value.firstOrNull { it.id == id }

    fun upsert(plan: WorkoutPlan) {
        _plans.update { list ->
            if (list.any { it.id == plan.id }) list.map { if (it.id == plan.id) plan else it } else list + plan
        }
        scope.launch { persist() }
    }

    fun delete(id: String) {
        _plans.update { list -> list.filterNot { it.id == id } }
        scope.launch { persist() }
    }

    private suspend fun persist() = writeLock.withLock { store.write(FILE, serializer, _plans.value) }

    private companion object {
        const val FILE = "plans.json"
    }
}

/**
 * Workout history. Summaries (with per-interval stats) live in one index file; the
 * second-by-second traces are stored per workout and only loaded for the detail screen.
 */
class HistoryRepository(private val store: JsonStore, private val scope: CoroutineScope) {
    private val _records = MutableStateFlow<List<WorkoutRecord>>(emptyList())

    /** Newest first. */
    val records: StateFlow<List<WorkoutRecord>> = _records.asStateFlow()
    private val writeLock = Mutex()
    private val serializer = ListSerializer(WorkoutRecord.serializer())

    init {
        scope.launch {
            store.read(INDEX, serializer)?.let { list -> _records.value = list.sortedByDescending { it.startEpochMs } }
        }
    }

    fun get(id: String): WorkoutRecord? = _records.value.firstOrNull { it.id == id }

    fun save(record: WorkoutRecord, samples: WorkoutSamples) {
        _records.update { list -> (list.filterNot { it.id == record.id } + record).sortedByDescending { it.startEpochMs } }
        scope.launch {
            writeLock.withLock {
                store.write(samplesPath(record.id), WorkoutSamples.serializer(), samples)
                store.write(INDEX, serializer, _records.value)
            }
        }
    }

    fun delete(id: String) {
        _records.update { list -> list.filterNot { it.id == id } }
        scope.launch {
            writeLock.withLock {
                store.delete(samplesPath(id))
                store.write(INDEX, serializer, _records.value)
            }
        }
    }

    suspend fun samples(id: String): WorkoutSamples? = store.read(samplesPath(id), WorkoutSamples.serializer())

    private fun samplesPath(id: String) = "history/$id.samples.json"

    private companion object {
        const val INDEX = "history/index.json"
    }
}
