package com.homedashboard.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalIsEInk = staticCompositionLocalOf { false }
val LocalIsWallCalendar = staticCompositionLocalOf { false }

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    secondary = Color(0xFF80DEEA),
    tertiary = Color(0xFFA5D6A7),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1976D2),
    secondary = Color(0xFF00ACC1),
    tertiary = Color(0xFF43A047),
    background = Color(0xFFFAFAFA),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black
)

/**
 * High contrast color scheme optimized for e-ink displays.
 * Uses pure black and white for maximum readability.
 */
private val EInkColorScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E0E0),
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFF404040),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0F0F0),
    onSecondaryContainer = Color.Black,
    tertiary = Color(0xFF606060),
    onTertiary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF303030),
    outline = Color(0xFF404040),
    outlineVariant = Color(0xFF606060)
)

@Composable
fun HomeDashboardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    eInkMode: Boolean = false,
    wallCalendarMode: Boolean = false,
    content: @Composable () -> Unit
) {
    // Colors: driven by eInkMode
    val colorScheme = when {
        eInkMode -> EInkColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Typography + Dimensions: driven by wallCalendarMode
    val typography = if (wallCalendarMode) WallCalendarTypography else StandardTypography
    val dimensions = if (wallCalendarMode) WallCalendarDimensions else StandardDimensions

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme || eInkMode
        }
    }

    CompositionLocalProvider(
        LocalIsEInk provides eInkMode,
        LocalIsWallCalendar provides wallCalendarMode,
        LocalDimensions provides dimensions
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
