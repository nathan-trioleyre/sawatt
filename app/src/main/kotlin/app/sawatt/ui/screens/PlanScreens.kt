package app.sawatt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.sawatt.AppContainer
import app.sawatt.core.ride.RideMath
import app.sawatt.core.workout.Block
import app.sawatt.core.workout.PlanStep
import app.sawatt.core.workout.RepeatSet
import app.sawatt.core.workout.WorkoutPlan
import app.sawatt.ui.Routes
import app.sawatt.ui.components.LargeTitle
import app.sawatt.ui.components.MetricLabel
import app.sawatt.ui.components.PlanCard
import app.sawatt.ui.components.PlanProfile
import app.sawatt.ui.components.PlayButton
import app.sawatt.ui.components.SawattIcons
import app.sawatt.ui.components.SawattTopBar
import app.sawatt.ui.components.SectionCard
import app.sawatt.ui.components.planMeta
import app.sawatt.ui.formatDuration
import app.sawatt.ui.parseDuration
import app.sawatt.ui.theme.SawattColors
import app.sawatt.workout.WorkoutStatus
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

@Composable
fun PlansScreen(container: AppContainer, nav: NavHostController) {
    val plans by container.plans.plans.collectAsStateWithLifecycle()
    val workout by container.workout.state.collectAsStateWithLifecycle()
    val profile by container.settings.profile.collectAsStateWithLifecycle()
    val ftpW = RideMath.effectiveFtp(profile)
    val idle = workout.status == WorkoutStatus.IDLE

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LargeTitle("Workouts", modifier = Modifier.padding(bottom = 6.dp)) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(SawattColors.SurfaceHigh)
                        .clickable(role = Role.Button) { nav.navigate(Routes.planEditor(null)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New workout", tint = SawattColors.Accent)
                }
            }
        }
        if (plans.isEmpty()) {
            item {
                Text(
                    "No workout yet. Tap + to build one from blocks of time and power.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SawattColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        items(plans, key = { it.id }) { plan ->
            PlanCard(
                plan, ftpW,
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                onClick = { nav.navigate(Routes.planEditor(plan.id)) },
                profileHeight = 48.dp,
            ) {
                PlayButton("Ride ${plan.name}", onClick = { nav.navigate(Routes.workout(plan.id)) }, enabled = idle)
            }
        }
    }
}

// ---- Editor ----------------------------------------------------------------------------

private val ids = AtomicLong()

/** Mutable editor model; text fields keep the raw input so partial values are possible. */
private class EditBlock(name: String, duration: String, power: String, cadence: String, percent: Boolean = false) {
    val id = ids.incrementAndGet()
    var name by mutableStateOf(name)
    var duration by mutableStateOf(duration)
    var power by mutableStateOf(power)
    var cadence by mutableStateOf(cadence)

    /** True when [power] is a percentage of FTP rather than watts. */
    var percent by mutableStateOf(percent)

    val powerValue: Int? get() = power.trim().toIntOrNull()?.takeIf { it in if (percent) PERCENT_RANGE else WATTS_RANGE }

    fun toBlock(): Block? {
        val d = parseDuration(duration) ?: return null
        val p = powerValue ?: return null
        val c = cadence.trim().takeIf { it.isNotEmpty() }?.let { it.toIntOrNull()?.takeIf { v -> v in 1..200 } ?: return null }
        val n = name.trim().ifEmpty { "Interval" }
        return if (percent) Block(n, d, targetCadenceRpm = c, targetFtpPercent = p) else Block(n, d, p, c)
    }

    companion object {
        val WATTS_RANGE = 0..2000
        val PERCENT_RANGE = 1..300

        fun from(b: Block) = EditBlock(
            b.name,
            formatDuration(b.durationSec),
            (b.targetFtpPercent ?: b.targetPowerW).toString(),
            b.targetCadenceRpm?.toString() ?: "",
            percent = b.targetFtpPercent != null,
        )

        fun blank(name: String = "Interval") = EditBlock(name, "5:00", "150", "")
    }
}

private sealed class EditStep {
    val id = ids.incrementAndGet()

    class Single(val block: EditBlock) : EditStep()
    class Repeat(times: String, val blocks: SnapshotStateList<EditBlock>) : EditStep() {
        var times by mutableStateOf(times)
    }
}

private fun PlanStep.toEdit(): EditStep = when (this) {
    is Block -> EditStep.Single(EditBlock.from(this))
    is RepeatSet -> EditStep.Repeat(times.toString(), mutableStateListOf<EditBlock>().apply { addAll(blocks.map(EditBlock::from)) })
}

