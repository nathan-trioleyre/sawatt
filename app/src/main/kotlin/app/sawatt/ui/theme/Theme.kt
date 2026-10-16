package app.sawatt.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** SaWatt palette: orange-red accent and the five power zone colours. */
object SawattColors {
    val Accent = Color(0xFFE5684A)
    val AccentDark = Color(0xFFCE3B17)
    val Background = Color(0xFF121212)
    val Surface = Color(0xFF1E1E1E)
    val SurfaceHigh = Color(0xFF2A2A2A)

    /** Zone 0..4 → grey, blue, green, yellow, red. */
    val Zones = listOf(Color(0xFFD1D3D4), Color(0xFF4867CE), Color(0xFF69BD45), Color(0xFFFFDD3C), Color(0xFFE2303E))

    val OnTarget = Color(0xFF69BD45)
    val Below = Color(0xFF5B9BFF)
    val Above = Color(0xFFE2303E)
    val HeartRed = Color(0xFFE2303E)
}

private val scheme = darkColorScheme(
    primary = SawattColors.Accent,
    onPrimary = Color.White,
    primaryContainer = SawattColors.AccentDark,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFFFDD3C),
    background = SawattColors.Background,
    surface = SawattColors.Background,
    surfaceContainer = SawattColors.Surface,
    surfaceContainerHigh = SawattColors.SurfaceHigh,
    surfaceVariant = SawattColors.Surface,
)

@Composable
fun SawattTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
