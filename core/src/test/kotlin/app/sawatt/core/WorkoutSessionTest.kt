package app.sawatt.core

import app.sawatt.core.history.HistoryStats
import app.sawatt.core.history.PeriodKind
import app.sawatt.core.history.StorageJson
import app.sawatt.core.workout.Block
import app.sawatt.core.workout.IntervalKind
import app.sawatt.core.workout.RepeatSet
import app.sawatt.core.workout.SessionEvent
import app.sawatt.core.workout.StatsSummary
import app.sawatt.core.workout.WorkoutPlan
import app.sawatt.core.workout.WorkoutRecord
import app.sawatt.core.workout.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class WorkoutSessionTest {
    private val shortPlan = WorkoutPlan(
        name = "test",
        steps = listOf(
            Block("Warm-up", 3, 100),
            RepeatSet(2, listOf(Block("Effort", 2, 250, 90), Block("Rest", 1, 100))),
        ),
    )

    private fun WorkoutSession.ride(seconds: Int, power: Int, cadence: Int = 80, hr: Int = 0): List<SessionEvent> {
        val events = mutableListOf<SessionEvent>()
        repeat(seconds) {
            onBikeSample(power, cadence)
            if (hr > 0) onHeartRate(hr)
            events += tick()
        }
        return events
    }

    @Test
    fun `example plan flattens to 8 intervals and 80 minutes`() {
        val plan = WorkoutPlan.example()
        val flat = plan.flatten()
        assertEquals(8, flat.size)
        assertEquals(80 * 60, plan.totalDurationSec)
        assertEquals("Effort 2/3", flat[3].label)
    }

    @Test
    fun `plan advances on time and reports per-interval averages`() {
        val s = WorkoutSession(shortPlan, weightKg = 75.0, ftpW = 200.0, startEpochMs = 0)
        assertEquals("Warm-up", s.snapshot().current.name)
        assertEquals(3, s.snapshot().current.remainingSec)

        s.ride(2, power = 90)
        val warmupEnd = s.ride(1, power = 120)
        val changed = warmupEnd.single() as SessionEvent.IntervalChanged
        assertEquals("Warm-up", changed.finished!!.name)
        assertEquals(100.0, changed.finished!!.stats.avgPowerW, 1e-9) // (90+90+120)/3
        assertEquals(120, changed.finished!!.stats.maxPowerW)
        assertEquals("Effort 1/2", changed.started.name)
        assertEquals(250, changed.started.targetPowerW)
        assertEquals(90, changed.started.targetCadenceRpm)

        // Mid-interval live average only covers the current interval.
        s.ride(1, power = 300, hr = 150)
        val live = s.snapshot()
        assertEquals(300.0, live.current.stats.avgPowerW, 1e-9)
        assertEquals(150.0, live.current.stats.avgHeartRate!!, 1e-9)
        assertEquals(1, live.current.remainingSec)
        assertEquals("Rest 1/2", live.next!!.label)
        assertEquals(4, live.elapsedSec)

        s.ride(1, power = 200)
        s.ride(1, power = 100) // Rest 1/2
        s.ride(2, power = 260) // Effort 2/2
        val last = s.ride(1, power = 100) // Rest 2/2 -> plan done
        assertTrue(last.any { it is SessionEvent.PlanCompleted })
        assertTrue(s.snapshot().planFinished)
        assertEquals(IntervalKind.LAP, s.snapshot().current.kind)

        val (record, samples) = s.finish()
        assertEquals(listOf("Warm-up", "Effort 1/2", "Rest 1/2", "Effort 2/2", "Rest 2/2"), record.intervals.map { it.name })
        assertEquals(250.0, record.intervals[1].stats.avgPowerW, 1e-9)
        assertEquals(260.0, record.intervals[3].stats.avgPowerW, 1e-9)
        assertEquals(9, record.summary.durationSec)
        assertEquals(9, samples.powerW.size)
        assertFalse(record.intervals.any { it.endedEarly })
    }

    @Test
    fun `ftp percentage targets are resolved with the session ftp`() {
        val plan = WorkoutPlan(
            name = "sweet spot",
            steps = listOf(Block("Sweet spot", 2, targetFtpPercent = 90), Block("Easy", 2, 120)),
        )
        val s = WorkoutSession(plan, weightKg = 75.0, ftpW = 250.0, startEpochMs = 0)
        val live = s.snapshot()
        assertEquals(225, live.current.targetPowerW)
        assertEquals(90, live.current.targetFtpPercent)
        assertEquals("120 W", live.next!!.block.targetLabel(live.ftpW))

        s.ride(4, power = 225)
        val intervals = s.finish().first.intervals
        assertEquals(225, intervals[0].targetPowerW)
        assertEquals(90, intervals[0].targetFtpPercent)
        assertEquals(120, intervals[1].targetPowerW)
        assertNull(intervals[1].targetFtpPercent)
    }

    @Test
    fun `block target labels`() {
        assertEquals("250 W", Block("A", 60, 250).targetLabel(200.0))
        assertEquals("75% FTP", Block("A", 60, targetFtpPercent = 75).targetLabel())
        assertEquals("75% FTP (188 W)", Block("A", 60, targetFtpPercent = 75).targetLabel(250.0))
    }

    @Test
    fun `a block needs exactly one power target`() {
        assertThrows(IllegalArgumentException::class.java) { Block("None", 60) }
        assertThrows(IllegalArgumentException::class.java) { Block("Both", 60, 200, targetFtpPercent = 80) }
    }

    @Test
    fun `plans saved before ftp percentages still load`() {
        val json = """{"id":"a","name":"old","steps":[{"type":"block","name":"X","durationSec":60,"targetPowerW":200,"targetCadenceRpm":null}]}"""
        val block = StorageJson.decodeFromString(WorkoutPlan.serializer(), json).steps.single() as Block
        assertEquals(200, block.targetPowerW)
        assertNull(block.targetFtpPercent)
    }

    @Test
    fun `lap skips a planned block and is marked ended early`() {
        val s = WorkoutSession(shortPlan, 75.0, 200.0, 0)
        assertTrue("lap on empty interval is ignored", s.lap().isEmpty())
        s.ride(1, power = 150)
        val ev = s.lap().first() as SessionEvent.IntervalChanged
        assertTrue(ev.finished!!.endedEarly)
        assertEquals(1, ev.finished!!.stats.durationSec)
        assertEquals("Effort 1/2", ev.started.name)
    }

    @Test
    fun `free ride laps split intervals`() {
        val s = WorkoutSession(null, 75.0, 200.0, 0)
        assertEquals("Lap 1", s.snapshot().current.name)
        assertNull(s.snapshot().current.remainingSec)
        s.ride(3, power = 200)
        s.lap()
        s.ride(2, power = 300)
        val (record, _) = s.finish()
        assertEquals(listOf("Lap 1", "Lap 2"), record.intervals.map { it.name })
        assertEquals(200.0, record.intervals[0].stats.avgPowerW, 1e-9)
        assertEquals(300.0, record.intervals[1].stats.avgPowerW, 1e-9)
        assertEquals(240.0, record.summary.avgPowerW, 1e-9)
        assertEquals(3, record.intervals[1].startOffsetSec)
    }

    @Test
    fun `readings within a second are averaged and stale data drops to zero`() {
        val s = WorkoutSession(null, 75.0, 200.0, 0)
        s.onBikeSample(100, 80)
        s.onBikeSample(200, 90)
        s.tick()
        assertEquals(150.0, s.snapshot().session.avgPowerW, 1e-9)
        repeat(3) { s.tick() } // carried
        s.tick() // 4th second without data -> 0
        val (_, samples) = s.finish()
        assertEquals(listOf(150, 150, 150, 150, 0), samples.powerW)
        assertEquals(listOf(85, 85, 85, 85, 0), samples.cadenceRpm)
    }

    @Test
    fun `json round trip keeps plan polymorphism and records`() {
        val plan = WorkoutPlan.example()
        val decoded = StorageJson.decodeFromString(WorkoutPlan.serializer(), StorageJson.encodeToString(WorkoutPlan.serializer(), plan))
        assertEquals(plan, decoded)

        val s = WorkoutSession(shortPlan, 75.0, 200.0, 1234)
        s.ride(4, power = 200, hr = 140)
        val record = s.finish().first
        val json = StorageJson.encodeToString(WorkoutRecord.serializer(), record)
        assertEquals(record, StorageJson.decodeFromString(WorkoutRecord.serializer(), json))
    }

    @Test
    fun `weekly stats use the ISO week-based year and weight by duration`() {
        fun rec(date: LocalDate, sec: Int, power: Double) = WorkoutRecord(
            id = date.toString(),
            startEpochMs = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
            summary = StatsSummary(durationSec = sec, avgPowerW = power, maxPowerW = power.toInt()),
            intervals = emptyList(),
        )
        val records = listOf(
            rec(LocalDate.of(2024, 12, 30), 3600, 200.0), // ISO week 1 of 2025
            rec(LocalDate.of(2025, 1, 2), 1800, 260.0),
            rec(LocalDate.of(2024, 12, 20), 600, 150.0),
        )
        val weeks = HistoryStats.byPeriod(records, PeriodKind.WEEK, ZoneOffset.UTC)
        assertEquals(2, weeks.size)
        assertEquals(2025, weeks[0].key.year)
        assertEquals(1, weeks[0].key.number)
        assertEquals(2, weeks[0].workouts)
        assertEquals(220.0, weeks[0].avgPowerW, 1e-9) // (200·3600 + 260·1800) / 5400
        assertEquals(2, HistoryStats.byPeriod(records, PeriodKind.YEAR, ZoneOffset.UTC).size)
    }
}
