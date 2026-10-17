package app.sawatt.core

import app.sawatt.core.workout.Block
import app.sawatt.core.workout.FtpTest
import app.sawatt.core.workout.WorkoutPlan
import app.sawatt.core.workout.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FtpTestTest {
    private fun WorkoutSession.ride(seconds: Int, power: Int) = repeat(seconds) {
        onBikeSample(power, 90)
        tick()
    }

    @Test
    fun `the protocol is a 66-minute plan with a 20-minute all-out block`() {
        val plan = FtpTest.plan()
        val flat = plan.flatten()
        assertEquals(FtpTest.PLAN_ID, plan.id)
        assertEquals(66 * 60, plan.totalDurationSec)
        assertEquals(12, flat.size)
        val allOut = flat.filter { it.block.allOut }
        assertEquals(1, allOut.size)
        assertEquals(FtpTest.ALL_OUT_SEC, allOut.single().block.durationSec)
        assertEquals("All out", allOut.single().label)
        assertEquals("Cool-down", flat.last().label)
        assertEquals(110, flat[1].block.targetCadenceRpm)
    }

    @Test
    fun `blocks are named by the caller`() {
        val plan = FtpTest.plan("Test FTP") { it.name.lowercase() }
        assertEquals("Test FTP", plan.name)
        assertEquals("warm_up", plan.flatten().first().label)
    }

    @Test
    fun `an all-out block has no power target`() {
        val block = Block("All out", 60, allOut = true)
        assertNull(block.targetWatts(250.0))
        assertEquals("All out", block.targetLabel(250.0))
        assertEquals(Block.ALL_OUT_INTENSITY_PERCENT, block.intensityPercent(250.0), 1e-9)
        assertThrows(IllegalArgumentException::class.java) { Block("All out", 60, 200, allOut = true) }
        assertThrows(IllegalArgumentException::class.java) { Block("All out", 60, targetFtpPercent = 100, allOut = true) }
    }

    @Test
    fun `ftp is 95 percent of the all-out average`() {
        assertEquals(238, FtpTest.estimateFtp(250.0))
        assertEquals(190, FtpTest.estimateFtp(200.0))
    }

    private fun shortTest() = WorkoutPlan(
        id = FtpTest.PLAN_ID,
        name = "FTP test",
        steps = listOf(Block("Easy", 2, 100), Block("All out", 4, allOut = true), Block("Cool-down", 2, 100)),
    )

    @Test
    fun `a completed test gives an ftp`() {
        val s = WorkoutSession(shortTest(), weightKg = 75.0, ftpW = 200.0, startEpochMs = 0)
        s.ride(2, power = 100)
        assertTrue(s.snapshot().current.allOut)
        assertNull(s.snapshot().current.targetPowerW)
        s.ride(4, power = 300)
        s.ride(1, power = 100)
        val result = FtpTest.result(s.finish().first)!!
        assertEquals(300.0, result.avgPowerW, 1e-9)
        assertEquals(285, result.ftpW)
        assertTrue(result.complete)
    }

    @Test
    fun `a test stopped during the all-out block is incomplete`() {
        val s = WorkoutSession(shortTest(), weightKg = 75.0, ftpW = 200.0, startEpochMs = 0)
        s.ride(2, power = 100)
        s.ride(2, power = 300)
        val result = FtpTest.result(s.finish().first)!!
        assertFalse(result.complete)
    }

    @Test
    fun `no result before the all-out block or for other workouts`() {
        val s = WorkoutSession(shortTest(), weightKg = 75.0, ftpW = 200.0, startEpochMs = 0)
        s.ride(1, power = 100)
        assertNull(FtpTest.result(s.finish().first))

        val other = WorkoutSession(WorkoutPlan(name = "x", steps = listOf(Block("All out", 2, allOut = true))), 75.0, 200.0, 0)
        other.ride(2, power = 300)
        assertNull(FtpTest.result(other.finish().first))
    }
}
