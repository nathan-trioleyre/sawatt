package app.sawatt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.sawatt.AppContainer
import app.sawatt.R
import app.sawatt.core.history.HistoryStats
import app.sawatt.core.history.PeriodKind
import app.sawatt.core.model.UnitSystem
import app.sawatt.core.ride.RideMath
import app.sawatt.core.workout.WorkoutRecord
import app.sawatt.core.workout.WorkoutSamples
import app.sawatt.ui.Routes
import app.sawatt.ui.buckets
import app.sawatt.ui.components.ExportButton
import app.sawatt.ui.components.Group
import app.sawatt.ui.components.GroupDivider
import app.sawatt.ui.components.GroupRow
import app.sawatt.ui.components.IntervalTable
import app.sawatt.ui.components.LargeTitle
import app.sawatt.ui.components.Metric
import app.sawatt.ui.components.MetricLabel
import app.sawatt.ui.components.PeriodBars
import app.sawatt.ui.components.PowerChart
import app.sawatt.ui.components.SawattTopBar
import app.sawatt.ui.components.SectionCard
import app.sawatt.ui.components.SectionHeader
import app.sawatt.ui.components.StatTile
import app.sawatt.ui.components.ZoneBar
import app.sawatt.ui.formatDistance
import app.sawatt.ui.formatDuration
import app.sawatt.ui.formatOrDash
import app.sawatt.ui.periodTitle
import app.sawatt.ui.recordsIn
import app.sawatt.ui.theme.SawattColors
import app.sawatt.ui.theme.SawattFonts
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Shown right after Finish: the workout that was just saved. */
@Composable
fun SummaryScreen(container: AppContainer, onDone: () -> Unit) {
    val record by container.workout.lastFinished.collectAsStateWithLifecycle()
    val profile by container.settings.profile.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val r = record

    Scaffold(
        containerColor = SawattColors.Background,
        topBar = {
            SawattTopBar("") {
                r?.let { ExportButton(container.exporter, it) }
                TextButton(onClick = onDone) { Text(stringResource(R.string.history_done), fontWeight = FontWeight.Bold) }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            if (r == null) {
                Text(stringResource(R.string.history_nothing_to_show), Modifier.padding(20.dp))
            } else {
                Text(
                    stringResource(R.string.history_saved),
                    style = MaterialTheme.typography.bodySmall,
                    color = SawattColors.Accent,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                WorkoutDetails(container, r, profile.units, RideMath.effectiveFtp(profile))
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp).navigationBarsPadding(),
                ) { Text(stringResource(R.string.history_delete_this_workout), color = SawattColors.Above) }
            }
        }
    }
    if (confirmDelete && r != null) {
        DeleteDialog(onConfirm = { container.history.delete(r.id); onDone() }, onDismiss = { confirmDelete = false })
    }
}

private val kinds = listOf(PeriodKind.WEEK to R.string.history_week, PeriodKind.MONTH to R.string.history_month, PeriodKind.YEAR to R.string.history_year)

@Composable
fun HistoryScreen(container: AppContainer, nav: NavHostController) {
    val records by container.history.records.collectAsStateWithLifecycle()
    val profile by container.settings.profile.collectAsStateWithLifecycle()
    var kindIndex by rememberSaveable { mutableIntStateOf(0) }
    val kind = kinds[kindIndex].first
    // Periods with at least one workout, newest first; index 0 is the most recent.
    val periods = remember(records, kind) { HistoryStats.byPeriod(records, kind) }
    var periodIndex by rememberSaveable(kindIndex) { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        LargeTitle(stringResource(R.string.history_title))
        Segmented(kinds.map { stringResource(it.second) }, kindIndex, Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)) { kindIndex = it }

        val p = periods.getOrNull(periodIndex.coerceAtMost(periods.lastIndex))
        if (p == null) {
            Text(
                stringResource(R.string.history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = SawattColors.TextSecondary,
                modifier = Modifier.padding(20.dp),
            )
            return@Column
        }
        val index = periods.indexOf(p)

        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { periodIndex = index + 1 }, enabled = index < periods.lastIndex) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.history_previous_period))
            }
            Text(
                periodTitle(p.key),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { periodIndex = index - 1 }, enabled = index > 0) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.history_next_period))
            }
        }

        Row(Modifier.padding(horizontal = 20.dp)) {
            StatTile(stringResource(R.string.history_time), formatDuration(p.durationSec), Modifier.weight(1f), valueSize = 26.sp)
            StatTile(stringResource(R.string.history_distance), formatDistance(p.distanceKm, profile.units), Modifier.weight(1f), valueSize = 26.sp)
            StatTile(stringResource(R.string.history_avg_w), p.avgPowerW.roundToInt().toString(), Modifier.weight(1f), valueSize = 26.sp)
            StatTile(stringResource(R.string.history_kcal), p.kcal.roundToInt().toString(), Modifier.weight(1f), valueSize = 26.sp)
        }

        val bars = buckets(records, p.key)
        PeriodBars(
            bars,
            Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp),
            height = 96.dp,
            showMinutes = kind == PeriodKind.WEEK,
            labelEvery = if (kind == PeriodKind.MONTH) 7 else 1,
        )

        SectionHeader(pluralStringResource(R.plurals.history_workout_count, p.workouts, p.workouts))
        Group {
            recordsIn(records, p.key).forEachIndexed { i, r ->
                if (i > 0) GroupDivider()
                WorkoutRow(r, profile.units) { nav.navigate(Routes.historyDetail(r.id)) }
            }
        }
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SawattColors.SurfaceHigh).padding(3.dp).selectableGroup(),
    ) {
        options.forEachIndexed { i, label ->
            Box(
                Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (i == selected) SawattColors.Muted else SawattColors.SurfaceHigh)
                    .selectable(selected = i == selected, role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontSize = 14.sp,
                    color = if (i == selected) SawattColors.Text else SawattColors.TextSecondary,
                )
            }
        }
    }
}

