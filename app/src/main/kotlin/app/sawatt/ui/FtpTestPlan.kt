package app.sawatt.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.sawatt.R
import app.sawatt.core.workout.Block
import app.sawatt.core.workout.FtpTest
import app.sawatt.core.workout.WorkoutPlan

/** The built-in FTP test plan, with its blocks named in the app language. */
@Composable
fun rememberFtpTestPlan(): WorkoutPlan {
    val context = LocalContext.current
    return remember(LocalConfiguration.current) {
        FtpTest.plan(context.getString(R.string.ftp_test_name)) { step ->
            context.getString(
                when (step) {
                    FtpTest.Step.WARM_UP -> R.string.ftp_step_warm_up
                    FtpTest.Step.SPIN_UP -> R.string.ftp_step_spin_up
                    FtpTest.Step.EASY -> R.string.ftp_step_easy
                    FtpTest.Step.OPENER -> R.string.ftp_step_opener
                    FtpTest.Step.RECOVERY -> R.string.ftp_step_recovery
                    FtpTest.Step.ALL_OUT -> R.string.ftp_step_all_out
                    FtpTest.Step.COOL_DOWN -> R.string.ftp_step_cool_down
                },
            )
        }
    }
}

/** A block's target as shown to the rider: "250 W", "75% FTP (188 W)" or "All out". */
@Composable
fun blockTargetLabel(block: Block, ftpW: Double?): String =
    if (block.allOut) stringResource(R.string.ftp_target_all_out) else block.targetLabel(ftpW)
