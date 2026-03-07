package com.aura.app.ui.util

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

object HapticEngine {
    /** Heavy thud for physical errors or financial confirmations */
    fun triggerThud(view: View) {
        try { view.performHapticFeedback(HapticFeedbackConstants.REJECT) } catch (_: Exception) { }
    }

    /** Sharp, precise tick for interactive component states */
    fun triggerClick(view: View) {
        try { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS) } catch (_: Exception) { }
    }

    /** Satisfying confirmation ramp for successful verifications */
    fun triggerSuccess(view: View) {
        try { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM) } catch (_: Exception) { }
    }

    /** Light tick for progress pulses (e.g. NFC scanning) — subtle but noticeable */
    fun triggerLight(view: View) {
        try { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } catch (_: Exception) { }
    }
}

/**
 * Universal spring-dampened scale modifier.
 * Replaces linear tweening with organic physical momentum.
 */
fun Modifier.springScale(
    isPressed: Boolean,
    scaleDown: Float = 0.95f
): Modifier = composed {
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "spring_scale"
    )

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Infinite pulse glow — scale oscillates 1.0→1.05 and alpha 0.6→1.0.
 * Use on status indicators, active icons, and streak fire elements.
 */
fun Modifier.pulseGlow(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "pulse_glow")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}

/**
 * Slow breathing modifier for async wait states (NFC scanning, RPC pending).
 * Subtle 1.0→1.02 scale over 2 seconds — signals "alive and working."
 */
fun Modifier.breathe(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "breathe")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe_scale",
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Animated shimmer border sweep — premium card edge effect.
 * 45° gradient sweeps across the element's bounds every 1.5 seconds.
 */
fun Modifier.shimmerBorder(
    baseColor: Color = Color(0xFF334155),
    shimmerColor: Color = Color.White.copy(alpha = 0.15f),
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer_border")
    val offset by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_offset",
    )
    this.drawWithContent {
        drawContent()
        val shimmerStart = size.width * offset
        val shimmerEnd = shimmerStart + size.width * 0.4f
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, shimmerColor, Color.Transparent),
                start = Offset(shimmerStart, 0f),
                end = Offset(shimmerEnd, size.height),
            ),
            size = size,
        )
    }
}
