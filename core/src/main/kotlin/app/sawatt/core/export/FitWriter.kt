package app.sawatt.core.export

import app.sawatt.core.workout.WorkoutRecord
import app.sawatt.core.workout.WorkoutSamples
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Garmin FIT activity export: the native format of Garmin devices, preferred by Strava, Garmin
 * Connect and intervals.icu. Writes the messages a cycling activity needs: file_id, timer events,
 * one record per second, one lap per interval, the session and the activity.
 *
 * Only riding time is recorded, so pauses do not appear in the timeline.
 */
object FitWriter {
    fun write(record: WorkoutRecord, samples: WorkoutSamples, zone: ZoneId = ZoneId.systemDefault()): ByteArray {
        require(samples.powerW.isNotEmpty()) { "Cannot export a workout without samples" }
        val data = FitEncoder()
        val start = fitTime(record.startEpochMs)
        val count = samples.powerW.size
        val end = start + count
        val distance = samples.cumulativeDistanceM()
        val laps = lapsOf(record, samples)

        data.define(FILE_ID, 0, listOf(Field(0, ENUM), Field(1, UINT16), Field(2, UINT16), Field(4, UINT32)))
        data.message(FILE_ID, FILE_TYPE_ACTIVITY, MANUFACTURER_DEVELOPMENT, 0, start)

        data.define(EVENT, 21, listOf(Field(253, UINT32), Field(0, ENUM), Field(1, ENUM)))
        data.message(EVENT, start, EVENT_TIMER, EVENT_TYPE_START)

        data.define(RECORD, 20, listOf(Field(253, UINT32), Field(3, UINT8), Field(4, UINT8), Field(5, UINT32), Field(6, UINT16), Field(7, UINT16)))
        for (i in 0 until count) {
            val hr = samples.heartRate.getOrElse(i) { 0 }
            data.message(
                RECORD,
                start + i,
                if (hr > 0) hr.coerceAtMost(254) else null,
                samples.cadenceRpm.getOrElse(i) { 0 }.coerceIn(0, 254),
                (distance[i] * 100).roundToLong(),
                (samples.speedMs(i) * 1000).roundToLong().coerceIn(0, 65_534),
                samples.powerW[i].coerceIn(0, 65_534),
            )
        }

        data.message(EVENT, end, EVENT_TIMER, EVENT_TYPE_STOP_ALL)

        data.define(
            LAP, 19,
            listOf(
                Field(254, UINT16), Field(253, UINT32), Field(0, ENUM), Field(1, ENUM), Field(2, UINT32),
                Field(7, UINT32), Field(8, UINT32), Field(9, UINT32), Field(11, UINT16),
                Field(15, UINT8), Field(16, UINT8), Field(17, UINT8), Field(18, UINT8),
                Field(19, UINT16), Field(20, UINT16), Field(25, ENUM), Field(39, ENUM),
            ),
        )
        laps.forEachIndexed { index, lap ->
            val s = lap.stats
            val lapEnd = minOf(lap.startSec + lap.durationSec, count)
            val lapDistance = distance[lapEnd - 1] - if (lap.startSec == 0) 0.0 else distance[lap.startSec - 1]
            data.message(
                LAP,
                index, start + lapEnd, EVENT_LAP, EVENT_TYPE_STOP, start + lap.startSec,
                lap.durationSec * 1000L, lap.durationSec * 1000L, (lapDistance * 100).roundToLong(), s.kcal.roundToInt(),
                s.avgHeartRate?.roundToInt(), s.maxHeartRate, s.avgCadenceRpm.roundToInt(), s.maxCadenceRpm,
                s.avgPowerW.roundToInt(), s.maxPowerW, SPORT_CYCLING, SUB_SPORT_INDOOR_CYCLING,
            )
        }

        val total = record.summary
        data.define(
            SESSION, 18,
            listOf(
                Field(254, UINT16), Field(253, UINT32), Field(0, ENUM), Field(1, ENUM), Field(2, UINT32),
                Field(5, ENUM), Field(6, ENUM), Field(7, UINT32), Field(8, UINT32), Field(9, UINT32), Field(11, UINT16),
                Field(16, UINT8), Field(17, UINT8), Field(18, UINT8), Field(19, UINT8), Field(20, UINT16), Field(21, UINT16),
                Field(25, UINT16), Field(26, UINT16),
            ),
        )
        data.message(
            SESSION,
            0, end, EVENT_SESSION, EVENT_TYPE_STOP, start, SPORT_CYCLING, SUB_SPORT_INDOOR_CYCLING,
            count * 1000L, count * 1000L, ((distance.lastOrNull() ?: 0.0) * 100).roundToLong(), total.kcal.roundToInt(),
            total.avgHeartRate?.roundToInt(), total.maxHeartRate, total.avgCadenceRpm.roundToInt(), total.maxCadenceRpm,
            total.avgPowerW.roundToInt(), total.maxPowerW, 0, laps.size,
        )

        data.define(ACTIVITY, 34, listOf(Field(253, UINT32), Field(0, UINT32), Field(1, UINT16), Field(2, ENUM), Field(3, ENUM), Field(4, ENUM), Field(5, UINT32)))
        val offset = zone.rules.getOffset(java.time.Instant.ofEpochMilli(record.startEpochMs)).totalSeconds
        data.message(ACTIVITY, end, count * 1000L, 1, ACTIVITY_MANUAL, EVENT_ACTIVITY, EVENT_TYPE_STOP, end + offset)

        return data.toFile()
    }

