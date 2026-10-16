package app.sawatt.core

import app.sawatt.core.ble.AdvertisingParser
import app.sawatt.core.ble.CadenceCalculator
import app.sawatt.core.ble.CyclingPowerParser
import app.sawatt.core.ble.HeartRateParser
import app.sawatt.core.ble.PowerMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProtocolTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    /** Drapeaux 0x0030 (données roue + pédalier). */
    private fun cpsPacket(power: Int, wheelRevs: Long, wheelTime: Int, crankRevs: Int, crankTime: Int) = bytes(
        0x30, 0x00,
        power and 0xFF, (power shr 8) and 0xFF,
        (wheelRevs and 0xFF).toInt(), ((wheelRevs shr 8) and 0xFF).toInt(),
        ((wheelRevs shr 16) and 0xFF).toInt(), ((wheelRevs shr 24) and 0xFF).toInt(),
        wheelTime and 0xFF, (wheelTime shr 8) and 0xFF,
        crankRevs and 0xFF, (crankRevs shr 8) and 0xFF,
        crankTime and 0xFF, (crankTime shr 8) and 0xFF,
    )

    @Test
    fun `parses power and crank data with wheel and crank flags`() {
        val m = CyclingPowerParser.parse(cpsPacket(power = 254, wheelRevs = 1000, wheelTime = 77, crankRevs = 300, crankTime = 40000))!!
        assertEquals(254, m.powerW)
        assertEquals(300, m.crankRevolutions)
        assertEquals(40000, m.lastCrankEventTime)
    }

    @Test
    fun `crank-only packet uses flag-driven offsets`() {
        // flags 0x0020: crank data right after power.
        val m = CyclingPowerParser.parse(bytes(0x20, 0x00, 0x2C, 0x01, 0x05, 0x00, 0x00, 0x04))!!
        assertEquals(300, m.powerW)
        assertEquals(5, m.crankRevolutions)
        assertEquals(1024, m.lastCrankEventTime)
    }

    @Test
    fun `power is signed`() {
        assertEquals(-2, CyclingPowerParser.parse(bytes(0, 0, 0xFE, 0xFF))!!.powerW)
    }

    @Test
    fun `heart rate uint8 and uint16`() {
        assertEquals(72, HeartRateParser.parse(bytes(0x00, 72)))
        assertEquals(300, HeartRateParser.parse(bytes(0x01, 0x2C, 0x01)))
        assertNull(HeartRateParser.parse(bytes(0x01, 0x2C)))
    }

    @Test
    fun `extracts 16-bit service uuids from raw advertising`() {
        val adv = bytes(
            0x02, 0x01, 0x06, // flags
            0x05, 0x03, 0x18, 0x18, 0x0A, 0x18, // complete list: 1818, 180A
            0x04, 0x09, 'S'.code, 'M'.code, '+'.code, // name
            0x00, 0x00, // padding
        )
        assertEquals(listOf("1818", "180A"), AdvertisingParser.serviceUuids16(adv))
    }

    @Test
    fun `truncated advertising structure is ignored`() {
        assertEquals(emptyList<String>(), AdvertisingParser.serviceUuids16(bytes(0x05, 0x03, 0x0D)))
    }

    private fun m(revs: Int, time: Int, power: Int = 200) = PowerMeasurement(0x30, power, revs, time)

    @Test
    fun `cadence algorithm`() {
        val calc = CadenceCalculator()
        assertNull("first packet primes", calc.onMeasurement(0, m(10, 0)))
        assertNull("throttled under 500 ms", calc.onMeasurement(400, m(11, 683)))
        // 1 rev in 683/1024 s → 89.96 → 90 rpm
        assertEquals(90, calc.onMeasurement(1000, m(11, 683))!!.cadenceRpm)
        // no new revolution: previous cadence kept once, then 0
        assertEquals(90, calc.onMeasurement(1500, m(11, 683))!!.cadenceRpm)
        assertEquals(0, calc.onMeasurement(2000, m(11, 683))!!.cadenceRpm)
    }

    @Test
    fun `event time and revolution counters wrap`() {
        val calc = CadenceCalculator()
        calc.onMeasurement(0, m(65535, 65000))
        // +2 revs, time 65000 → 1000 is 1536 ticks = 1.5 s → 80 rpm
        assertEquals(80, calc.onMeasurement(1000, m(1, 1000))!!.cadenceRpm)
    }

    @Test
    fun `implausible cadence keeps the previous value`() {
        val calc = CadenceCalculator()
        calc.onMeasurement(0, m(0, 0))
        assertEquals(60, calc.onMeasurement(1000, m(1, 1024))!!.cadenceRpm)
        // 10 revs in 1/1024 s
        assertEquals(60, calc.onMeasurement(2000, m(11, 1025))!!.cadenceRpm)
    }
}
