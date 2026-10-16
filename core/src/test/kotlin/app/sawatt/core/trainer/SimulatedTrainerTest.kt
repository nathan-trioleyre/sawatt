package app.sawatt.core.trainer

import app.sawatt.core.ble.BikeSample
import app.sawatt.core.workout.Block
import app.sawatt.core.workout.WorkoutPlan
import app.sawatt.core.workout.WorkoutSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Everything runs in virtual time: no hardware, no real waiting. */
@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedTrainerTest {

    private fun TestScope.trainer() = SimulatedTrainer(backgroundScope, Random(42))

    private fun <T> TestScope.collect(flow: Flow<T>): MutableList<T> {
        val out = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { out += it } }
        return out
    }

    @Test
    fun `scan finds a bike and a heart-rate strap`() = runTest {
        val t = trainer()
        t.startScan()
        assertTrue(t.scanning.value)
        advanceTimeBy(1_001)
        assertFalse(t.scanning.value)
        assertEquals(listOf(DeviceType.BIKE, DeviceType.HEART_RATE), t.discovered.value.map { it.type })
    }

    @Test
    fun `connected bike rides towards the target power`() = runTest {
        val t = trainer()
        val samples = collect(t.bikeSamples)
        t.targetPowerW = 250
        t.connect(SimulatedTrainer.BIKE)
        assertEquals(LinkState.CONNECTED, t.bike.value?.state)

        advanceTimeBy(60_001)
        assertEquals(120, samples.size) // one sample every 500 ms
        val lastTenSeconds = samples.takeLast(20).map(BikeSample::powerW).average()
        assertEquals(250.0, lastTenSeconds, 15.0)
    }

    @Test
    fun `disconnect stops the data`() = runTest {
        val t = trainer()
        val samples = collect(t.bikeSamples)
        val heart = collect(t.heartRates)
        t.connect(SimulatedTrainer.BIKE)
        t.connect(SimulatedTrainer.HEART_RATE)
        advanceTimeBy(5_000)
        t.disconnectAll()
        runCurrent()
        val count = samples.size
        assertTrue(heart.isNotEmpty())
        advanceTimeBy(5_000)
        assertEquals(count, samples.size)
        assertNull(t.bike.value)
        assertNull(t.heartRateSensor.value)
    }

    @Test
    fun `simulated trainer drives an interval workout without hardware`() = runTest {
        val t = trainer()
        val plan = WorkoutPlan(name = "test", steps = listOf(Block("Easy", 60, 150), Block("Hard", 60, 300)))
        val session = WorkoutSession(plan, weightKg = 75.0, ftpW = 250.0, startEpochMs = 0)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            t.bikeSamples.collect { session.onBikeSample(it.powerW, it.cadenceRpm) }
        }
        t.connect(SimulatedTrainer.BIKE)

        repeat(120) {
            t.targetPowerW = session.snapshot().current.targetPowerW
            advanceTimeBy(1_000)
            session.tick()
        }
        val intervals = session.finish().first.intervals
        assertEquals(listOf("Easy", "Hard"), intervals.map { it.name })
        // The virtual rider needs a few seconds to settle, so averages are close but not exact.
        assertEquals(150.0, intervals[0].stats.avgPowerW, 20.0)
        assertEquals(300.0, intervals[1].stats.avgPowerW, 30.0)
    }
}
