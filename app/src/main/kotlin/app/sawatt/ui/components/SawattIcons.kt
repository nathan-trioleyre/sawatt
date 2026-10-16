package app.sawatt.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import app.sawatt.R

/**
 * Icônes Material absentes du jeu de base, embarquées en drawables vectoriels.
 *
 * On évite ainsi la bibliothèque `material-icons-extended` (plusieurs milliers d'icônes, 35 Mo) :
 * l'APK de debug passe d'environ 18 à 11 Mo.
 */
object SawattIcons {
    val ArrowDownward: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_arrow_downward)
    val ArrowUpward: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_arrow_upward)
    val Bluetooth: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_bluetooth)
    val DirectionsBike: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_directions_bike)
    val Flag: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_flag)
    val History: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_history)
    val HourglassEmpty: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_hourglass_empty)
    val ListAlt: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_list_alt)
    val Pause: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_pause)
    val Repeat: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_repeat)
    val Stop: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_stop)
}
