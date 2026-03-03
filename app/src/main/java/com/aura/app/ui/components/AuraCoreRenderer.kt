package com.aura.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500
import com.aura.app.ui.theme.SolanaGreen
import com.aura.app.ui.theme.UltraViolet
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Dynamic Aura Core — a Canvas-drawn, evolving geometric visualization
 * that physically represents the user's streak and reputation.
 *
 * Evolution tiers:
 * - 0-7 days:  Dim Spark — single pulsing circle
 * - 8-30 days: Sprout — rotating triangle with inner glow
 * - 31-89 days: Tree — rotating hexagon with particle orbits
 * - 90+ days: Full Aura ✨ — sacred geometry with chromatic layers
 *
 * If the streak is broken (isDegraded = true), hairline cracks appear.
 */
@Composable
fun AuraCoreRenderer(
    streakDays: Int,
    auraScore: Int,
    isDegraded: Boolean = false,
    hotzoneColor: Color? = null,
    size: Dp = 160.dp,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "aura_core")

    // Primary rotation — slow and majestic
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 12000, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "rotation",
    )

    // Secondary counter-rotation for inner layers
    val counterRotation by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 8000, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "counter_rotation",
    )

    // Pulse breathing effect
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 2000, easing = LinearEasing),
            RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    // Particle orbit angle
    val particleAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 5000, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "particles",
    )

    // Crack flicker for degraded state
    val crackAlpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 800, easing = LinearEasing),
            RepeatMode.Reverse,
        ),
        label = "crack",
    )

    val primaryColor = hotzoneColor ?: Orange500
    val secondaryColor = if (streakDays >= 90) SolanaGreen else Gold500
    val tertiaryColor = UltraViolet

    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val maxR = this.size.minDimension / 2f

        // ─── Background glow ─────────────────────────────────────────
        drawCircle(
            brush = Brush.radialGradient(
                listOf(primaryColor.copy(alpha = 0.15f * pulse), Color.Transparent),
                center = center,
                radius = maxR * 1.2f,
            ),
            radius = maxR * 1.2f,
            center = center,
        )

        when {
            // ═══ TIER 4: Full Aura (90+ days) — Sacred Geometry ═══
            streakDays >= 90 -> {
                // Outer chromatic ring
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(SolanaGreen, UltraViolet, Gold500, primaryColor, SolanaGreen),
                        center = center,
                    ),
                    radius = maxR * 0.95f * pulse,
                    center = center,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                )

                // Rotating outer hexagon
                rotate(rotation, pivot = center) {
                    drawPolygon(center, maxR * 0.85f * pulse, 6, primaryColor.copy(alpha = 0.8f), 3.dp.toPx())
                }

                // Counter-rotating inner hexagon
                rotate(counterRotation, pivot = center) {
                    drawPolygon(center, maxR * 0.6f * pulse, 6, secondaryColor.copy(alpha = 0.6f), 2.dp.toPx())
                }

                // Inner rotating triangle
                rotate(rotation * 1.5f, pivot = center) {
                    drawPolygon(center, maxR * 0.4f * pulse, 3, tertiaryColor.copy(alpha = 0.7f), 2.dp.toPx())
                }

                // Particle orbits — 8 particles
                drawParticleOrbit(center, maxR * 0.75f, particleAngle, 8, SolanaGreen, 4.dp.toPx())
                drawParticleOrbit(center, maxR * 0.55f, -particleAngle * 1.3f, 5, Gold500.copy(alpha = 0.7f), 3.dp.toPx())

                // Central core glow
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.9f), primaryColor.copy(alpha = 0.5f), Color.Transparent),
                        center = center,
                        radius = maxR * 0.2f,
                    ),
                    radius = maxR * 0.2f * pulse,
                    center = center,
                )
            }

            // ═══ TIER 3: Tree (31-89 days) — Hexagon + Particles ═══
            streakDays >= 31 -> {
                // Outer glow ring
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(primaryColor, secondaryColor, primaryColor),
                        center = center,
                    ),
                    radius = maxR * 0.9f * pulse,
                    center = center,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )

                // Rotating hexagon
                rotate(rotation, pivot = center) {
                    drawPolygon(center, maxR * 0.7f * pulse, 6, primaryColor.copy(alpha = 0.7f), 2.5f.dp.toPx())
                }

                // Counter-rotating inner triangle
                rotate(counterRotation, pivot = center) {
                    drawPolygon(center, maxR * 0.4f * pulse, 3, secondaryColor.copy(alpha = 0.5f), 2.dp.toPx())
                }

                // Particle orbit — 6 particles
                drawParticleOrbit(center, maxR * 0.65f, particleAngle, 6, primaryColor.copy(alpha = 0.8f), 3.dp.toPx())

                // Core
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.7f), primaryColor.copy(alpha = 0.3f), Color.Transparent),
                        center = center,
                        radius = maxR * 0.18f,
                    ),
                    radius = maxR * 0.18f * pulse,
                    center = center,
                )
            }

            // ═══ TIER 2: Sprout (8-30 days) — Triangle + Glow ═══
            streakDays >= 8 -> {
                // Outer glow ring
                drawCircle(
                    color = primaryColor.copy(alpha = 0.3f),
                    radius = maxR * 0.85f * pulse,
                    center = center,
                    style = Stroke(width = 2.dp.toPx()),
                )

                // Rotating triangle
                rotate(rotation, pivot = center) {
                    drawPolygon(center, maxR * 0.6f * pulse, 3, primaryColor.copy(alpha = 0.6f), 2.dp.toPx())
                }

                // Inner glow circle
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(primaryColor.copy(alpha = 0.4f), Color.Transparent),
                        center = center,
                        radius = maxR * 0.35f,
                    ),
                    radius = maxR * 0.35f * pulse,
                    center = center,
                )

                // Core spark
                drawCircle(
                    color = Color.White.copy(alpha = 0.6f),
                    radius = maxR * 0.08f * pulse,
                    center = center,
                )
            }

            // ═══ TIER 1: Dim Spark (0-7 days) — Pulsing Circle ═══
            else -> {
                // Simple pulsing circles
                drawCircle(
                    color = primaryColor.copy(alpha = 0.15f * pulse),
                    radius = maxR * 0.7f * pulse,
                    center = center,
                    style = Stroke(width = 1.5f.dp.toPx()),
                )

                drawCircle(
                    color = primaryColor.copy(alpha = 0.25f),
                    radius = maxR * 0.4f * pulse,
                    center = center,
                    style = Stroke(width = 1.dp.toPx()),
                )

                // Dim center spark
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(primaryColor.copy(alpha = 0.5f * pulse), Color.Transparent),
                        center = center,
                        radius = maxR * 0.15f,
                    ),
                    radius = maxR * 0.15f,
                    center = center,
                )
            }
        }

        // ─── Degradation cracks ──────────────────────────────────────
        if (isDegraded) {
            val crackColor = Color.Red.copy(alpha = crackAlpha)
            val crackPath = Path().apply {
                // Crack 1: top-right
                moveTo(center.x, center.y - maxR * 0.15f)
                lineTo(center.x + maxR * 0.12f, center.y - maxR * 0.35f)
                lineTo(center.x + maxR * 0.05f, center.y - maxR * 0.28f)
                lineTo(center.x + maxR * 0.18f, center.y - maxR * 0.5f)
            }
            drawPath(crackPath, crackColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

            val crackPath2 = Path().apply {
                // Crack 2: bottom-left
                moveTo(center.x, center.y + maxR * 0.1f)
                lineTo(center.x - maxR * 0.1f, center.y + maxR * 0.3f)
                lineTo(center.x - maxR * 0.06f, center.y + maxR * 0.22f)
                lineTo(center.x - maxR * 0.15f, center.y + maxR * 0.45f)
            }
            drawPath(crackPath2, crackColor, style = Stroke(width = 1.5f.dp.toPx(), cap = StrokeCap.Round))
        }

        // ─── Aura Score arc overlay ──────────────────────────────────
        val scoreArc = 360f * (auraScore / 100f)
        drawArc(
            brush = Brush.sweepGradient(
                listOf(primaryColor.copy(alpha = 0.6f), secondaryColor.copy(alpha = 0.6f), primaryColor.copy(alpha = 0.6f)),
                center = center,
            ),
            startAngle = -90f,
            sweepAngle = scoreArc,
            useCenter = false,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            topLeft = Offset(center.x - maxR, center.y - maxR),
            size = androidx.compose.ui.geometry.Size(maxR * 2f, maxR * 2f),
        )
    }
}

// ─── Drawing Helpers ──────────────────────────────────────────────────────

private fun DrawScope.drawPolygon(
    center: Offset,
    radius: Float,
    sides: Int,
    color: Color,
    strokeWidth: Float,
) {
    val path = Path()
    for (i in 0 until sides) {
        val angle = (2.0 * PI * i / sides - PI / 2).toFloat()
        val x = center.x + radius * cos(angle)
        val y = center.y + radius * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
}

private fun DrawScope.drawParticleOrbit(
    center: Offset,
    orbitRadius: Float,
    currentAngle: Float,
    count: Int,
    color: Color,
    particleRadius: Float,
) {
    for (i in 0 until count) {
        val angle = Math.toRadians((currentAngle + (360.0 / count) * i).toDouble())
        val x = center.x + orbitRadius * cos(angle).toFloat()
        val y = center.y + orbitRadius * sin(angle).toFloat()
        drawCircle(color, particleRadius, Offset(x, y))
    }
}
