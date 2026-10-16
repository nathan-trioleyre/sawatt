package app.sawatt.core.workout

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.roundToInt

/** One step of a structured workout. */
@Serializable
sealed interface PlanStep

/**
 * A single interval held for [durationSec], optionally at [targetCadenceRpm].
 *
 * The power target is either absolute ([targetPowerW]) or relative to the rider's FTP
 * ([targetFtpPercent]); exactly one of them is set. A percentage is converted to watts with the
 * rider's FTP when the workout starts, so a plan keeps working as the FTP improves.
 */
@Serializable
@SerialName("block")
data class Block(
    val name: String,
    val durationSec: Int,
    val targetPowerW: Int? = null,
    val targetCadenceRpm: Int? = null,
    val targetFtpPercent: Int? = null,
) : PlanStep {
    init {
        require((targetPowerW == null) != (targetFtpPercent == null)) {
            "A block needs exactly one power target: watts or % of FTP"
        }
    }

    /** Target power in watts for a rider with the given FTP. */
    fun targetWatts(ftpW: Double): Int = targetPowerW ?: (ftpW * targetFtpPercent!! / 100).roundToInt()

    /** "250 W", or "75% FTP" ("75% FTP (188 W)" when [ftpW] is known). */
    fun targetLabel(ftpW: Double? = null): String = when {
        targetPowerW != null -> "$targetPowerW W"
        ftpW == null -> "$targetFtpPercent% FTP"
        else -> "$targetFtpPercent% FTP (${targetWatts(ftpW)} W)"
    }
}

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
