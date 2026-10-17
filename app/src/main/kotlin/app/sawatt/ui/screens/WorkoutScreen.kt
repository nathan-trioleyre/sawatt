package app.sawatt.ui.screens

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sawatt.AppContainer
import app.sawatt.R
import app.sawatt.core.model.UnitSystem
import app.sawatt.core.ride.RideMath
import app.sawatt.core.trainer.LinkState
import app.sawatt.core.workout.CurrentInterval
import app.sawatt.core.workout.IntervalKind
import app.sawatt.core.workout.LiveState
import app.sawatt.core.workout.WorkoutPlan
import app.sawatt.ui.intervalName
import app.sawatt.ui.components.IconTile
import app.sawatt.ui.components.IntervalTable
import app.sawatt.ui.components.Metric
import app.sawatt.ui.components.MetricLabel
import app.sawatt.ui.components.PlanProfile
import app.sawatt.ui.components.RoundButton
import app.sawatt.ui.components.SawattIcons
import app.sawatt.ui.components.SawattTopBar
import app.sawatt.ui.components.SectionCard
import app.sawatt.ui.components.StatTile
import app.sawatt.ui.components.cadenceColor
import app.sawatt.ui.components.planMeta
import app.sawatt.ui.components.targetColor
import app.sawatt.ui.components.zoneColor
import app.sawatt.ui.formatDistance
import app.sawatt.ui.formatDuration
import app.sawatt.ui.theme.SawattColors
import app.sawatt.ui.theme.SawattFonts
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
    val bike by container.trainer.bike.collectAsStateWithLifecycle()
    val hr by container.trainer.heartRateSensor.collectAsStateWithLifecycle()
    val plans by container.plans.plans.collectAsStateWithLifecycle()
    val plan: WorkoutPlan? = state.plan ?: planId?.let { id -> plans.firstOrNull { it.id == id } }
    val live = state.live
    val ftpW = live?.ftpW ?: RideMath.effectiveFtp(profile)
    var confirmDiscard by remember { mutableStateOf(false) }

    KeepScreenOn(app.keepScreenOn && state.status != WorkoutStatus.IDLE)

    val bikeOk = bike?.state == LinkState.CONNECTED
    val hrOk = hr?.state == LinkState.CONNECTED
    val subtitle = when {
        live == null -> if (plan != null) planMeta(plan) else stringResource(R.string.workout_press_lap_to_split)
        live.current.kind == IntervalKind.PLANNED -> stringResource(
            R.string.workout_interval_of_elapsed, live.current.index, live.plannedCount, formatDuration(live.elapsedSec),
        )
        else -> stringResource(R.string.workout_interval_elapsed, live.current.index, formatDuration(live.elapsedSec))
    }
    Scaffold(
        containerColor = SawattColors.Background,
        topBar = {
            SawattTopBar(plan?.name ?: stringResource(R.string.workout_free_ride), onBack, subtitle) {
                IconButton(onClick = onDevices) {
                    Icon(
                        SawattIcons.DirectionsBike,
                        stringResource(if (bikeOk) R.string.workout_bike_connected else R.string.workout_bike_not_connected),
                        tint = if (bikeOk) SawattColors.Accent else SawattColors.TextSecondary,
                    )
                }
                IconButton(onClick = onDevices) {
                    Icon(
                        Icons.Default.Favorite,
                        stringResource(if (hrOk) R.string.workout_hr_connected else R.string.workout_hr_not_connected),
                        tint = if (hrOk) SawattColors.Heart else SawattColors.TextSecondary,
                    )
                }
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
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (live == null) {
                Idle(plan, ftpW, readings, bikeOk, onDevices)
            } else {
                Live(live, plan, readings, state.status, profile.units)
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.workout_discard_title)) },
            text = { Text(stringResource(R.string.workout_discard_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    controller.discard()
                    onFinished(false)
                }) { Text(stringResource(R.string.workout_discard)) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.workout_cancel)) } },
        )
    }
}

