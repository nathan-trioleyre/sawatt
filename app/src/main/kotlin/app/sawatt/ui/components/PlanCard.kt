package app.sawatt.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.sawatt.core.workout.WorkoutPlan
import app.sawatt.ui.formatDuration
import app.sawatt.ui.theme.SawattColors

fun planMeta(plan: WorkoutPlan): String {
    val n = plan.flatten().size
    return "${formatDuration(plan.totalDurationSec)} · $n interval" + if (n > 1) "s" else ""
}

/** A plan with its name, duration and intensity profile. */
@Composable
fun PlanCard(
    plan: WorkoutPlan,
    ftpW: Double,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    profileHeight: Dp = 44.dp,
    action: (@Composable () -> Unit)? = null,
) {
    SectionCard(modifier, onClick = onClick, spacing = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(plan.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(planMeta(plan), style = MaterialTheme.typography.bodySmall, color = SawattColors.TextSecondary)
            }
            action?.invoke()
        }
        PlanProfile(plan.flatten(), ftpW, height = profileHeight)
    }
}

/** Small round play button. */
@Composable
fun PlayButton(label: String, onClick: () -> Unit, enabled: Boolean = true, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(if (enabled) SawattColors.Accent else SawattColors.Muted)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = label, tint = SawattColors.OnAccent, modifier = Modifier.size(size * 0.5f))
    }
}
