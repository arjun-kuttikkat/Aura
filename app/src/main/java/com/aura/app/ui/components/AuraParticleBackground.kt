package com.aura.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500
import kotlin.math.PI
import kotlin.math.sin

private data class ParticleConfig(
    val baseX: Float,
    val baseY: Float,
    val radius: Float,
    val alpha: Float,
    val color: Color,
    val phaseOffset: Float,
    val driftX: Float,
    val driftY: Float,
)

/**
 * Floating particle background for Home, Profile, Rewards screens.
 * 15 circular particles, 4-40dp diameter, opacity 0.04-0.12,
 * Orange500 and Gold500, slow drift with random phase offset.
 */
@Composable
fun AuraParticleBackground(
    modifier: Modifier = Modifier,
    particleCount: Int = 15,
) {
    val particles = remember(particleCount) {
        (0 until particleCount).map { i ->
            val seed = (i * 7919) % 1000 / 1000f
            ParticleConfig(
                baseX = 0.1f + (seed * 0.8f),
                baseY = 0.1f + (((i * 2659) % 1000) / 1000f * 0.8f),
                radius = 4f + (seed * 36f),
                alpha = 0.04f + (seed * 0.08f),
                color = if (i % 3 == 0) Gold500 else Orange500,
                phaseOffset = (i * 0.13f) % 1f,
                driftX = 0.05f + (seed * 0.1f),
                driftY = 0.05f + (((i * 4001) % 1000) / 1000f * 0.1f),
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val baseTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000),
            repeatMode = RepeatMode.Restart,
        ),
        label = "time",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        drawParticles(
            size = IntSize(size.width.toInt(), size.height.toInt()),
            baseTime = baseTime,
            particles = particles,
        )
    }
}

private fun DrawScope.drawParticles(
    size: IntSize,
    baseTime: Float,
    particles: List<ParticleConfig>,
) {
    val width = size.width.toFloat()
    val height = size.height.toFloat()

    val radiusScale = density // Convert dp-like values to pixels
    particles.forEach { config ->
        val phase = (baseTime + config.phaseOffset) % 1f
        val x = (config.baseX + sin(phase * 2 * PI.toFloat()) * config.driftX) * width
        val y = (config.baseY + sin(phase * 2 * PI.toFloat() + config.phaseOffset * 2 * PI.toFloat()) * config.driftY) * height

        drawCircle(
            color = config.color.copy(alpha = config.alpha),
            radius = config.radius * radiusScale,
            center = Offset(x, y),
        )
    }
}
