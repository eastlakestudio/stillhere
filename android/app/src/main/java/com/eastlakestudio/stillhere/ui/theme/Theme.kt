package com.eastlakestudio.stillhere.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ═══════════════════════════════════════════
// MD3 色彩体系 —— 暖橙系
// ═══════════════════════════════════════════

private val md_primary = Color(0xFFC25A00)
private val md_onPrimary = Color(0xFFFFFFFF)
private val md_primaryContainer = Color(0xFFFFDCC3)
private val md_onPrimaryContainer = Color(0xFF3E1800)

private val md_secondary = Color(0xFFC4376B)
private val md_onSecondary = Color(0xFFFFFFFF)
private val md_secondaryContainer = Color(0xFFFFD9E3)
private val md_onSecondaryContainer = Color(0xFF3E001F)

private val md_tertiary = Color(0xFFB94582)
private val md_onTertiary = Color(0xFFFFFFFF)
private val md_tertiaryContainer = Color(0xFFFFD9E7)
private val md_onTertiaryContainer = Color(0xFF3E002B)

private val md_error = Color(0xFFBA1A1A)
private val md_onError = Color(0xFFFFFFFF)
private val md_errorContainer = Color(0xFFFFDAD6)
private val md_onErrorContainer = Color(0xFF410002)

private val md_background = Color(0xFFFFF8F4)
private val md_onBackground = Color(0xFF221A14)
private val md_surface = Color(0xFFFFF8F4)
private val md_onSurface = Color(0xFF221A14)
private val md_surfaceVariant = Color(0xFFF4DFD0)
private val md_onSurfaceVariant = Color(0xFF52443A)
private val md_outline = Color(0xFF857369)
private val md_outlineVariant = Color(0xFFD7C2B4)

private val md_surfaceContainerLowest = Color(0xFFFFF1EA)
private val md_surfaceContainerLow = Color(0xFFFFF0E1)
private val md_surfaceContainer = Color(0xFFFCE9D7)
private val md_surfaceContainerHigh = Color(0xFFF6E4D2)
private val md_surfaceContainerHighest = Color(0xFFF0DECC)

private val LightColorScheme = lightColorScheme(
    primary = md_primary,
    onPrimary = md_onPrimary,
    primaryContainer = md_primaryContainer,
    onPrimaryContainer = md_onPrimaryContainer,
    secondary = md_secondary,
    onSecondary = md_onSecondary,
    secondaryContainer = md_secondaryContainer,
    onSecondaryContainer = md_onSecondaryContainer,
    tertiary = md_tertiary,
    onTertiary = md_onTertiary,
    tertiaryContainer = md_tertiaryContainer,
    onTertiaryContainer = md_onTertiaryContainer,
    error = md_error,
    onError = md_onError,
    errorContainer = md_errorContainer,
    onErrorContainer = md_onErrorContainer,
    background = md_background,
    onBackground = md_onBackground,
    surface = md_surface,
    onSurface = md_onSurface,
    surfaceVariant = md_surfaceVariant,
    onSurfaceVariant = md_onSurfaceVariant,
    outline = md_outline,
    outlineVariant = md_outlineVariant,
    surfaceContainerLowest = md_surfaceContainerLowest,
    surfaceContainerLow = md_surfaceContainerLow,
    surfaceContainer = md_surfaceContainer,
    surfaceContainerHigh = md_surfaceContainerHigh,
    surfaceContainerHighest = md_surfaceContainerHighest,
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB77C),
    onPrimary = Color(0xFF502900),
    primaryContainer = Color(0xFF714000),
    onPrimaryContainer = Color(0xFFFFDCC3),
    secondary = Color(0xFFFFB1C9),
    onSecondary = Color(0xFF650037),
    secondaryContainer = Color(0xFF8D174E),
    onSecondaryContainer = Color(0xFFFFD9E3),
    tertiary = Color(0xFFFFB1D1),
    onTertiary = Color(0xFF640044),
    tertiaryContainer = Color(0xFF9A276A),
    onTertiaryContainer = Color(0xFFFFD9E7),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1B110C),
    onBackground = Color(0xFFF0DFD5),
    surface = Color(0xFF1B110C),
    onSurface = Color(0xFFF0DFD5),
    surfaceVariant = Color(0xFF52443A),
    onSurfaceVariant = Color(0xFFD7C2B4),
    outline = Color(0xFFA08D80),
    outlineVariant = Color(0xFF52443A),
    surfaceContainerLowest = Color(0xFF150C07),
    surfaceContainerLow = Color(0xFF231914),
    surfaceContainer = Color(0xFF271D18),
    surfaceContainerHigh = Color(0xFF322822),
    surfaceContainerHighest = Color(0xFF3E322C),
)

// ═══════════════════════════════════════════
// MD3 排版体系
// ═══════════════════════════════════════════

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

// ═══════════════════════════════════════════
// MD3 形状体系
// ═══════════════════════════════════════════

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

// ═══════════════════════════════════════════
// Theme Composable
// ═══════════════════════════════════════════

@Composable
fun StillHereTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
