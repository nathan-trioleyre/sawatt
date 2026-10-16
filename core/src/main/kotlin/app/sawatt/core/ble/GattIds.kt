package app.sawatt.core.ble

/**
 * 16-bit Bluetooth SIG identifiers used by SaWatt.
 *
 * The bike is a standard Cycling Power sensor; heart-rate straps are standard HRM sensors.
 * SaWatt only reads: it never writes to the bike.
 */
object GattIds {
    const val SERVICE_CYCLING_POWER = 0x1818
    const val CHAR_CYCLING_POWER_MEASUREMENT = 0x2A63

    const val SERVICE_HEART_RATE = 0x180D
    const val CHAR_HEART_RATE_MEASUREMENT = 0x2A37

    const val SERVICE_DEVICE_INFORMATION = 0x180A
    const val CHAR_FIRMWARE_REVISION = 0x2A26

    const val DESCRIPTOR_CCCD = 0x2902

    /** Names advertised by some bikes that do not always advertise their service: "SMART+ #1234", "PHANTOM+ #1234". */
    val BIKE_NAME_PREFIXES = listOf("SMART+ #", "PHANTOM+ #")

    /** Expands a 16-bit SIG id to its full 128-bit UUID string. */
    fun uuid16(id: Int): String = "0000%04x-0000-1000-8000-00805f9b34fb".format(id)
}
