package app.sawatt.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.sawatt.R

/**
 * SaWatt palette: deep ink-blue surfaces with bright pastel accents. Each metric keeps its own
 * colour on every screen (power: mint, heart rate: pink, cadence: ice cyan).
 */
object SawattColors {
    val Background = Color(0xFF0F1629)
    val Surface = Color(0xFF18213A)
    val SurfaceHigh = Color(0xFF222C4A)
    val Hairline = Color(0xFF2B3656)
    val Track = Color(0xFF26304F)
    val Muted = Color(0xFF34406A)
    val Chevron = Color(0xFF5D6A92)

    val Text = Color(0xFFEEF2FF)
    val TextSecondary = Color(0xFF93A0C4)

    val Accent = Color(0xFF7CF2C4)
    val OnAccent = Background

    val Power = Accent
    val Heart = Color(0xFFFF7EB6)
    val Cadence = Color(0xFF7FE3FF)
    val Pause = Color(0xFFFFD56B)

    /** Interval average against its target: below, on target, above. */
    val Below = Color(0xFF7AB8FF)
    val OnTarget = Accent
    val Above = Color(0xFFFF8A9A)

    /** Power zones 0..4 (see RideMath.powerZone): grey, blue, lime, butter yellow, coral. */
    val Zones = listOf(Color(0xFFA3ADC9), Color(0xFF7AB8FF), Color(0xFFB4F07A), Color(0xFFFFD56B), Color(0xFFFF8A9A))
}

/** Barlow for text, Barlow Condensed for the big numbers. */
object SawattFonts {
    val Text = FontFamily(
        Font(R.font.barlow_regular, FontWeight.Normal),
        Font(R.font.barlow_medium, FontWeight.Medium),
        Font(R.font.barlow_semibold, FontWeight.SemiBold),
        Font(R.font.barlow_bold, FontWeight.Bold),
    )
    val Numbers = FontFamily(
        Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
        Font(R.font.barlow_condensed_bold, FontWeight.Bold),
    )
}

private val scheme = darkColorScheme(
    primary = SawattColors.Accent,
    onPrimary = SawattColors.OnAccent,
    primaryContainer = Color(0xFF1E4A4A),
    onPrimaryContainer = SawattColors.Accent,
    secondary = SawattColors.Pause,
    onSecondary = SawattColors.OnAccent,
    secondaryContainer = SawattColors.SurfaceHigh,
    onSecondaryContainer = SawattColors.Text,
    background = SawattColors.Background,
    onBackground = SawattColors.Text,
    surface = SawattColors.Background,
    onSurface = SawattColors.Text,
    surfaceVariant = SawattColors.Surface,
    onSurfaceVariant = SawattColors.TextSecondary,
    surfaceContainerLowest = SawattColors.Background,
    surfaceContainerLow = SawattColors.Surface,
    surfaceContainer = SawattColors.Surface,
    surfaceContainerHigh = SawattColors.SurfaceHigh,
    surfaceContainerHighest = SawattColors.SurfaceHigh,
    outline = SawattColors.Chevron,
    outlineVariant = SawattColors.Hairline,
    error = SawattColors.Above,
    onError = SawattColors.OnAccent,
)

private fun Typography.withFont(family: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = family)
    return copy(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}

private val typography = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
).withFont(SawattFonts.Text)

@Composable
fun SawattTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography) {
        // Screens drawn straight on the background (the tabs) have no Surface to set the text colour.
        CompositionLocalProvider(LocalContentColor provides SawattColors.Text, content = content)
    }
}