    /** FIT timestamps count seconds since 1989-12-31T00:00:00Z. */
    private fun fitTime(epochMs: Long): Long = epochMs / 1000 - FIT_EPOCH_OFFSET

    private const val FIT_EPOCH_OFFSET = 631_065_600L

    // Local message types.
    private const val FILE_ID = 0
    private const val EVENT = 1
    private const val RECORD = 2
    private const val LAP = 3
    private const val SESSION = 4
    private const val ACTIVITY = 5

    // Profile values.
    private const val FILE_TYPE_ACTIVITY = 4
    private const val MANUFACTURER_DEVELOPMENT = 255
    private const val EVENT_TIMER = 0
    private const val EVENT_SESSION = 8
    private const val EVENT_LAP = 9
    private const val EVENT_ACTIVITY = 26
    private const val EVENT_TYPE_START = 0
    private const val EVENT_TYPE_STOP = 1
    private const val EVENT_TYPE_STOP_ALL = 4
    private const val SPORT_CYCLING = 2
    private const val SUB_SPORT_INDOOR_CYCLING = 6
    private const val ACTIVITY_MANUAL = 0
}

internal enum class BaseType(val id: Int, val size: Int, val invalid: Long) {
    ENUM_T(0x00, 1, 0xFF),
    UINT8_T(0x02, 1, 0xFF),
    UINT16_T(0x84, 2, 0xFFFF),
    UINT32_T(0x86, 4, 0xFFFF_FFFFL),
}

private val ENUM = BaseType.ENUM_T
private val UINT8 = BaseType.UINT8_T
private val UINT16 = BaseType.UINT16_T
private val UINT32 = BaseType.UINT32_T

internal data class Field(val number: Int, val type: BaseType)

/** Minimal little-endian FIT encoder: definition and data messages, header and CRCs. */
internal class FitEncoder {
    private val body = ByteArrayOutputStream()
    private val definitions = mutableMapOf<Int, List<Field>>()

    fun define(localType: Int, globalMessage: Int, fields: List<Field>) {
        definitions[localType] = fields
        body.write(0x40 or localType) // definition message header
        body.write(0) // reserved
        body.write(0) // little-endian architecture
        writeLe(body, globalMessage.toLong(), 2)
        body.write(fields.size)
        for (f in fields) {
            body.write(f.number)
            body.write(f.type.size)
            body.write(f.type.id)
        }
    }

    /** Writes a data message; a null value is encoded as the type's "invalid" marker. */
    fun message(localType: Int, vararg values: Number?) {
        val fields = definitions.getValue(localType)
        require(values.size == fields.size) { "Expected ${fields.size} values, got ${values.size}" }
        body.write(localType)
        fields.forEachIndexed { i, f -> writeLe(body, values[i]?.toLong() ?: f.type.invalid, f.type.size) }
    }

    fun toFile(): ByteArray {
        val data = body.toByteArray()
        val header = ByteArrayOutputStream()
        header.write(14) // header size
        header.write(0x20) // protocol version 2.0
        writeLe(header, 2132, 2) // profile version 21.32
        writeLe(header, data.size.toLong(), 4)
        header.write(".FIT".toByteArray(Charsets.US_ASCII))
        val headerCrc = crc(header.toByteArray())
        writeLe(header, headerCrc.toLong(), 2)

        val out = ByteArrayOutputStream()
        out.write(header.toByteArray())
        out.write(data)
        val fileCrc = crc(out.toByteArray())
        writeLe(out, fileCrc.toLong(), 2)
        return out.toByteArray()
    }

    private fun writeLe(out: ByteArrayOutputStream, value: Long, size: Int) {
        for (i in 0 until size) out.write(((value shr (8 * i)) and 0xFF).toInt())
    }

    companion object {
        private val CRC_TABLE = intArrayOf(
            0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
            0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400,
        )

        /** FIT CRC-16, as specified by the FIT protocol. */
        fun crc(bytes: ByteArray): Int {
            var crc = 0
            for (b in bytes) {
                val byte = b.toInt() and 0xFF
                var tmp = CRC_TABLE[crc and 0xF]
                crc = (crc shr 4) and 0x0FFF
                crc = crc xor tmp xor CRC_TABLE[byte and 0xF]
                tmp = CRC_TABLE[crc and 0xF]
                crc = (crc shr 4) and 0x0FFF
                crc = crc xor tmp xor CRC_TABLE[(byte shr 4) and 0xF]
            }
            return crc
        }
    }
}
