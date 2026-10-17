package app.sawatt.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.sawatt.core.workout.IntervalRecord
import app.sawatt.ui.theme.SawattColors

/**
 * Power trace (smoothed over 5 s), target power steps and interval boundaries.
 */
@Composable
fun PowerChart(power: List<Int>, intervals: List<IntervalRecord>, modifier: Modifier = Modifier) {
    if (power.isEmpty()) return
    val smoothed = remember5s(power)
    val maxY = maxOf(smoothed.maxOrNull() ?: 0.0, intervals.maxOfOrNull { it.targetPowerW ?: 0 }?.toDouble() ?: 0.0, 100.0) * 1.1

    Canvas(modifier.fillMaxWidth().height(120.dp)) {
        val w = size.width
        val h = size.height
        fun x(sec: Int) = sec.toFloat() / power.size * w
        fun y(watts: Double) = (h - watts / maxY * h).toFloat()

        // Interval boundaries.
        intervals.drop(1).forEach { iv ->
            drawLine(SawattColors.Hairline, Offset(x(iv.startOffsetSec), 0f), Offset(x(iv.startOffsetSec), h), strokeWidth = 1.dp.toPx())
        }
        // Power: filled area under the line.
        val line = Path()
        smoothed.forEachIndexed { i, v ->
            if (i == 0) line.moveTo(x(i), y(v)) else line.lineTo(x(i), y(v))
        }
        val area = Path().apply {
            addPath(line)
            lineTo(x(smoothed.lastIndex), h)
            lineTo(0f, h)
            close()
        }
        drawPath(area, SawattColors.Power.copy(alpha = 0.14f))
        drawPath(line, SawattColors.Power, style = Stroke(width = 1.5.dp.toPx()))
        // Targets.
        intervals.forEach { iv ->
            val t = iv.targetPowerW ?: return@forEach
            drawLine(
                SawattColors.Text.copy(alpha = 0.85f),
                Offset(x(iv.startOffsetSec), y(t.toDouble())),
                Offset(x(iv.startOffsetSec + iv.stats.durationSec), y(t.toDouble())),
                strokeWidth = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
            )
        }
        drawLine(SawattColors.Hairline, Offset(0f, h), Offset(w, h), strokeWidth = 1.dp.toPx())
    }
}

private fun remember5s(power: List<Int>): List<Double> {
    val out = ArrayList<Double>(power.size)
    var sum = 0.0
    for (i in power.indices) {
        sum += power[i]
        if (i >= 5) sum -= power[i - 5]
        out += sum / minOf(i + 1, 5)
    }
    return out
}
