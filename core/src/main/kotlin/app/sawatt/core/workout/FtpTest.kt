package app.sawatt.core.workout

import kotlin.math.roundToInt

/**
 * The guided 20-minute FTP test: a structured warm-up with openers, then 20 minutes all out. The
 * FTP is estimated as 95% of the average power held over those 20 minutes.
 */
object FtpTest {
    /** Id of the built-in plan; it is never stored with the user's plans. */
    const val PLAN_ID = "builtin:ftp-test"

    const val ALL_OUT_SEC = 20 * 60
    const val FTP_FACTOR = 0.95

    /** The steps of the protocol, so the app can name them in the rider's language. */
    enum class Step { WARM_UP, SPIN_UP, EASY, OPENER, RECOVERY, ALL_OUT, COOL_DOWN }

    fun plan(name: String = "FTP test", label: (Step) -> String = ::englishLabel) = WorkoutPlan(
        id = PLAN_ID,
        name = name,
        steps = listOf(
            Block(label(Step.WARM_UP), 10 * 60, targetFtpPercent = 55),
            RepeatSet(
                times = 3,
                blocks = listOf(
                    Block(label(Step.SPIN_UP), 60, targetCadenceRpm = 110, targetFtpPercent = 95),
                    Block(label(Step.EASY), 60, targetFtpPercent = 50),
                ),
            ),
            Block(label(Step.EASY), 5 * 60, targetFtpPercent = 50),
            Block(label(Step.OPENER), 5 * 60, targetFtpPercent = 105),
            Block(label(Step.RECOVERY), 10 * 60, targetFtpPercent = 50),
            Block(label(Step.ALL_OUT), ALL_OUT_SEC, allOut = true),
            Block(label(Step.COOL_DOWN), 10 * 60, targetFtpPercent = 45),
        ),
    )

    /**
     * Outcome of an FTP test ride: [ftpW] is estimated from the all-out block's [avgPowerW], and is
     * only meaningful when that block was ridden to the end ([complete]).
     */
    data class Result(val avgPowerW: Double, val ftpW: Int, val complete: Boolean)

    fun estimateFtp(avgPowerW: Double): Int = (avgPowerW * FTP_FACTOR).roundToInt()

    /** The result of [record], or null when it is not an FTP test or the all-out block was not reached. */
    fun result(record: WorkoutRecord): Result? {
        if (record.planId != PLAN_ID) return null
        val effort = record.intervals.firstOrNull { it.allOut } ?: return null
        val avg = effort.stats.avgPowerW
        return Result(avg, estimateFtp(avg), complete = !effort.endedEarly && avg > 0)
    }

    private fun englishLabel(step: Step) = when (step) {
        Step.WARM_UP -> "Warm-up"
        Step.SPIN_UP -> "Spin-up"
        Step.EASY -> "Easy"
        Step.OPENER -> "Opener"
        Step.RECOVERY -> "Recovery"
        Step.ALL_OUT -> "All out"
        Step.COOL_DOWN -> "Cool-down"
    }
}
