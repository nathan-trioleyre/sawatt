package app.sawatt.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.sawatt.core.workout.IntervalRecord
import app.sawatt.ui.formatDuration
import app.sawatt.ui.formatOrDash
import kotlin.math.roundToInt

private data class Column(val title: String, val width: Dp, val alignEnd: Boolean = true)

private val columns = listOf(
    Column("#", 28.dp),
    Column("Interval", 110.dp, alignEnd = false),
    Column("Time", 58.dp),
    Column("Target", 74.dp),
    Column("Avg W", 56.dp),
    Column("Max W", 56.dp),
    Column("Avg rpm", 62.dp),
    Column("Avg bpm", 62.dp),
)

/** One row per interval: the end-of-workout summary table, also used in the history detail. */
@Composable
fun IntervalTable(intervals: List<IntervalRecord>, modifier: Modifier = Modifier) {
    Column(modifier.horizontalScroll(rememberScrollState())) {
        Row(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(vertical = 6.dp)) {
            columns.forEach { Cell(it, it.title, bold = true) }
        }
        intervals.forEach { iv ->
            val s = iv.stats
            val target = buildString {
                iv.targetPowerW?.let { append("$it W") }
                iv.targetCadenceRpm?.let { append(" @$it") }
            }.ifEmpty { "–" }
            val time = formatDuration(s.durationSec) + if (iv.endedEarly) "*" else ""
            val avgColor = targetColor(s.avgPowerW, iv.targetPowerW)
            Row(Modifier.padding(vertical = 6.dp)) {
                Cell(columns[0], iv.index.toString())
                Cell(columns[1], iv.name)
                Cell(columns[2], time)
                Cell(columns[3], target)
                Cell(columns[4], s.avgPowerW.roundToInt().toString(), bold = true, color = avgColor)
                Cell(columns[5], s.maxPowerW.toString())
                Cell(columns[6], s.avgCadenceRpm.roundToInt().toString())
                Cell(columns[7], formatOrDash(s.avgHeartRate))
            }
            HorizontalDivider(Modifier.width(columns.fold(0.dp) { acc, c -> acc + c.width }))
        }
        if (intervals.any { it.endedEarly }) {
            Text(
                "* block ended early (Lap or Finish)",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun Cell(column: Column, text: String, bold: Boolean = false, color: Color? = null) {
    Text(
        text,
        modifier = Modifier.width(column.width).padding(horizontal = 4.dp),
        textAlign = if (column.alignEnd) TextAlign.End else TextAlign.Start,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = color ?: MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
