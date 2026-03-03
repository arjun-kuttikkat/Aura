package com.aura.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Orange500,
    onPrimary = Color.Black,
    primaryContainer = Gold500,
    onPrimaryContainer = Color.Black,
    secondary = Gold500,
    onSecondary = Color.Black,
    secondaryContainer = DarkCard,
    onSecondaryContainer = DarkOnSurface,
    tertiary = Orange700,
    onTertiary = Color.Black,
    background = DarkBase,
    onBackground = DarkOnSurface,
    surface = DarkBase,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkCard,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = ErrorRed,
)

private val LightColorScheme = lightColorScheme(
    primary = Orange500,
    onPrimary = Color.Black,
    primaryContainer = Gold500,
    onPrimaryContainer = Color.Black,
    secondary = DarkCard,
    onSecondary = DarkOnSurface,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = LightOnSurface,
    tertiary = Gold500,
    onTertiary = Color.Black,
    background = LightSurface,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = ErrorRed,
)

@Composable
fun AuraTheme(
    darkTheme: Boolean = true,  // Force dark for premium feel
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
