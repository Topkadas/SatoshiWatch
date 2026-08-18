package com.satoshiwatch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Bitcoinová oranžová jako akcent, hluboké černé pozadí kvůli OLED úspoře
val BitcoinOrange = Color(0xFFF7931A)
val DeepBackground = Color(0xFF0B0F14)
val SurfaceDark = Color(0xFF141A22)
val SurfaceVariantDark = Color(0xFF1D2530)
val AlertRed = Color(0xFFFF5252)
val ConfirmGreen = Color(0xFF4CAF50)
val TextPrimaryDark = Color(0xFFE8EAED)
val TextSecondaryDark = Color(0xFF9AA5B1)

private val DarkColors = darkColorScheme(
    primary = BitcoinOrange,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF5C3A08),
    onPrimaryContainer = Color(0xFFFFDDB5),
    secondary = TextSecondaryDark,
    onSecondary = Color.Black,
    background = DeepBackground,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    error = AlertRed,
    onError = Color.Black,
    outline = Color(0xFF3A4552)
)

private val LightColors = lightColorScheme(
    primary = BitcoinOrange,
    onPrimary = Color.White,
    background = Color(0xFFFAFAFA),
    surface = Color.White,
    error = Color(0xFFD32F2F)
)

val AppTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 0.sp
    )
)

/** Výchozí je tmavý režim (OLED); světlý jen pokud si ho systém vyžádá a [forceDark] je vypnuto. */
@Composable
fun SatoshiWatchTheme(
    forceDark: Boolean = true,
    content: @Composable () -> Unit
) {
    val dark = forceDark || isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