@Composable
private fun Idle(plan: WorkoutPlan?, ftpW: Double, readings: SensorReadings, bikeOk: Boolean, onDevices: () -> Unit) {
    if (!bikeOk) {
        SectionCard(Modifier.fillMaxWidth().padding(top = 8.dp), onClick = onDevices) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(SawattIcons.Bluetooth, SawattColors.Below)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.workout_no_bike), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.workout_no_bike_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = SawattColors.TextSecondary,
                    )
                }
            }
        }
    }
    MetricGrid(
        listOf(
            { StatTile(stringResource(R.string.workout_power), readings.powerW.toString(), unit = "W", labelColor = SawattColors.Power, valueSize = 40.sp) },
            { StatTile(stringResource(R.string.workout_cadence), readings.cadenceRpm.toString(), unit = stringResource(R.string.workout_unit_rpm), labelColor = SawattColors.Cadence, valueSize = 40.sp) },
            {
                StatTile(
                    stringResource(R.string.workout_heart_rate), if (readings.heartRate > 0) readings.heartRate.toString() else "–", unit = "bpm",
                    labelColor = SawattColors.Heart, valueSize = 40.sp,
                )
            },
            { StatTile(stringResource(R.string.workout_ftp), ftpW.roundToInt().toString(), unit = "W", valueSize = 40.sp) },
        ),
    )
    if (plan != null) {
        SectionCard(Modifier.fillMaxWidth(), spacing = 10.dp) {
            PlanProfile(plan.flatten(), ftpW, height = 56.dp)
            plan.flatten().forEach { p ->
                val pct = p.block.targetFtpPercent?.toDouble() ?: if (ftpW > 0) p.block.targetWatts(ftpW) * 100 / ftpW else 0.0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(zoneColor(pct)))
                    Spacer(Modifier.width(10.dp))
                    Text(p.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    val rpm = stringResource(R.string.workout_unit_rpm)
                    val cadence = p.block.targetCadenceRpm?.let { " · $it $rpm" } ?: ""
                    Text(
                        stringResource(R.string.workout_block_line, formatDuration(p.block.durationSec), p.block.targetLabel(ftpW)) + cadence,
                        style = MaterialTheme.typography.bodySmall,
                        color = SawattColors.TextSecondary,
                    )
                }
            }
        }
    } else {
        Text(
            stringResource(R.string.workout_free_ride_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = SawattColors.TextSecondary,
        )
    }
}

@Composable
private fun Live(live: LiveState, plan: WorkoutPlan?, readings: SensorReadings, status: WorkoutStatus, units: UnitSystem) {
    val c = live.current
    IntervalHeader(c, live)
    IntervalRing(c, status)

    val remaining = c.remainingSec
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
        Metric(
            formatDuration(remaining ?: c.elapsedSec),
            size = 44.sp,
            color = if (remaining != null && remaining <= 5) SawattColors.Pause else SawattColors.Text,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            "  " + stringResource(if (remaining != null) R.string.workout_left else R.string.workout_elapsed),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = SawattColors.TextSecondary,
            modifier = Modifier.alignByBaseline(),
        )
    }

    MetricGrid(
        listOf(
            {
                StatTile(
                    stringResource(R.string.workout_power), readings.powerW.toString(), unit = "W", labelColor = SawattColors.Power, valueSize = 36.sp,
                    valueColor = targetColor(readings.powerW.toDouble(), c.targetPowerW) ?: SawattColors.Text,
                )
            },
            {
                StatTile(
                    stringResource(R.string.workout_cadence), readings.cadenceRpm.toString(), unit = stringResource(R.string.workout_unit_rpm), labelColor = SawattColors.Cadence, valueSize = 36.sp,
                    valueColor = cadenceColor(readings.cadenceRpm, c.targetCadenceRpm) ?: SawattColors.Text,
                )
            },
            {
                StatTile(
                    stringResource(R.string.workout_heart_rate), if (readings.heartRate > 0) readings.heartRate.toString() else "–", unit = "bpm",
                    labelColor = SawattColors.Heart, valueSize = 36.sp,
                )
            },
            { StatTile(stringResource(R.string.workout_max_interval), c.stats.maxPowerW.toString(), unit = "W", valueSize = 36.sp) },
        ),
    )

    if (plan != null && live.plannedCount > 0) {
        val planned = plan.flatten()
        val progress = if (c.kind == IntervalKind.PLANNED) {
            planned.take(c.index - 1).sumOf { it.block.durationSec } + minOf(c.elapsedSec, c.plannedDurationSec ?: c.elapsedSec)
        } else {
            planned.sumOf { it.block.durationSec }
        }
        PlanProfile(planned, live.ftpW, progressSec = progress)
    }
    live.next?.let { n ->
        Text(
            stringResource(R.string.workout_next, n.label, formatDuration(n.block.durationSec), n.block.targetLabel(live.ftpW)),
            style = MaterialTheme.typography.bodyMedium,
            color = SawattColors.TextSecondary,
        )
    }
    if (live.planFinished && live.plannedCount > 0) {
        Text(stringResource(R.string.workout_plan_completed), style = MaterialTheme.typography.bodyMedium, color = SawattColors.OnTarget)
    }

    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricLabel(stringResource(R.string.workout_whole_workout))
        val s = live.session
        Row {
            StatTile(stringResource(R.string.workout_avg_power), s.avgPowerW.roundToInt().toString(), Modifier.weight(1f), unit = "W", valueSize = 22.sp)
            StatTile(stringResource(R.string.workout_distance), formatDistance(s.distanceKm, units), Modifier.weight(1f), valueSize = 22.sp)
            StatTile(stringResource(R.string.workout_energy), s.kcal.roundToInt().toString(), Modifier.weight(1f), unit = "kcal", valueSize = 22.sp)
        }
    }

    if (live.completed.isNotEmpty()) {
        SectionCard(Modifier.fillMaxWidth()) {
            MetricLabel(stringResource(R.string.workout_completed_intervals))
            IntervalTable(live.completed.reversed(), live.ftpW)
        }
    }
}

