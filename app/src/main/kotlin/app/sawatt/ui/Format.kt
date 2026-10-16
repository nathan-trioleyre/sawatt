package app.sawatt.ui

import app.sawatt.core.model.UnitSystem
import app.sawatt.core.ride.RideMath
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

fun formatDuration(totalSec: Int): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatDuration(totalSec: Long): String = formatDuration(totalSec.toInt())

/** Parses "15" (minutes), "15:00", "1:30" or "1:00:00" into seconds. */
fun parseDuration(text: String): Int? {
    val parts = text.trim().split(":").map { it.trim() }
    if (parts.any { it.isEmpty() || it.toIntOrNull() == null }) return null
    val n = parts.map { it.toInt() }
    return when (n.size) {
        1 -> n[0] * 60
        2 -> n[0] * 60 + n[1]
        3 -> n[0] * 3600 + n[1] * 60 + n[2]
        else -> null
    }?.takeIf { it > 0 }
}

fun formatDistance(km: Double, units: UnitSystem): String =
    if (units == UnitSystem.IMPERIAL) "%.1f mi".format(km / RideMath.KM_PER_MILE) else "%.1f km".format(km)

fun formatWatts(w: Double) = "${w.roundToInt()} W"

fun formatOrDash(v: Double?) = v?.roundToInt()?.toString() ?: "–"

private val dateTimeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

fun formatDateTime(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(dateTimeFormat)
