package com.antigravity.studio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Pure OLED Dark Theme matching the Desktop Antigravity IDE:
 * - `#111215` Background
 * - `#1A1B1E` Surfaces
 * - `#2A2B2F` Borders
 */
object AntigravityPalette {
    val BackgroundOled = Color(0xFF111215)
    val SurfaceDark = Color(0xFF1A1B1E)
    val SurfaceElevated = Color(0xFF212226)
    val BorderSubtle = Color(0xFF2A2B2F)
    val AccentPrimary = Color(0xFF6E8EFB)
    val AccentCyan = Color(0xFF38BDF8)
    val StatusActiveGreen = Color(0xFF22C55E)
    val StatusWarningAmber = Color(0xFFF59E0B)
    val StatusErrorRed = Color(0xFFEF4444)
    val DiffAddGreen = Color(0xFF34D399)
    val DiffAddBg = Color(0xFF0F291E)
    val DiffDelRed = Color(0xFFF87171)
    val DiffDelBg = Color(0xFF2D161B)
    val TextPrimary = Color(0xFFF3F4F6)
    val TextSecondary = Color(0xFF9CA3AF)
    val TextMuted = Color(0xFF6B7280)
}

private val AntigravityColorScheme = darkColorScheme(
    primary = AntigravityPalette.AccentPrimary,
    onPrimary = Color.White,
    secondary = AntigravityPalette.AccentCyan,
    onSecondary = Color.Black,
    background = AntigravityPalette.BackgroundOled,
    onBackground = AntigravityPalette.TextPrimary,
    surface = AntigravityPalette.SurfaceDark,
    onSurface = AntigravityPalette.TextPrimary,
    surfaceVariant = AntigravityPalette.SurfaceElevated,
    onSurfaceVariant = AntigravityPalette.TextSecondary,
    outline = AntigravityPalette.BorderSubtle,
    error = AntigravityPalette.StatusErrorRed
)

private val AntigravityTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = AntigravityPalette.TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = AntigravityPalette.TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = AntigravityPalette.TextPrimary
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = AntigravityPalette.TextSecondary
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = AntigravityPalette.TextSecondary
    )
)

@Composable
fun AntigravityStudioTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AntigravityColorScheme,
        typography = AntigravityTypography,
        content = content
    )
}
