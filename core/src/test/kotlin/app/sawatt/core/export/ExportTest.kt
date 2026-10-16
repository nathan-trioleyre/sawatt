package app.sawatt.core.export

import app.sawatt.core.workout.Block
import app.sawatt.core.workout.RepeatSet
import app.sawatt.core.workout.WorkoutPlan
import app.sawatt.core.workout.WorkoutRecord
import app.sawatt.core.workout.WorkoutSamples
import app.sawatt.core.workout.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import javax.xml.parsers.DocumentBuilderFactory

class ExportTest {
    /** 2026-10-16T17:30:00Z, a 3-interval workout with heart rate. */
    private fun workout(): Pair<WorkoutRecord, WorkoutSamples> {
        val plan = WorkoutPlan(
            name = "Test <&> plan",
            steps = listOf(Block("Warm-up", 5, 120), RepeatSet(1, listOf(Block("Effort", 10, 250, 90))), Block("Cool-down", 5, 100)),
        )
        val s = WorkoutSession(plan, weightKg = 75.0, ftpW = 250.0, startEpochMs = 1_792_171_800_000)
        repeat(20) { i ->
            val target = s.snapshot().current.targetPowerW ?: 150
            s.onBikeSample(target + i % 3, 85)
            s.onHeartRate(120 + i)
            s.tick()
        }
        return s.finish()
    }

    @Test
    fun `tcx has one lap per interval and one trackpoint per second`() {
        val (record, samples) = workout()
        val xml = TcxWriter.write(record, samples)
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(xml.byteInputStream())
        val ns = "http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"
        val ext = "http://www.garmin.com/xmlschemas/ActivityExtension/v2"
        assertEquals(3, doc.getElementsByTagNameNS(ns, "Lap").length)
        assertEquals(20, doc.getElementsByTagNameNS(ns, "Trackpoint").length)
        assertEquals("2026-10-16T17:30:00Z", doc.getElementsByTagNameNS(ns, "Id").item(0).textContent)
        assertEquals(Math.round(record.intervals[1].stats.avgPowerW).toString(), doc.getElementsByTagNameNS(ext, "AvgWatts").item(1).textContent)
        assertEquals("120", doc.getElementsByTagNameNS(ns, "HeartRateBpm").item(0).textContent.trim())
    }

    @Test
    fun `fit file has a valid header and crc`() {
        val (record, samples) = workout()
        val fit = FitWriter.write(record, samples, ZoneOffset.UTC)
        assertEquals(14, fit[0].toInt())
        assertEquals(".FIT", String(fit, 8, 4, Charsets.US_ASCII))
        val dataSize = le(fit, 4, 4).toInt()
        assertEquals(14 + dataSize + 2, fit.size)
        assertEquals(0, FitEncoder.crc(fit.copyOfRange(0, 14))) // header CRC included
        assertEquals(0, FitEncoder.crc(fit)) // file CRC included
    }

    @Test
    fun `fit file contains records, laps and session with the workout values`() {
        val (record, samples) = workout()
        val messages = decode(FitWriter.write(record, samples, ZoneOffset.UTC))
        val byGlobal = messages.groupBy { it.global }
        assertEquals(1, byGlobal.getValue(0).size) // file_id
        assertEquals(20, byGlobal.getValue(20).size) // records
        assertEquals(3, byGlobal.getValue(19).size) // laps
        assertEquals(1, byGlobal.getValue(18).size) // session
        assertEquals(1, byGlobal.getValue(34).size) // activity

        val start = record.startEpochMs / 1000 - 631_065_600L
        val firstRecord = byGlobal.getValue(20).first().values
        assertEquals(start, firstRecord.getValue(253))
        assertEquals(120L, firstRecord.getValue(3)) // heart rate
        assertEquals(samples.powerW[0].toLong(), firstRecord.getValue(7)) // power

        val effort = byGlobal.getValue(19)[1].values
        assertEquals(start + 5, effort.getValue(2)) // lap start_time
        assertEquals(10_000L, effort.getValue(7)) // total_elapsed_time in ms
        assertEquals(Math.round(record.intervals[1].stats.avgPowerW), effort.getValue(19))

        val session = byGlobal.getValue(18).single().values
        assertEquals(20_000L, session.getValue(8)) // total_timer_time
        assertEquals(3L, session.getValue(26)) // num_laps
        assertTrue(session.getValue(9) > 0) // total_distance
    }

    private data class Message(val global: Int, val values: Map<Int, Long>)

    /** Tiny FIT decoder for normal headers and little-endian fields, enough to check our output. */
    private fun decode(fit: ByteArray): List<Message> {
        val end = 14 + le(fit, 4, 4).toInt()
        val defs = mutableMapOf<Int, Pair<Int, List<Pair<Int, Int>>>>()
        val out = mutableListOf<Message>()
        var p = 14
        while (p < end) {
            val header = fit[p++].toInt() and 0xFF
            val local = header and 0x0F
            if (header and 0x40 != 0) {
                p += 2 // reserved + architecture
                val global = le(fit, p, 2).toInt()
                p += 2
                val n = fit[p++].toInt() and 0xFF
                val fields = (0 until n).map {
                    val f = (fit[p].toInt() and 0xFF) to (fit[p + 1].toInt() and 0xFF)
                    p += 3
                    f
                }
                defs[local] = global to fields
            } else {
                val (global, fields) = defs.getValue(local)
                val values = mutableMapOf<Int, Long>()
                for ((num, size) in fields) {
                    values[num] = le(fit, p, size)
                    p += size
                }
                out += Message(global, values)
            }
        }
        return out
    }

    private fun le(b: ByteArray, at: Int, size: Int): Long =
        (0 until size).fold(0L) { acc, i -> acc or ((b[at + i].toLong() and 0xFF) shl (8 * i)) }
}
