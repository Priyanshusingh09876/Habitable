package edu.bu.cs501.habitable.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Cold night" palette: deep navy for the night sky, ember red/orange for heat
 * violations, ice blue for outdoor weather, and a mint green for "within the law".
 * All text/background pairs used for body text meet WCAG AA.
 */
object HabitableColors {
    // Night
    val NavyDeep = Color(0xFF070E1A)
    val Navy = Color(0xFF0F1B2D)
    val NavyLight = Color(0xFF1B2B44)
    val NavyGlow = Color(0xFF23406B)
    val Mist = Color(0xFF9FB0C8)
    val MistLight = Color(0xFFC9D6E8)

    // Neutrals
    val Ink = Color(0xFF141B26)
    val Muted = Color(0xFF5B6676)
    val Paper = Color(0xFFF3F5F9)
    val Card = Color(0xFFEAEEF4)
    val Line = Color(0xFFDDE3EB)

    // Heat / violation
    val Heat = Color(0xFFE4572E)
    val HeatBright = Color(0xFFFF7A45)
    val HeatSoft = Color(0xFFFCE5DC)
    val HeatInk = Color(0xFFA33A1B)
    val Ember = Color(0xFF5C1A22)
    val EmberDeep = Color(0xFF1E0E1F)

    // Cold / outdoor
    val Cold = Color(0xFF3A86C8)
    val ColdBright = Color(0xFF6CB4F0)
    val ColdSoft = Color(0xFFE3EEF8)
    val ColdInk = Color(0xFF1F5C93)

    // Good / compliant
    val Good = Color(0xFF2E9E6B)
    val GoodBright = Color(0xFF5FD39C)
    val GoodSoft = Color(0xFFE1F3EA)
    val GoodInk = Color(0xFF1D6B47)

    // Warning
    val Amber = Color(0xFFE0A100)
    val AmberSoft = Color(0xFFFFF3D6)
    val AmberInk = Color(0xFF7A5600)

    val Shade = Color(0xFFF7B9A3)
}

/** Gradients reused by the hero cards and the setup banner. */
object HabitableBrushes {
    val Night = Brush.linearGradient(listOf(HabitableColors.NavyDeep, HabitableColors.Navy, HabitableColors.NavyGlow))
    val Heat = Brush.horizontalGradient(listOf(HabitableColors.Heat, HabitableColors.HeatBright))
    /** Temperature scale used by the limit meter: ice blue → mint → ember. */
    val TempScale = Brush.horizontalGradient(
        listOf(HabitableColors.ColdBright, HabitableColors.Cold, HabitableColors.GoodBright, HabitableColors.HeatBright, HabitableColors.Heat),
    )
}

private val LightScheme = lightColorScheme(
    primary = HabitableColors.Navy,
    onPrimary = Color.White,
    primaryContainer = HabitableColors.NavyLight,
    onPrimaryContainer = Color.White,
    secondary = HabitableColors.Heat,
    onSecondary = Color.White,
    secondaryContainer = HabitableColors.HeatSoft,
    onSecondaryContainer = HabitableColors.HeatInk,
    tertiary = HabitableColors.Cold,
    onTertiary = Color.White,
    tertiaryContainer = HabitableColors.ColdSoft,
    onTertiaryContainer = HabitableColors.ColdInk,
    background = HabitableColors.Paper,
    onBackground = HabitableColors.Ink,
    surface = Color.White,
    onSurface = HabitableColors.Ink,
    surfaceVariant = HabitableColors.Card,
    onSurfaceVariant = HabitableColors.Muted,
    surfaceContainer = Color.White,
    surfaceContainerLow = HabitableColors.Paper,
    surfaceContainerHigh = HabitableColors.Card,
    outline = HabitableColors.Line,
    outlineVariant = HabitableColors.Line,
    error = HabitableColors.HeatInk,
    onError = Color.White,
    errorContainer = HabitableColors.HeatSoft,
    onErrorContainer = HabitableColors.HeatInk,
)

private val DarkScheme = darkColorScheme(
    primary = HabitableColors.MistLight,
    onPrimary = HabitableColors.Navy,
    primaryContainer = HabitableColors.NavyLight,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFFF9C7A),
    onSecondary = HabitableColors.Navy,
    secondaryContainer = Color(0xFF5A2A1C),
    onSecondaryContainer = Color(0xFFFFD2C3),
    tertiary = Color(0xFF8FC0EC),
    onTertiary = HabitableColors.Navy,
    tertiaryContainer = Color(0xFF1F3A55),
    onTertiaryContainer = Color(0xFFD6E8FA),
    background = Color(0xFF0A111C),
    onBackground = Color(0xFFE6EAF0),
    surface = Color(0xFF121D2E),
    onSurface = Color(0xFFE6EAF0),
    surfaceVariant = Color(0xFF1B2B44),
    onSurfaceVariant = Color(0xFFA9B4C4),
    surfaceContainer = Color(0xFF121D2E),
    surfaceContainerLow = Color(0xFF0A111C),
    surfaceContainerHigh = Color(0xFF1B2B44),
    outline = Color(0xFF2A3A52),
    outlineVariant = Color(0xFF2A3A52),
    error = Color(0xFFFFB4A3),
    onError = Color(0xFF5A1500),
    errorContainer = Color(0xFF7A2A14),
    onErrorContainer = Color(0xFFFFDAD1),
)

/** Monospace for timestamps and hashes, so evidence reads like a log. */
val EvidenceFont: FontFamily = FontFamily.Monospace

val HabitableTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 64.sp, lineHeight = 68.sp, letterSpacing = (-2.5).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Black, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-1.5).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
)

val HabitableShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun HabitableTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = HabitableTypography,
        shapes = HabitableShapes,
        content = content,
    )
}
