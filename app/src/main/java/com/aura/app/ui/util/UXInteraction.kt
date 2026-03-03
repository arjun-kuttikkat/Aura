package com.aura.app.ui.util

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

object HapticEngine {
    /** Heavy thud for physical errors or locks */
    fun triggerThud(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }

    /** Sharp, precise tick for interactive component states */
    fun triggerClick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
    }

    /** Satisfying confirmation ramp for successful verifications */
    fun triggerSuccess(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    /** Light tick for progress pulses (e.g. NFC scanning) */
    fun triggerLight(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
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