@Composable
fun PlanEditorScreen(container: AppContainer, planId: String?, onDone: () -> Unit) {
    val profile by container.settings.profile.collectAsStateWithLifecycle()
    val ftpW = RideMath.effectiveFtp(profile)
    val original = remember(planId) { planId?.let(container.plans::get) }
    var name by remember { mutableStateOf(original?.name ?: "New workout") }
    val steps = remember {
        mutableStateListOf<EditStep>().apply {
            if (original != null) addAll(original.steps.map { it.toEdit() })
            else add(EditStep.Single(EditBlock.blank("Warm-up")))
        }
    }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun build(): WorkoutPlan? {
        val built = steps.map { s ->
            when (s) {
                is EditStep.Single -> s.block.toBlock() ?: return null
                is EditStep.Repeat -> {
                    val times = s.times.trim().toIntOrNull()?.takeIf { it in 1..50 } ?: return null
                    val blocks = s.blocks.map { it.toBlock() ?: return null }
                    if (blocks.isEmpty()) return null
                    RepeatSet(times, blocks)
                }
            }
        }
        if (built.isEmpty()) return null
        return WorkoutPlan(id = original?.id ?: java.util.UUID.randomUUID().toString(), name = name.trim().ifEmpty { "Workout" }, steps = built)
    }

    val preview = build()
    Scaffold(
        containerColor = SawattColors.Background,
        topBar = {
            SawattTopBar(if (original == null) "New workout" else "Edit workout", onBack = onDone) {
                if (original != null) {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete workout") }
                }
                TextButton(onClick = {
                    val plan = build()
                    if (plan == null) {
                        error = "Check the highlighted values: duration like 15 or 15:00, power in W, optional cadence."
                    } else {
                        container.plans.upsert(plan)
                        onDone()
                    }
                }) { Text("Save", fontWeight = FontWeight.Bold) }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Workout name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            SectionCard(Modifier.fillMaxWidth(), spacing = 10.dp) {
                Text(
                    preview?.let(::planMeta) ?: "Incomplete workout",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (preview == null) SawattColors.TextSecondary else SawattColors.Text,
                )
                preview?.let { PlanProfile(it.flatten(), ftpW, height = 56.dp) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            steps.forEachIndexed { index, step ->
                key(step.id) {
                    StepEditor(
                        step = step,
                        ftpW = ftpW,
                        onMoveUp = { if (index > 0) steps.add(index - 1, steps.removeAt(index)) },
                        onMoveDown = { if (index < steps.lastIndex) steps.add(index + 1, steps.removeAt(index)) },
                        onRemove = { steps.removeAt(index) },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { steps.add(EditStep.Single(EditBlock.blank())) }) {
                    Icon(Icons.Default.Add, null)
                    Text("Block")
                }
                OutlinedButton(onClick = {
                    steps.add(
                        EditStep.Repeat(
                            "3",
                            mutableStateListOf(EditBlock("Effort", "15:00", "250", ""), EditBlock("Recovery", "5:00", "120", "")),
                        ),
                    )
                }) {
                    Icon(SawattIcons.Repeat, null)
                    Text("Repeat set")
                }
            }
            Text(
                "Durations: \"15\" = 15 min, \"15:30\" = 15 min 30 s. Cadence is optional.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (confirmDelete && original != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${original.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    container.plans.delete(original.id)
                    onDone()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StepEditor(step: EditStep, ftpW: Double, onMoveUp: () -> Unit, onMoveDown: () -> Unit, onRemove: () -> Unit) {
    SectionCard(Modifier.fillMaxWidth(), padding = 12.dp, spacing = 6.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (step) {
                    is EditStep.Single -> MetricLabel("Block", SawattColors.Accent, Modifier.weight(1f))
                    is EditStep.Repeat -> {
                        MetricLabel("Repeat", SawattColors.Accent)
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            step.times, { step.times = it },
                            modifier = Modifier.width(80.dp),
                            singleLine = true,
                            suffix = { Text("×") },
                            isError = step.times.trim().toIntOrNull()?.let { it in 1..50 } != true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }
                IconButton(onClick = onMoveUp) { Icon(SawattIcons.ArrowUpward, "Move up") }
                IconButton(onClick = onMoveDown) { Icon(SawattIcons.ArrowDownward, "Move down") }
                IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "Remove") }
            }
            when (step) {
                is EditStep.Single -> BlockFields(step.block, ftpW)
                is EditStep.Repeat -> {
                    step.blocks.forEachIndexed { i, b ->
                        key(b.id) {
                            Row(verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) { BlockFields(b, ftpW) }
                                IconButton(onClick = { step.blocks.removeAt(i) }) { Icon(Icons.Default.Close, "Remove block") }
                            }
                        }
                    }
                    TextButton(onClick = { step.blocks.add(EditBlock.blank()) }) {
                        Icon(Icons.Default.Add, null)
                        Text("Block in set")
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockFields(b: EditBlock, ftpW: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(b.name, { b.name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                b.duration, { b.duration = it },
                label = { Text("Duration") },
                singleLine = true,
                isError = parseDuration(b.duration) == null,
                modifier = Modifier.weight(1.1f),
            )
            OutlinedTextField(
                b.power, { b.power = it },
                label = { Text(if (b.percent) "% FTP" else "Power W") },
                singleLine = true,
                isError = b.powerValue == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                b.cadence, { b.cadence = it },
                label = { Text("rpm") },
                placeholder = { Text("opt.") },
                singleLine = true,
                isError = b.cadence.isNotBlank() && b.cadence.trim().toIntOrNull()?.let { it in 1..200 } != true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(0.9f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = b.percent,
                onClick = { b.percent = !b.percent },
                label = { Text("% of FTP") },
            )
            val pct = b.powerValue
            if (b.percent && pct != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "≈ ${(ftpW * pct / 100).roundToInt()} W with your FTP of ${ftpW.roundToInt()} W",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
