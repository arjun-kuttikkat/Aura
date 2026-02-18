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
    primary = Gold500,
    onPrimary = Color.Black,
    primaryContainer = Gold900,
    onPrimaryContainer = Gold100,
    secondary = Orange500,
    onSecondary = Color.Black,
    secondaryContainer = Orange900,
    onSecondaryContainer = Orange100,
    tertiary = Gold300,
    onTertiary = Color.Black,
    background = DarkSurface,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
)

private val LightColorScheme = lightColorScheme(
    primary = Orange700,
    onPrimary = Color.White,
    primaryContainer = Orange100,
    onPrimaryContainer = Orange900,
    secondary = Gold700,
    onSecondary = Color.White,
    secondaryContainer = Gold100,
    onSecondaryContainer = Gold900,
    tertiary = Orange500,
    onTertiary = Color.White,
    background = LightSurface,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
)

@Composable
fun AuraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
