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
 * Exact Antigravity 2.0 Desktop Dark Palette matching the reference screenshot:
 * - Main Canvas: `#0E0E10`
 * - Left Sidebar: `#121215`
 * - Prompt Card: `#18191C` with `#26272C` border
 * - Prompt Footer (`Local Checkout`): `#131417`
 * - `+ New Conversation` Pill: `#25262B`
 * - Active Session Dot: `#3B82F6`
 */
object AntigravityPalette {
    val TopBarBg = Color(0xFF0B0B0D)
    val BackgroundOled = Color(0xFF0E0E10)
    val SidebarBg = Color(0xFF121215)
    val SurfaceDark = Color(0xFF18191C)
    val SurfaceFooter = Color(0xFF131417)
    val SurfaceElevated = Color(0xFF232429)
    val NewConvoButtonBg = Color(0xFF25262B)
    val NewConvoButtonBorder = Color(0xFF31333A)
    val SendButtonCircleBg = Color(0xFF2A2C32)
    val BorderSubtle = Color(0xFF222328)
    val BorderCard = Color(0xFF27282E)
    val AccentBlueDot = Color(0xFF3B82F6)
    val AccentPrimary = Color(0xFF4F8EF7)
    val AccentCyan = Color(0xFF38BDF8)
    val StatusActiveGreen = Color(0xFF22C55E)
    val StatusWarningAmber = Color(0xFFF59E0B)
    val StatusErrorRed = Color(0xFFEF4444)
    val DiffAddGreen = Color(0xFF34D399)
    val DiffAddBg = Color(0xFF0F291E)
    val DiffDelRed = Color(0xFFF87171)
    val DiffDelBg = Color(0xFF2D161B)
    val TextPrimary = Color(0xFFE4E6EB)
    val TextSecondary = Color(0xFF9CA0A8)
    val TextMuted = Color(0xFF686B73)
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
        fontSize = 17.sp,
        lineHeight = 22.sp,
        color = AntigravityPalette.TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = AntigravityPalette.TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
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
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
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
