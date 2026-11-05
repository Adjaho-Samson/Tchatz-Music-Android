package com.tchatz.klassio.ui.theme

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

/** Palette officielle Tchatz Klassio — sobre, académique, contrastée. */
object KlassioColors {
    val Blue = Color(0xFF2563EB)
    val BlueDark = Color(0xFF1D4ED8)
    val Navy = Color(0xFF163B70)
    val White = Color(0xFFFFFFFF)
    val GrayLight = Color(0xFFF3F6FA)
    val GrayBorder = Color(0xFFE2E8F0)
    val GrayDark = Color(0xFF334155)
    val GrayMedium = Color(0xFF64748B)
    val Success = Color(0xFF15803D)
    val SuccessBg = Color(0xFFDCFCE7)
    val Warning = Color(0xFFB45309)
    val WarningBg = Color(0xFFFEF3C7)
    val Danger = Color(0xFFB91C1C)
    val DangerBg = Color(0xFFFEE2E2)
    val BlueBg = Color(0xFFDBEAFE)

    // Thème sombre facultatif
    val DarkBg = Color(0xFF0F172A)
    val DarkSurface = Color(0xFF1E293B)
    val DarkBorder = Color(0xFF334155)
    val DarkText = Color(0xFFE2E8F0)
}

private val LightScheme = lightColorScheme(
    primary = KlassioColors.Blue,
    onPrimary = KlassioColors.White,
    primaryContainer = KlassioColors.BlueBg,
    onPrimaryContainer = KlassioColors.Navy,
    secondary = KlassioColors.Navy,
    onSecondary = KlassioColors.White,
    background = KlassioColors.GrayLight,
    onBackground = KlassioColors.GrayDark,
    surface = KlassioColors.White,
    onSurface = KlassioColors.GrayDark,
    surfaceVariant = KlassioColors.GrayLight,
    onSurfaceVariant = KlassioColors.GrayMedium,
    outline = KlassioColors.GrayBorder,
    error = KlassioColors.Danger,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = KlassioColors.DarkBg,
    primaryContainer = KlassioColors.Navy,
    onPrimaryContainer = Color(0xFFDBEAFE),
    background = KlassioColors.DarkBg,
    onBackground = KlassioColors.DarkText,
    surface = KlassioColors.DarkSurface,
    onSurface = KlassioColors.DarkText,
    surfaceVariant = KlassioColors.DarkBorder,
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = KlassioColors.DarkBorder,
    error = Color(0xFFFCA5A5),
)

private val KlassioTypography = Typography(
    displaySmall = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun KlassioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = KlassioTypography,
        content = content,
    )
}
