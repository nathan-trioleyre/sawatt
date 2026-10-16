package app.sawatt.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.sawatt.AppContainer
import app.sawatt.core.history.HistoryStats
import app.sawatt.core.history.PeriodKind
import app.sawatt.core.history.PeriodStats
import app.sawatt.core.model.UnitSystem
import app.sawatt.core.workout.WorkoutRecord
import app.sawatt.core.workout.WorkoutSamples
import app.sawatt.ui.Routes
import app.sawatt.ui.components.SawattTopBar
import app.sawatt.ui.components.ExportButton
import app.sawatt.ui.components.IntervalTable
import app.sawatt.ui.components.PowerChart
import app.sawatt.ui.components.SectionCard
import app.sawatt.ui.components.StatTile
import app.sawatt.ui.formatDateTime
import app.sawatt.ui.formatDistance
import app.sawatt.ui.formatDuration
import app.sawatt.ui.formatOrDash
import app.sawatt.ui.theme.SawattColors
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Shown right after Finish: the per-interval table of the workout that was just saved. */
@Composable
fun SummaryScreen(container: AppContainer, onDone: () -> Unit) {
    val record by container.workout.lastFinished.collectAsStateWithLifecycle()
    val profile by container.settings.profile.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val r = record

    Scaffold(
        topBar = { SawattTopBar("Workout summary") { r?.let { ExportButton(container.exporter, it) } } },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { confirmDelete = true }, enabled = r != null) { Text("Delete") }
                Button(onClick = onDone, modifier = Modifier.weight(1f)) { Text("Done") }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp).verticalScroll(rememberScrollState())) {
            if (r == null) {
                Text("Nothing to show.")
            } else {
                Text("Saved to history.", color = SawattColors.OnTarget, style = MaterialTheme.typography.bodyMedium)
                WorkoutDetails(container, r, profile.units)
            }
        }
    }
    if (confirmDelete && r != null) {
        DeleteDialog(onConfirm = { container.history.delete(r.id); onDone() }, onDismiss = { confirmDelete = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(container: AppContainer, nav: NavController) {
    val records by container.history.records.collectAsStateWithLifecycle()
    val profile by container.settings.profile.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Workouts", "Weeks", "Months", "Years")

    Scaffold(topBar = { SawattTopBar("Workout history", onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
            if (records.isEmpty()) {
                Text("No workout history yet!", Modifier.padding(16.dp))
                return@Column
            }
            val totals = HistoryStats.totals(records)
            Text(
                "${totals.workouts} workouts · ${formatDuration(totals.durationSec)} · " +
                    "${formatDistance(totals.distanceKm, profile.units)} · ${totals.kcal.roundToInt()} kcal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(
                Modifier.padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (tab == 0) {
                    items(records, key = { it.id }) { r ->
                        WorkoutRow(r, profile.units) { nav.navigate(Routes.historyDetail(r.id)) }
                    }
                } else {
                    val kind = PeriodKind.entries[tab - 1]
                    items(HistoryStats.byPeriod(records, kind)) { p -> PeriodRow(p, profile.units) }
                }
            }
        }
    }
}

@Composable
private fun WorkoutRow(r: WorkoutRecord, units: UnitSystem, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        SectionCard(Modifier.fillMaxWidth()) {
            Text(formatDateTime(r.startEpochMs), style = MaterialTheme.typography.titleSmall)
            Text(
                (r.planName ?: "Free ride") + " · ${r.intervals.size} intervals",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${formatDuration(r.summary.durationSec)} · avg ${r.summary.avgPowerW.roundToInt()} W · " +
                    "${formatDistance(r.summary.distanceKm, units)} · ${r.summary.kcal.roundToInt()} kcal",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PeriodRow(p: PeriodStats, units: UnitSystem) {
    val title = when (p.key.kind) {
        PeriodKind.WEEK -> "Week ${p.key.number} · ${p.key.year}"
        PeriodKind.MONTH -> Month.of(p.key.number).getDisplayName(TextStyle.FULL, Locale.getDefault())
            .replaceFirstChar { it.titlecase() } + " ${p.key.year}"
        PeriodKind.YEAR -> p.key.year.toString()
    }
    SectionCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            "${p.workouts} workouts · ${formatDuration(p.durationSec)} · ${formatDistance(p.distanceKm, units)} · ${p.kcal.roundToInt()} kcal",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "avg ${p.avgPowerW.roundToInt()} W (max ${p.maxPowerW}) · avg ${p.avgCadenceRpm.roundToInt()} rpm · " +
                "HR ${formatOrDash(p.avgHeartRate)} (max ${p.maxHeartRate ?: "–"})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun HistoryDetailScreen(container: AppContainer, id: String, onBack: () -> Unit) {
    val records by container.history.records.collectAsStateWithLifecycle()
    val profile by container.settings.profile.collectAsStateWithLifecycle()
    val record = records.firstOrNull { it.id == id }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SawattTopBar(record?.let { formatDateTime(it.startEpochMs) } ?: "Workout", onBack) {
                record?.let { ExportButton(container.exporter, it) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete") }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp).verticalScroll(rememberScrollState())) {
            if (record == null) Text("Workout not found.") else WorkoutDetails(container, record, profile.units)
        }
    }
    if (confirmDelete && record != null) {
        DeleteDialog(onConfirm = { container.history.delete(record.id); onBack() }, onDismiss = { confirmDelete = false })
    }
}

@Composable
private fun WorkoutDetails(container: AppContainer, r: WorkoutRecord, units: UnitSystem) {
    var samples by remember(r.id) { mutableStateOf<WorkoutSamples?>(null) }
    LaunchedEffect(r.id) { samples = container.history.samples(r.id) }
    val s = r.summary

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(r.planName ?: "Free ride", style = MaterialTheme.typography.titleLarge)
        SectionCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatTile("Time", formatDuration(s.durationSec), valueSize = 22.sp)
                StatTile("Avg power", s.avgPowerW.roundToInt().toString(), unit = "W", valueSize = 22.sp)
                StatTile("Max power", s.maxPowerW.toString(), unit = "W", valueSize = 22.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatTile("Avg cad.", s.avgCadenceRpm.roundToInt().toString(), unit = "rpm", valueSize = 22.sp)
                StatTile("Avg HR", formatOrDash(s.avgHeartRate), unit = "bpm", valueSize = 22.sp)
                StatTile("Max HR", s.maxHeartRate?.toString() ?: "–", unit = "bpm", valueSize = 22.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatTile("Distance", formatDistance(s.distanceKm, units), valueSize = 22.sp)
                StatTile("Energy", s.kcal.roundToInt().toString(), unit = "kcal", valueSize = 22.sp)
            }
        }
        samples?.let { tr ->
            SectionCard {
                Text("POWER (5 s avg) · dashed: target", style = MaterialTheme.typography.labelMedium)
                PowerChart(tr.powerW, r.intervals)
            }
        }
        SectionCard {
            Text("INTERVALS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            IntervalTable(r.intervals)
        }
        ZoneBar(s.zoneSeconds)
    }
}

/** Time in each power zone (% of FTP: <56, 56-75, 76-90, 91-105, ≥106). */
@Composable
private fun ZoneBar(zoneSeconds: List<Int>) {
    val total = zoneSeconds.sum().takeIf { it > 0 } ?: return
    SectionCard {
        Text("INTENSITY (TIME IN POWER ZONE)", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth()) {
            zoneSeconds.forEachIndexed { i, sec ->
                Text(
                    "${(sec * 100.0 / total).roundToInt()}%",
                    color = SawattColors.Zones[i],
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun DeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete workout?") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
