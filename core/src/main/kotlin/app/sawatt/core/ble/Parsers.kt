package app.sawatt.core.ble

/** Little-endian helpers over a raw notification payload. */
private fun ByteArray.u8(i: Int) = this[i].toInt() and 0xFF
private fun ByteArray.u16(i: Int) = u8(i) or (u8(i + 1) shl 8)
private fun ByteArray.s16(i: Int) = u16(i).toShort().toInt()

/** One Cycling Power Measurement (0x2A63) notification. */
data class PowerMeasurement(
    val flags: Int,
    val powerW: Int,
    /** Cumulative crank revolutions (uint16), or null when the packet carries none. */
    val crankRevolutions: Int?,
    /** Last crank event time in 1/1024 s (uint16), or null. */
    val lastCrankEventTime: Int?,
)

object CyclingPowerParser {
    private const val FLAG_PEDAL_BALANCE = 1 shl 0
    private const val FLAG_ACCUMULATED_TORQUE = 1 shl 2
    private const val FLAG_WHEEL_DATA = 1 shl 4
    private const val FLAG_CRANK_DATA = 1 shl 5

    /**
     * Parses per the Bluetooth CPS spec, using the flags to locate the crank data.
     *
     * Fallback: if the flags do not announce crank data but the packet is at least 14 bytes long,
     * read the fixed offsets of the "wheel + crank" layout (flags 0x0030): crank revolutions at
     * 10..11, event time at 12..13. Some bikes send this layout without setting the flags.
     */
    fun parse(data: ByteArray): PowerMeasurement? {
        if (data.size < 4) return null
        val flags = data.u16(0)
        val power = data.s16(2)
        var offset = 4
        if (flags and FLAG_PEDAL_BALANCE != 0) offset += 1
        if (flags and FLAG_ACCUMULATED_TORQUE != 0) offset += 2
        if (flags and FLAG_WHEEL_DATA != 0) offset += 6

        return when {
            flags and FLAG_CRANK_DATA != 0 && data.size >= offset + 4 ->
                PowerMeasurement(flags, power, data.u16(offset), data.u16(offset + 2))
            data.size >= 14 ->
                PowerMeasurement(flags, power, data.u16(10), data.u16(12))
            else -> PowerMeasurement(flags, power, null, null)
        }
    }
}

object HeartRateParser {
    /** Heart Rate Measurement (0x2A37): bit 0 of the flags selects a uint8 or uint16 value. */
    fun parse(data: ByteArray): Int? {
        if (data.size < 2) return null
        val flags = data.u8(0)
        return if (flags and 1 != 0) {
            if (data.size < 3) null else data.u16(1)
        } else {
            data.u8(1)
        }
    }
}

object AdvertisingParser {
    /**
     * Extracts 16-bit service UUIDs (AD types 0x02/0x03) from a raw advertising payload, as
     * uppercase 4-digit hex strings ("1818", "180D"). Useful on Android, where scan results do not
     * always list service UUIDs.
     */
    fun serviceUuids16(raw: ByteArray): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < raw.size) {
            val len = raw.u8(i)
            // An AD structure spans raw[i .. i+len]: length byte, type byte, then len-1 data bytes.
            if (len == 0 || i + len >= raw.size) break
            val type = raw.u8(i + 1)
            if (type == 0x02 || type == 0x03) {
                var p = i + 2
                while (p + 1 <= i + len) {
                    out += "%04X".format(raw.u16(p))
                    p += 2
                }
            }
            i += len + 1
        }
        return out
    }
}