@Composable
private fun IntervalHeader(c: CurrentInterval, live: LiveState) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
        val target = c.targetPowerW
        val zone = c.targetFtpPercent?.toDouble() ?: if (target != null && live.ftpW > 0) target * 100 / live.ftpW else null
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            // The zone colour, which the removed "Interval 1 of 12" line used to carry.
            Box(Modifier.size(10.dp).clip(CircleShape).background(zone?.let(::zoneColor) ?: SawattColors.Accent))
            Spacer(Modifier.width(10.dp))
            Text(intervalName(c.name, c.kind), style = MaterialTheme.typography.titleLarge, fontSize = 26.sp)
        }
        if (target != null) {
            Column(horizontalAlignment = Alignment.End) {
                MetricLabel(stringResource(R.string.workout_target))
                Metric(
                    target.toString(),
                    unit = "W" + (c.targetFtpPercent?.let { " · $it%" } ?: "") +
                        (c.targetCadenceRpm?.let { " · $it ${stringResource(R.string.workout_unit_rpm)}" } ?: ""),
                    size = 24.sp,
                )
            }
        }
    }
}

/** The interval average power, inside a ring that fills up as the interval goes by. */
@Composable
private fun IntervalRing(c: CurrentInterval, status: WorkoutStatus) {
    val avg = c.stats.avgPowerW
    val color = targetColor(avg, c.targetPowerW) ?: SawattColors.Text
    val progress = c.plannedDurationSec?.let { (c.elapsedSec.toFloat() / it).coerceIn(0f, 1f) }
    val verdict = when {
        c.targetPowerW == null -> "W"
        color == SawattColors.Below -> stringResource(R.string.workout_below_target)
        color == SawattColors.Above -> stringResource(R.string.workout_above_target)
        else -> stringResource(R.string.workout_on_target)
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(236.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 14.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(SawattColors.Track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                if (progress != null && progress > 0f) {
                    drawArc(
                        if (c.targetPowerW == null) SawattColors.Accent else color,
                        -90f, 360f * progress, false, Offset(inset, inset), arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MetricLabel(stringResource(R.string.workout_interval_avg))
                Text(
                    avg.roundToInt().toString(),
                    fontFamily = SawattFonts.Numbers,
                    fontWeight = FontWeight.Bold,
                    fontSize = 96.sp,
                    lineHeight = 92.sp,
                    color = color,
                )
                if (status == WorkoutStatus.PAUSED) {
                    Text(stringResource(R.string.workout_paused), style = MaterialTheme.typography.labelMedium, color = SawattColors.Pause)
                } else {
                    Text(verdict, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = SawattColors.TextSecondary)
                }
            }
        }
    }
}

/** Two-column grid of metrics separated by hairlines. */
@Composable
private fun MetricGrid(cells: List<@Composable () -> Unit>) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = SawattColors.Hairline)
        cells.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(Modifier.weight(1f).padding(vertical = 10.dp, horizontal = 4.dp)) { row[0]() }
                VerticalDivider(Modifier.fillMaxHeight(), thickness = 1.dp, color = SawattColors.Hairline)
                Box(Modifier.weight(1f).padding(vertical = 10.dp).padding(start = 16.dp)) { row.getOrNull(1)?.invoke() }
            }
            HorizontalDivider(thickness = 1.dp, color = SawattColors.Hairline)
        }
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
        Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally),
    ) {
        when (status) {
            WorkoutStatus.IDLE -> RoundButton(Icons.Default.PlayArrow, stringResource(R.string.workout_start), onStart, size = 80.dp)
            WorkoutStatus.RUNNING -> {
                RoundButton(SawattIcons.Pause, stringResource(R.string.workout_pause), onPause, container = SawattColors.Pause.copy(alpha = 0.18f), content = SawattColors.Pause)
                RoundButton(SawattIcons.Flag, stringResource(R.string.workout_lap), onLap)
            }
            WorkoutStatus.PAUSED -> {
                RoundButton(Icons.Default.Delete, stringResource(R.string.workout_discard), onDiscard, container = SawattColors.Above.copy(alpha = 0.16f), content = SawattColors.Above)
                RoundButton(SawattIcons.Stop, stringResource(R.string.workout_finish), onFinish, container = SawattColors.SurfaceHigh, content = SawattColors.Text)
                RoundButton(Icons.Default.PlayArrow, stringResource(R.string.workout_resume), onResume)
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
