package app.sawatt.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import app.sawatt.R

/**
 * Icons missing from the Material core set, bundled as vector drawables: Material icons, plus
 * SaWatt's own stroke icons for the tab bar.
 *
 * This avoids the `material-icons-extended` library (thousands of icons, 35 MB): the debug APK
 * goes from about 18 to 11 MB.
 */
object SawattIcons {
    val ArrowDownward: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_arrow_downward)
    val ArrowUpward: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_arrow_upward)
    val Bluetooth: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_bluetooth)
    val DirectionsBike: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_directions_bike)
    val Flag: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_flag)
    val Pause: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_pause)
    val Repeat: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_repeat)
    val Stop: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_stop)

    val TabToday: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_tab_today)
    val TabWorkouts: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_tab_workouts)
    val TabHistory: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_tab_history)
    val TabMe: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_tab_me)
}
