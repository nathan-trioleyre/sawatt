package app.sawatt.core.workout

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/** One step of a structured workout. */
@Serializable
sealed interface PlanStep

/** A single interval: hold [targetPowerW] (and optionally [targetCadenceRpm]) for [durationSec]. */
@Serializable
@SerialName("block")
data class Block(
    val name: String,
    val durationSec: Int,
    val targetPowerW: Int,
    val targetCadenceRpm: Int? = null,
) : PlanStep

/** [blocks] played in order, [times] times (e.g. 3 × [15' @ 250 W + 5' recovery]). */
@Serializable
@SerialName("repeat")
data class RepeatSet(
    val times: Int,
    val blocks: List<Block>,
) : PlanStep

@Serializable
data class WorkoutPlan(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val steps: List<PlanStep>,
) {
    /** The plan unrolled into the concrete sequence of intervals that will be ridden. */
    fun flatten(): List<PlannedInterval> {
        val out = mutableListOf<PlannedInterval>()
        for (step in steps) {
            when (step) {
                is Block -> out += PlannedInterval(step, repetition = null)
                is RepeatSet -> for (rep in 1..step.times) {
                    for (block in step.blocks) out += PlannedInterval(block, Repetition(rep, step.times))
                }
            }
        }
        return out
    }

    val totalDurationSec: Int get() = flatten().sumOf { it.block.durationSec }

    companion object {
        /** The example from the requirements: 10' warm-up, 3 × [15' @ 250 W + 5' recovery], 10' cool-down. */
        fun example() = WorkoutPlan(
            name = "3 × 15' @ 250 W",
            steps = listOf(
                Block("Warm-up", 10 * 60, 150, null),
                RepeatSet(
                    times = 3,
                    blocks = listOf(
                        Block("Effort", 15 * 60, 250, 90),
                        Block("Recovery", 5 * 60, 120, null),
                    ),
                ),
                Block("Cool-down", 10 * 60, 120, null),
            ),
        )
    }
}

@Serializable
data class Repetition(val index: Int, val of: Int)

data class PlannedInterval(val block: Block, val repetition: Repetition?) {
    val label: String
        get() = if (repetition == null) block.name else "${block.name} ${repetition.index}/${repetition.of}"
}
