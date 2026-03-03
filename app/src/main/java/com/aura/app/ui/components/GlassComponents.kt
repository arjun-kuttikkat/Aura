package com.aura.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aura.app.ui.theme.DarkCard
import com.aura.app.ui.theme.Orange500

/**
 * Glass morphism card with gradient border glow.
 * Every card in the app should use GlassCard or a variant.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glowColor: Color = Orange500,
    cornerRadius: Dp = 20.dp,
    elevation: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = glowColor.copy(alpha = 0.15f),
                spotColor = glowColor.copy(alpha = 0.3f),
            )
            .background(
                color = DarkCard,
                shape = RoundedCornerShape(cornerRadius),
            )
            .border(
                width = 0.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.4f),
                        glowColor.copy(alpha = 0.1f),
                        Color.Transparent,
                    ),
                ),
                shape = RoundedCornerShape(cornerRadius),
            ),
    ) {
        content()
    }
}

/**
 * Dark card variant for dense content areas.
 */
@Composable
fun GlassCardDark(
    modifier: Modifier = Modifier,
    glowColor: Color = Orange500,
    cornerRadius: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .background(
                color = DarkCard,
                shape = RoundedCornerShape(cornerRadius),
            )
            .border(
                width = 0.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.25f),
                        glowColor.copy(alpha = 0.08f),
                    ),
                ),
                shape = RoundedCornerShape(cornerRadius),
            ),
    ) {
        content()
    }
}
