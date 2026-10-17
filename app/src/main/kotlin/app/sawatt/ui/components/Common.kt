package app.sawatt.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sawatt.core.ride.RideMath
import app.sawatt.ui.theme.SawattColors
import app.sawatt.ui.theme.SawattFonts

/** Compact bar for pushed screens (editor, workout detail…); tab screens use [LargeTitle]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SawattTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = SawattColors.TextSecondary, maxLines = 1) }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = SawattColors.Background),
    )
}

/** Big bold screen title, with an optional small line above it and an action on the right. */
@Composable
fun LargeTitle(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    action: @Composable () -> Unit = {},
) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            overline?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelMedium, color = SawattColors.TextSecondary) }
            Text(title, style = MaterialTheme.typography.headlineLarge)
        }
        action()
    }
}

/** Section title with an optional link on the right ("See all"). */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontSize = 20.sp, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = SawattColors.Accent,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onAction).padding(4.dp),
            )
        }
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    spacing: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SawattColors.Surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** Small uppercase label above a number; coloured with the metric's colour when it has one. */
@Composable
fun MetricLabel(
    text: String,
    color: Color = SawattColors.TextSecondary,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, textAlign = textAlign, modifier = modifier)
}

/** A big condensed number with its unit, e.g. "238 W". */
@Composable
fun Metric(
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    color: Color = SawattColors.Text,
    size: TextUnit = 28.sp,
) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            value,
            fontFamily = SawattFonts.Numbers,
            fontWeight = FontWeight.SemiBold,
            fontSize = size,
            lineHeight = size,
            color = color,
            modifier = Modifier.alignByBaseline(),
        )
        if (unit != null) {
            Text(
                " $unit",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = SawattColors.TextSecondary,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

/** A labelled number, e.g. "AVG POWER / 247 W". */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    labelColor: Color = SawattColors.TextSecondary,
    valueColor: Color = SawattColors.Text,
    valueSize: TextUnit = 28.sp,
) {
    Column(modifier) {
        MetricLabel(label, labelColor)
        Metric(value, unit = unit, color = valueColor, size = valueSize)
    }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(6.dp).clip(CircleShape).background(color))
}

/** Large round action button with its label underneath (Start, Pause, Lap…). */
@Composable
fun RoundButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = SawattColors.Accent,
    content: Color = SawattColors.OnAccent,
    size: Dp = 72.dp,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(size).clip(CircleShape).background(container).clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = content, modifier = Modifier.size(size * 0.4f))
        }
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = SawattColors.TextSecondary)
    }
}

/** Colour of a power value against its target: blue under −5 %, mint within ±5 %, coral above +5 %. */
fun targetColor(value: Double, target: Int?): Color? {
    if (target == null || target <= 0) return null
    val ratio = value / target
    return when {
        ratio < 0.95 -> SawattColors.Below
        ratio > 1.05 -> SawattColors.Above
        else -> SawattColors.OnTarget
    }
}

/** Colour of a cadence against its target, with a ±5 rpm tolerance. */
fun cadenceColor(rpm: Int, target: Int?): Color? {
    if (target == null) return null
    return when {
        rpm < target - 5 -> SawattColors.Below
        rpm > target + 5 -> SawattColors.Above
        else -> SawattColors.OnTarget
    }
}

fun zoneColor(percentFtp: Double): Color = SawattColors.Zones[RideMath.powerZone(percentFtp)]
