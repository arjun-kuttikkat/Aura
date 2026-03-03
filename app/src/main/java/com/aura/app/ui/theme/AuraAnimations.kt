package com.aura.app.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Global Aura animation specs. Use these everywhere for consistency.
 * Every animation must feel alive — nothing is static.
 */
object AuraAnimations {

    /** Premium spring for natural motion (cards, modals, FAB) */
    val AuraSpring = spring<Float>(
        dampingRatio = 0.7f,
        stiffness = Spring.StiffnessMedium,
    )

    /** Smooth ease-out for enter/exit transitions */
    val AuraEaseOut = tween<Float>(
        durationMillis = 400,
        easing = FastOutSlowInEasing,
    )

    /** Snappy response for micro-interactions */
    val AuraSnappy = tween<Float>(
        durationMillis = 250,
        easing = LinearOutSlowInEasing,
    )

    /** Screen content slide-up duration */
    const val ScreenEnterDuration = 300

    /** Stagger delay between list item animations */
    const val StaggerDelay = 80

    /** Card scale animation: 0.94 → 1.0 */
    const val CardScaleStart = 0.94f
    const val CardScaleEnd = 1f

    /** Button press scale */
    const val ButtonPressScale = 0.96f

    /** Score ring arc animation duration */
    const val ScoreRingDuration = 800

    /** Score count-up duration */
    const val ScoreCountUpDuration = 1000

    /** Glow pulse cycle (InfiniteTransition) */
    const val GlowPulseDuration = 3000
}
