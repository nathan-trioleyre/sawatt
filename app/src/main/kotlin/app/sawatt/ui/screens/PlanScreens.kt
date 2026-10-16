package app.sawatt.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.sawatt.AppContainer
import app.sawatt.core.workout.Block
import app.sawatt.core.workout.PlanStep
import app.sawatt.core.workout.RepeatSet
import app.sawatt.core.workout.WorkoutPlan
import app.sawatt.ui.Routes
import app.sawatt.ui.components.SawattIcons
import app.sawatt.ui.components.SawattTopBar
import app.sawatt.ui.components.SectionCard
import app.sawatt.ui.formatDuration
import app.sawatt.ui.parseDuration
import app.sawatt.workout.WorkoutStatus
import java.util.concurrent.atomic.AtomicLong

@Composable
fun PlansScreen(container: AppContainer, nav: NavController) {
    val plans by container.plans.plans.collectAsStateWithLifecycle()
    val workout by container.workout.state.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<WorkoutPlan?>(null) }

    Scaffold(
        topBar = { SawattTopBar("Structured workouts", onBack = { nav.popBackStack() }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.planEditor(null)) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New plan") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(plans, key = { it.id }) { plan ->
                SectionCard(Modifier.fillMaxWidth()) {
                    Text(plan.name, style = MaterialTheme.typography.titleMedium)
                    Text(describe(plan), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { nav.navigate(Routes.workout(plan.id)) },
                            enabled = workout.status == WorkoutStatus.IDLE,
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Text("Ride")
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { nav.navigate(Routes.planEditor(plan.id)) }) { Icon(Icons.Default.Edit, "Edit") }
                        IconButton(onClick = { toDelete = plan }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                }
            }
        }
    }
    toDelete?.let { plan ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete \"${plan.name}\"?") },
            confirmButton = { TextButton(onClick = { container.plans.delete(plan.id); toDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

private fun describe(plan: WorkoutPlan): String {
    fun b(x: Block) = "${formatDuration(x.durationSec)} @ ${x.targetPowerW} W"
    val steps = plan.steps.joinToString(" → ") { s ->
        when (s) {
            is Block -> b(s)
            is RepeatSet -> "${s.times} × [" + s.blocks.joinToString(" + ") { b(it) } + "]"
        }
    }
    return "${formatDuration(plan.totalDurationSec)} · $steps"
}

// ---- Editor ----------------------------------------------------------------------------

private val ids = AtomicLong()

/** Mutable editor model; text fields keep the raw input so partial values are possible. */
private class EditBlock(name: String, duration: String, power: String, cadence: String) {
    val id = ids.incrementAndGet()
    var name by mutableStateOf(name)
    var duration by mutableStateOf(duration)
    var power by mutableStateOf(power)
    var cadence by mutableStateOf(cadence)

    fun toBlock(): Block? {
        val d = parseDuration(duration) ?: return null
        val p = power.trim().toIntOrNull()?.takeIf { it in 0..2000 } ?: return null
        val c = cadence.trim().takeIf { it.isNotEmpty() }?.let { it.toIntOrNull()?.takeIf { v -> v in 1..200 } ?: return null }
        return Block(name.trim().ifEmpty { "Interval" }, d, p, c)
    }

    companion object {
        fun from(b: Block) = EditBlock(b.name, formatDuration(b.durationSec), b.targetPowerW.toString(), b.targetCadenceRpm?.toString() ?: "")
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
    val original = remember(planId) { planId?.let(container.plans::get) }
    var name by remember { mutableStateOf(original?.name ?: "New workout") }
    val steps = remember {
        mutableStateListOf<EditStep>().apply {
            if (original != null) addAll(original.steps.map { it.toEdit() })
            else add(EditStep.Single(EditBlock.blank("Warm-up")))
        }
    }
    var error by remember { mutableStateOf<String?>(null) }

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
        topBar = {
            SawattTopBar(if (original == null) "New plan" else "Edit plan", onBack = onDone) {
                TextButton(onClick = {
                    val plan = build()
                    if (plan == null) {
                        error = "Check the highlighted values: duration like 15 or 15:00, power in W, optional cadence."
                    } else {
                        container.plans.upsert(plan)
                        onDone()
                    }
                }) { Text("Save") }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Plan name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(
                preview?.let { "Total ${formatDuration(it.totalDurationSec)} · ${it.flatten().size} intervals" } ?: "Incomplete plan",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            steps.forEachIndexed { index, step ->
                key(step.id) {
                    StepEditor(
                        step = step,
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
}

@Composable
private fun StepEditor(step: EditStep, onMoveUp: () -> Unit, onMoveDown: () -> Unit, onRemove: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (step) {
                    is EditStep.Single -> Text("BLOCK", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    is EditStep.Repeat -> {
                        Text("REPEAT", style = MaterialTheme.typography.labelMedium)
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
                is EditStep.Single -> BlockFields(step.block)
                is EditStep.Repeat -> {
                    step.blocks.forEachIndexed { i, b ->
                        key(b.id) {
                            Row(verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) { BlockFields(b) }
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
private fun BlockFields(b: EditBlock) {
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
                label = { Text("Power W") },
                singleLine = true,
                isError = b.power.trim().toIntOrNull()?.let { it in 0..2000 } != true,
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
    }
}
