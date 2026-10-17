package app.sawatt.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sawatt.R
import app.sawatt.ui.Bucket
import app.sawatt.ui.theme.SawattColors

/**
 * Riding time per bucket as vertical bars. Days without a ride keep a short grey stub, so the
 * rhythm of the period stays readable.
 */
@Composable
fun PeriodBars(
    buckets: List<Bucket>,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
    barColor: (Bucket) -> Color = { SawattColors.Accent },
    showMinutes: Boolean = false,
    labelEvery: Int = 1,
) {
    val max = buckets.maxOfOrNull { it.seconds }?.takeIf { it > 0 } ?: 1
    val gap = if (buckets.size > 12) 3.dp else 8.dp
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
        buckets.forEachIndexed { i, b ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (showMinutes) {
                    Text(
                        if (b.seconds > 0) "${b.seconds / 60}'" else "",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = SawattColors.TextSecondary,
                    )
                }
                Box(Modifier.height(height).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    val fraction = (b.seconds.toFloat() / max).coerceAtLeast(0.05f)
                    Box(
                        Modifier.widthIn(max = 30.dp).fillMaxWidth().fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (b.seconds == 0) SawattColors.Track else barColor(b)),
                    )
                }
                Text(
                    if (i % labelEvery == 0) b.label else "",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (b.isCurrent) SawattColors.Accent else SawattColors.TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Share of time in each power zone, as one segmented bar with its legend. */
@Composable
fun ZoneBar(zoneSeconds: List<Int>, modifier: Modifier = Modifier) {
    val total = zoneSeconds.sum().takeIf { it > 0 } ?: return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricLabel(stringResource(R.string.workout_time_in_zones))
        Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            zoneSeconds.forEachIndexed { i, sec ->
                if (sec > 0) Box(Modifier.weight(sec.toFloat() / total).fillMaxHeight().background(SawattColors.Zones[i]))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            zoneSeconds.forEachIndexed { i, sec ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(SawattColors.Zones[i])
                    Text(
                        " Z${i + 1} ${(sec + 30) / 60}'",
                        style = MaterialTheme.typography.bodySmall,
                        color = SawattColors.TextSecondary,
                    )
                }
            }
        }
    }
}
