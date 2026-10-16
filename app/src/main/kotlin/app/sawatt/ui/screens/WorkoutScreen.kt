package app.sawatt.ui.screens

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sawatt.AppContainer
import app.sawatt.ble.LinkState
import app.sawatt.core.model.UnitSystem
import app.sawatt.core.workout.CurrentInterval
import app.sawatt.core.workout.IntervalKind
import app.sawatt.core.workout.LiveState
import app.sawatt.core.workout.WorkoutPlan
import app.sawatt.ui.components.SawattTopBar
import app.sawatt.ui.components.IntervalTable
import app.sawatt.ui.components.SectionCard
import app.sawatt.ui.components.StatTile
import app.sawatt.ui.components.targetColor
import app.sawatt.ui.formatDistance
import app.sawatt.ui.formatDuration
import app.sawatt.ui.formatOrDash
import app.sawatt.ui.theme.SawattColors
import app.sawatt.workout.SensorReadings
import app.sawatt.workout.WorkoutStatus
import kotlin.math.roundToInt

@Composable
fun WorkoutScreen(
    container: AppContainer,
    planId: String?,
    onBack: () -> Unit,
    onDevices: () -> Unit,
    onFinished: (saved: Boolean) -> Unit,
) {
    val controller = container.workout
    val state by controller.state.collectAsStateWithLifecycle()
    val readings by controller.readings.collectAsStateWithLifecycle()
    val app by container.settings.app.collectAsStateWithLifecycle()
    val profile by container.settings.profile.collectAsStateWithLifecycle()
    val bike by container.ble.bike.collectAsStateWithLifecycle()
    val hr by container.ble.heartRateSensor.collectAsStateWithLifecycle()
    val plans by container.plans.plans.collectAsStateWithLifecycle()
    val plan: WorkoutPlan? = state.plan ?: planId?.let { id -> plans.firstOrNull { it.id == id } }
    var confirmDiscard by remember { mutableStateOf(false) }

    KeepScreenOn(app.keepScreenOn && state.status != WorkoutStatus.IDLE)

    val bikeOk = app.simulateBike || bike?.state == LinkState.CONNECTED
    Scaffold(
        topBar = {
            SawattTopBar(plan?.name ?: "Free ride", onBack) {
                IconButton(onClick = onDevices) {
                    Icon(Icons.AutoMirrored.Filled.DirectionsBike, "Bike", tint = if (bikeOk) SawattColors.OnTarget else Color.Gray)
                }
                Icon(
                    Icons.Default.Favorite, "Heart rate",
                    tint = if (app.simulateBike || hr?.state == LinkState.CONNECTED) SawattColors.HeartRed else Color.Gray,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        },
        bottomBar = {
            Controls(
                status = state.status,
                onStart = { controller.start(plan) },
                onPause = controller::pause,
                onResume = controller::resume,
                onLap = controller::lap,
                onFinish = { onFinished(controller.finish() != null) },
                onDiscard = { confirmDiscard = true },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val live = state.live
            if (live == null) {
                Idle(plan, readings, bikeOk, onDevices)
            } else {
                Live(live, readings, state.status, profile.units)
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard workout?") },
            text = { Text("Nothing will be saved.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    controller.discard()
                    onFinished(false)
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Idle(plan: WorkoutPlan?, readings: SensorReadings, bikeOk: Boolean, onDevices: () -> Unit) {
    if (!bikeOk) {
        SectionCard {
            Text("No bike connected", style = MaterialTheme.typography.titleMedium)
            Text("You can start anyway; data appears as soon as the bike is connected.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onDevices) {
                Icon(Icons.Default.Bluetooth, null)
                Spacer(Modifier.width(8.dp))
                Text("Connect devices")
            }
        }
    }
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatTile("Power", readings.powerW.toString(), unit = "W", valueSize = 40.sp)
            StatTile("Cadence", readings.cadenceRpm.toString(), unit = "rpm", valueSize = 40.sp)
            StatTile("Heart", if (readings.heartRate > 0) readings.heartRate.toString() else "–", unit = "bpm", valueSize = 40.sp)
        }
    }
    if (plan != null) {
        SectionCard {
            Text("${plan.name} · ${formatDuration(plan.totalDurationSec)}", style = MaterialTheme.typography.titleMedium)
            plan.flatten().forEach { p ->
                val cadence = p.block.targetCadenceRpm?.let { " · $it rpm" } ?: ""
                Text("${p.label} — ${formatDuration(p.block.durationSec)} @ ${p.block.targetPowerW} W$cadence", style = MaterialTheme.typography.bodySmall)
            }
        }
    } else {
        Text(
            "Free ride: press Lap at any time to start a new interval with its own averages.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Live(live: LiveState, readings: SensorReadings, status: WorkoutStatus, units: UnitSystem) {
    val c = live.current
    CurrentIntervalCard(c, live, status)

    // Instantaneous values, coloured against the target.
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatTile(
                "Power", readings.powerW.toString(), unit = "W", valueSize = 52.sp,
                valueColor = targetColor(readings.powerW.toDouble(), c.targetPowerW) ?: MaterialTheme.colorScheme.onSurface,
            )
            StatTile("Cadence", readings.cadenceRpm.toString(), unit = "rpm", valueSize = 52.sp,
                valueColor = cadenceColor(readings.cadenceRpm, c.targetCadenceRpm) ?: MaterialTheme.colorScheme.onSurface)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatTile("Heart rate", if (readings.heartRate > 0) readings.heartRate.toString() else "–", unit = "bpm")
            StatTile("Total time", formatDuration(live.elapsedSec))
        }
    }

    // Per-interval statistics since the start of this interval.
    SectionCard {
        Text("THIS INTERVAL", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        val s = c.stats
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatTile(
                "Avg power", s.avgPowerW.roundToInt().toString(), unit = "W", valueSize = 44.sp,
                valueColor = targetColor(s.avgPowerW, c.targetPowerW) ?: MaterialTheme.colorScheme.onSurface,
            )
            StatTile("Max power", s.maxPowerW.toString(), unit = "W")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatTile("Avg cadence", s.avgCadenceRpm.roundToInt().toString(), unit = "rpm")
            StatTile("Avg HR", formatOrDash(s.avgHeartRate), unit = "bpm")
        }
    }

    SectionCard {
        Text("WHOLE WORKOUT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val s = live.session
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatTile("Avg power", s.avgPowerW.roundToInt().toString(), unit = "W", valueSize = 20.sp)
            StatTile("Distance", formatDistance(s.distanceKm, units), valueSize = 20.sp)
            StatTile("Energy", s.kcal.roundToInt().toString(), unit = "kcal", valueSize = 20.sp)
        }
    }

    if (live.completed.isNotEmpty()) {
        SectionCard {
            Text("COMPLETED INTERVALS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IntervalTable(live.completed.reversed())
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun CurrentIntervalCard(c: CurrentInterval, live: LiveState, status: WorkoutStatus) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val position = if (c.kind == IntervalKind.PLANNED) "Interval ${c.index}/${live.plannedCount}" else "Interval ${c.index}"
                Text(position.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(c.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (c.targetPowerW != null) {
                    val cadence = c.targetCadenceRpm?.let { " · $it rpm" } ?: ""
                    Text("Target ${c.targetPowerW} W$cadence", style = MaterialTheme.typography.titleMedium, color = SawattColors.Zones[3])
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (c.remainingSec != null) "REMAINING" else "ELAPSED", style = MaterialTheme.typography.labelSmall)
                Text(
                    formatDuration(c.remainingSec ?: c.elapsedSec),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = if ((c.remainingSec ?: 99) <= 5) SawattColors.Zones[3] else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        c.plannedDurationSec?.let { total ->
            LinearProgressIndicator(progress = { c.elapsedSec / total.toFloat() }, modifier = Modifier.fillMaxWidth())
        }
        live.next?.let { n ->
            Text(
                "Next: ${n.label} · ${formatDuration(n.block.durationSec)} @ ${n.block.targetPowerW} W",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (live.planFinished && live.plannedCount > 0) {
            Text("Plan completed — keep riding or press Finish.", style = MaterialTheme.typography.bodyMedium, color = SawattColors.OnTarget)
        }
        if (status == WorkoutStatus.PAUSED) {
            Text("PAUSED", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

private fun cadenceColor(rpm: Int, target: Int?): Color? {
    if (target == null) return null
    return when {
        rpm < target - 5 -> SawattColors.Below
        rpm > target + 5 -> SawattColors.Above
        else -> SawattColors.OnTarget
    }
}

@Composable
private fun Controls(
    status: WorkoutStatus,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onLap: () -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val big = Modifier.weight(1f).height(56.dp)
        when (status) {
            WorkoutStatus.IDLE -> Button(onClick = onStart, modifier = big) {
                Icon(Icons.Default.PlayArrow, null)
                Text("Start")
            }
            WorkoutStatus.RUNNING -> {
                FilledTonalButton(onClick = onPause, modifier = big) {
                    Icon(Icons.Default.Pause, null)
                    Text("Pause")
                }
                Button(onClick = onLap, modifier = big) {
                    Icon(Icons.Default.Flag, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Lap")
                }
            }
            WorkoutStatus.PAUSED -> {
                Button(onClick = onResume, modifier = big) {
                    Icon(Icons.Default.PlayArrow, null)
                    Text("Resume")
                }
                FilledTonalButton(onClick = onFinish, modifier = big) {
                    Icon(Icons.Default.Stop, null)
                    Text("Finish")
                }
                OutlinedButton(
                    onClick = onDiscard,
                    modifier = Modifier.height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Discard") }
            }
        }
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(enabled) {
        if (enabled) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
