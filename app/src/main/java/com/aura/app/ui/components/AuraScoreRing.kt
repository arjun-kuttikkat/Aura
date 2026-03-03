package com.aura.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aura.app.ui.theme.AuraAnimations
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500

/**
 * Aura Score Ring - animated arc that displays trust score.
 * Shared between Home hero card and Profile.
 */
@Composable
fun AuraScoreRing(
    score: Int,
    size: Dp = 80.dp,
    animate: Boolean = true,
    showNumber: Boolean = true,
    strokeWidth: Dp = 6.dp,
) {
    val clampedScore = score.coerceIn(0, 100)
    val spec = if (animate) tween<Float>(
        durationMillis = AuraAnimations.ScoreRingDuration,
        easing = FastOutSlowInEasing,
    ) else tween<Float>(durationMillis = 1)
    val animatedProgress by animateFloatAsState(
        targetValue = clampedScore / 100f,
        animationSpec = spec,
        label = "scoreRing",
    )

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val radius = (size.toPx() - strokePx) / 2f
            val center = Offset(size.toPx() / 2f, size.toPx() / 2f)

            // Background ring
            drawCircle(
                color = Color.Gray.copy(alpha = 0.3f),
                radius = radius,
                center = center,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )

            // Score arc (orange→gold gradient, start from top = -90 degrees)
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(Orange500, Gold500, Orange500),
                ),
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(2 * radius, 2 * radius),
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }

        if (showNumber) {
            Text(
                text = "$clampedScore",
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Orange500,
            )
        }
    }
}