private val zone = ZoneId.systemDefault()

@Composable
private fun WorkoutRow(r: WorkoutRecord, units: UnitSystem, onClick: () -> Unit) {
    val date = Instant.ofEpochMilli(r.startEpochMs).atZone(zone)
    val locale = Locale.getDefault()
    GroupRow(
        r.planName ?: stringResource(R.string.history_free_ride),
        subtitle = "${formatDuration(r.summary.durationSec)} · ${formatDistance(r.summary.distanceKm, units)}",
        onClick = onClick,
        leading = {
            Column(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(SawattColors.SurfaceHigh),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    date.format(DateTimeFormatter.ofPattern("EEE", locale)).uppercase(locale),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = SawattColors.TextSecondary,
                )
                Text(date.dayOfMonth.toString(), fontFamily = SawattFonts.Numbers, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 18.sp)
            }
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Metric(r.summary.avgPowerW.roundToInt().toString(), unit = "W", size = 20.sp)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = SawattColors.Chevron)
            }
        },
    )
}

@Composable
fun HistoryDetailScreen(container: AppContainer, id: String, onBack: () -> Unit) {
    val records by container.history.records.collectAsStateWithLifecycle()
    val profile by container.settings.profile.collectAsStateWithLifecycle()
    val record = records.firstOrNull { it.id == id }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = SawattColors.Background,
        topBar = {
            SawattTopBar("", onBack) {
                record?.let { ExportButton(container.exporter, it) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, stringResource(R.string.history_delete)) }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            if (record == null) {
                Text(stringResource(R.string.history_not_found), Modifier.padding(20.dp))
            } else {
                WorkoutDetails(container, record, profile.units, RideMath.effectiveFtp(profile))
            }
        }
    }
    if (confirmDelete && record != null) {
        DeleteDialog(onConfirm = { container.history.delete(record.id); onBack() }, onDismiss = { confirmDelete = false })
    }
}

@Composable
private fun WorkoutDetails(container: AppContainer, r: WorkoutRecord, units: UnitSystem, ftpW: Double) {
    var samples by remember(r.id) { mutableStateOf<WorkoutSamples?>(null) }
    LaunchedEffect(r.id) { samples = container.history.samples(r.id) }
    val s = r.summary
    val locale = Locale.getDefault()
    val date = Instant.ofEpochMilli(r.startEpochMs).atZone(zone)

    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column {
            MetricLabel(date.format(DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", locale)))
            Text(r.planName ?: stringResource(R.string.history_free_ride), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        val stats: List<@Composable (Modifier) -> Unit> = listOf(
            { m -> StatTile(stringResource(R.string.history_time), formatDuration(s.durationSec), m) },
            { m -> StatTile(stringResource(R.string.history_distance), formatDistance(s.distanceKm, units), m) },
            { m -> StatTile(stringResource(R.string.history_energy), s.kcal.roundToInt().toString(), m, unit = "kcal") },
            { m -> StatTile(stringResource(R.string.history_avg_power), s.avgPowerW.roundToInt().toString(), m, unit = "W") },
            { m -> StatTile(stringResource(R.string.history_max_power), s.maxPowerW.toString(), m, unit = "W") },
            { m -> StatTile(stringResource(R.string.history_avg_cadence), s.avgCadenceRpm.roundToInt().toString(), m, unit = "rpm") },
            { m -> StatTile(stringResource(R.string.history_avg_hr), formatOrDash(s.avgHeartRate), m, unit = "bpm") },
            { m -> StatTile(stringResource(R.string.history_max_hr), s.maxHeartRate?.toString() ?: "–", m, unit = "bpm") },
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            stats.chunked(3).forEach { row ->
                Row {
                    row.forEach { it(Modifier.weight(1f)) }
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }

        samples?.let { tr ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MetricLabel(stringResource(R.string.history_power_chart))
                PowerChart(tr.powerW, r.intervals)
            }
        }
        SectionCard(Modifier.fillMaxWidth()) {
            MetricLabel(stringResource(R.string.history_intervals))
            IntervalTable(r.intervals, ftpW)
        }
        ZoneBar(s.zoneSeconds)
    }
}

@Composable
private fun DeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_delete_title)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.history_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.history_cancel)) } },
    )
}
