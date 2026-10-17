package app.sawatt.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sawatt.core.workout.IntervalRecord
import app.sawatt.ui.formatDuration
import app.sawatt.ui.theme.SawattColors
import app.sawatt.ui.theme.SawattFonts
import kotlin.math.abs
import kotlin.math.roundToInt

private val timeWidth = 52.dp
private val avgWidth = 44.dp
private val deltaWidth = 62.dp

/**
 * One row per interval: zone, name, time, average power and its gap to the target, with the other
 * statistics on a second line. Used by the end-of-workout summary, the history and the live screen.
 */
@Composable
fun IntervalTable(intervals: List<IntervalRecord>, ftpW: Double, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetricLabel("Interval", modifier = Modifier.weight(1f).padding(start = 12.dp))
            MetricLabel("Time", modifier = Modifier.width(timeWidth), textAlign = TextAlign.End)
            MetricLabel("Avg W", modifier = Modifier.width(avgWidth), textAlign = TextAlign.End)
            MetricLabel("vs target", modifier = Modifier.width(deltaWidth), textAlign = TextAlign.End)
        }
        HorizontalDivider(thickness = 1.dp, color = SawattColors.Hairline)
        intervals.forEach { iv ->
            IntervalRow(iv, ftpW)
            HorizontalDivider(thickness = 1.dp, color = SawattColors.Hairline)
        }
        if (intervals.any { it.endedEarly }) {
            Text(
                "* block ended early (Lap or Finish)",
                style = MaterialTheme.typography.bodySmall,
                color = SawattColors.TextSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun IntervalRow(iv: IntervalRecord, ftpW: Double) {
    val s = iv.stats
    val reference = iv.targetPowerW?.toDouble() ?: s.avgPowerW
    val zone = iv.targetFtpPercent?.toDouble() ?: if (ftpW > 0) reference * 100 / ftpW else 0.0
    val details = buildList {
        iv.targetPowerW?.let { add("Target $it W" + (iv.targetFtpPercent?.let { p -> " ($p%)" } ?: "")) }
        add("max ${s.maxPowerW} W")
        add("${s.avgCadenceRpm.roundToInt()} rpm" + (iv.targetCadenceRpm?.let { " / $it" } ?: ""))
        s.avgHeartRate?.let { add("${it.roundToInt()} bpm") }
    }.joinToString(" · ")

    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.width(4.dp).height(32.dp).clip(RoundedCornerShape(2.dp)).background(zoneColor(zone)))
        Column(Modifier.weight(1f)) {
            Text(iv.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(details, style = MaterialTheme.typography.bodySmall, fontSize = 12.sp, color = SawattColors.TextSecondary)
        }
        Text(
            formatDuration(s.durationSec) + if (iv.endedEarly) "*" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = SawattColors.TextSecondary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(timeWidth),
        )
        Text(
            s.avgPowerW.roundToInt().toString(),
            fontFamily = SawattFonts.Numbers,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(avgWidth),
        )
        Box(Modifier.width(deltaWidth), contentAlignment = Alignment.CenterEnd) {
            val target = iv.targetPowerW
            val color = targetColor(s.avgPowerW, target)
            if (target != null && color != null) {
                val d = (s.avgPowerW - target).roundToInt()
                val sign = when {
                    d > 0 -> "+"
                    d < 0 -> "−"
                    else -> "±"
                }
                Text(
                    "$sign${abs(d)} W",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.14f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            } else {
                Text("–", color = SawattColors.TextSecondary)
            }
        }
    }
}
