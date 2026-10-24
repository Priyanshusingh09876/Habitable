package edu.bu.cs501.habitable.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Palette from the proposal: cold-night navy, heat orange, cold blue.
 * All text/background pairs meet WCAG AA (checked in docs/ARCHITECTURE.md).
 */
object HabitableColors {
    val Navy = Color(0xFF0F1B2D)
    val NavyLight = Color(0xFF1B2B44)
    val Ink = Color(0xFF1E2633)
    val Muted = Color(0xFF5B6676)
    val Paper = Color(0xFFFBFCFD)
    val Card = Color(0xFFF3F5F8)
    val Line = Color(0xFFE2E6EC)
    val Heat = Color(0xFFE4572E)
    val HeatSoft = Color(0xFFFCE5DC)
    val HeatInk = Color(0xFFA33A1B)
    val Cold = Color(0xFF3A86C8)
    val ColdSoft = Color(0xFFE3EEF8)
    val ColdInk = Color(0xFF1F5C93)
    val Good = Color(0xFF2E9E6B)
    val GoodSoft = Color(0xFFE1F3EA)
    val GoodInk = Color(0xFF1D6B47)
    val Shade = Color(0xFFF7B9A3)
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
    outline = HabitableColors.Line,
    outlineVariant = HabitableColors.Line,
    error = HabitableColors.HeatInk,
    onError = Color.White,
    errorContainer = HabitableColors.HeatSoft,
    onErrorContainer = HabitableColors.HeatInk,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFC9D6E8),
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
    background = Color(0xFF0B1320),
    onBackground = Color(0xFFE6EAF0),
    surface = Color(0xFF121D2E),
    onSurface = Color(0xFFE6EAF0),
    surfaceVariant = Color(0xFF1B2B44),
    onSurfaceVariant = Color(0xFFA9B4C4),
    outline = Color(0xFF34455F),
    outlineVariant = Color(0xFF34455F),
    error = Color(0xFFFFB4A3),
    onError = Color(0xFF5A1500),
    errorContainer = Color(0xFF7A2A14),
    onErrorContainer = Color(0xFFFFDAD1),
)

val HabitableTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp),
)

@Composable
fun HabitableTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = HabitableTypography,
        content = content,
    )
}
