package com.aura.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Orange500,
    onPrimary = Color.Black,
    primaryContainer = Orange700,
    onPrimaryContainer = Color.White,
    secondary = Gold500,
    onSecondary = Color.Black,
    secondaryContainer = SlateElevated,
    onSecondaryContainer = DarkOnSurface,
    tertiary = Gold500,
    onTertiary = DarkVoid,
    background = DarkSurface,
    onBackground = DarkOnSurface,
    surface = DarkBase,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkCard,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = ErrorRed,
)

@Composable
fun AuraTheme(
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content,
    )
}
