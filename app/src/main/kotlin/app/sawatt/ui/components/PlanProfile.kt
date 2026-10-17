package app.sawatt.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.sawatt.core.workout.PlannedInterval
import app.sawatt.ui.theme.SawattColors

/**
 * Intensity profile of a plan: one bar per interval, its width the duration, its height and colour
 * the power zone. With [progressSec], intervals not started yet are dimmed and a cursor marks the
 * current position.
 */
@Composable
fun PlanProfile(
    intervals: List<PlannedInterval>,
    ftpW: Double,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    progressSec: Int? = null,
) {
    val bars = remember(intervals, ftpW) {
        intervals.map { p ->
            val pct = p.block.targetFtpPercent?.toDouble()
                ?: if (ftpW > 0) p.block.targetWatts(ftpW) * 100 / ftpW else 0.0
            p.block.durationSec to pct
        }
    }
    val total = bars.sumOf { it.first }
    if (total <= 0) return

    Canvas(modifier.fillMaxWidth().height(height)) {
        val gap = if (bars.size > 60) 0f else 2.dp.toPx()
        val usable = size.width - gap * (bars.size - 1)
        val radius = CornerRadius(3.dp.toPx())
        var x = 0f
        var startSec = 0
        var cursorX: Float? = null
        bars.forEach { (sec, pct) ->
            val w = usable * sec / total
            val h = size.height * (pct / 125).coerceIn(0.08, 1.0).toFloat()
            val started = progressSec == null || startSec <= progressSec
            drawRoundRect(
                color = zoneColor(pct).copy(alpha = if (started) 1f else 0.3f),
                topLeft = Offset(x, size.height - h),
                size = Size(w, h),
                cornerRadius = radius,
            )
            if (progressSec != null && progressSec >= startSec && progressSec < startSec + sec) {
                cursorX = x + w * (progressSec - startSec) / sec
            }
            x += w + gap
            startSec += sec
        }
        cursorX?.let { cx ->
            drawLine(SawattColors.Text, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 2.dp.toPx())
        }
    }
}
