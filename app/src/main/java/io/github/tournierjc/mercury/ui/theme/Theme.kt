package io.github.tournierjc.mercury.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Grok-inspired palette: near-black canvas, warm white text, one accent. */
data class MercuryColors(
    val canvas: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val userBubble: Color,
    val danger: Color,
    val hairline: Color,
    /** The line around a field: one step brighter than a divider, so a rounded box reads as a box. */
    val hairlineStrong: Color,
    /**
     * The links of the belief graph. `hairline` is 1,26:1 against the canvas — fine as a divider,
     * invisible as the only thing that says two ideas are connected (WCAG 1.4.11 asks for 3:1).
     */
    val graphEdge: Color,
)

val DarkColors = MercuryColors(
    canvas = Color(0xFF000000),
    surface = Color(0xFF141414),
    surfaceMuted = Color(0xFF1F1F1F),
    textPrimary = Color(0xFFF7F7F5),
    textSecondary = Color(0xFF8A8A86),
    accent = Color(0xFF00D4FF),
    userBubble = Color(0xFF262626),
    danger = Color(0xFFFF6B6B),
    hairline = Color(0xFF1E1E1E),
    hairlineStrong = Color(0xFF2E2E2C),
    graphEdge = Color(0xFF5A5A5A),
)

val LightColors = MercuryColors(
    canvas = Color(0xFFFCFCFA),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFF0F0EE),
    textPrimary = Color(0xFF0F0F0E),
    textSecondary = Color(0xFF6B6B68),
    accent = Color(0xFF0088A8),
    userBubble = Color(0xFFEDEDEA),
    danger = Color(0xFFC0392B),
    hairline = Color(0xFFE4E4E0),
    hairlineStrong = Color(0xFFD3D3CE),
    graphEdge = Color(0xFF8A8A84),
)

val LocalMercuryColors = staticCompositionLocalOf { DarkColors }

private val MercuryTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.3).sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.6.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
)

/**
 * Shapes, set once.
 *
 * Material's `extraSmall` is what `OutlinedTextField` and `FilterChip` take their corner from,
 * so the whole app's fields become rounded by changing this instead of remembering a `shape =`
 * argument on every call site.
 */
private val MercuryShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@Composable
fun MercuryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val scheme = if (darkTheme) {
        darkColorScheme(
            background = colors.canvas,
            surface = colors.surface,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            primary = colors.accent,
            onPrimary = Color.Black,
            error = colors.danger,
            outline = colors.hairlineStrong,
            outlineVariant = colors.hairline,
            surfaceVariant = colors.surfaceMuted,
        )
    } else {
        lightColorScheme(
            background = colors.canvas,
            surface = colors.surface,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            primary = colors.accent,
            onPrimary = Color.White,
            error = colors.danger,
            outline = colors.hairlineStrong,
            outlineVariant = colors.hairline,
            surfaceVariant = colors.surfaceMuted,
        )
    }
    CompositionLocalProvider(LocalMercuryColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MercuryTypography,
            shapes = MercuryShapes,
            content = content,
        )
    }
}

object MercuryTheme {
    val dimensions: Dimensions = Dimensions()
}

data class Dimensions(
    val gutter: androidx.compose.ui.unit.Dp = 20.dp,
    val bubbleRadius: androidx.compose.ui.unit.Dp = 22.dp,
)
