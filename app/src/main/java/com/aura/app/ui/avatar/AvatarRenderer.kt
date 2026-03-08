package com.aura.app.ui.avatar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aura.app.model.AvatarCatalog
import com.aura.app.model.AvatarConfig

/**
 * AvatarCanvas — layered 2D avatar renderer using Compose Canvas.
 *
 * Layers (bottom to top):
 *  0. Background
 *  1. Body + skin tone
 *  2. Face shape + skin tone
 *  3. Eyes (shape + color)
 *  4. Eyebrows
 *  5. Nose
 *  6. Mouth / expression
 *  7. Hair (back layer)
 *  8. Outfit (top + bottom)
 *  9. Hair (front layer)
 * 10. Accessories (hat, glasses, earring)
 * 11. Expression particles (if special expression)
 */
@Composable
fun AvatarCanvas(
    config: AvatarConfig,
    modifier: Modifier = Modifier,
    animate: Boolean = true
) {
    val skinColor  = AvatarCatalog.SKIN_TONES.getOrElse(config.skinTone) { AvatarCatalog.SKIN_TONES[0] }
    val hairColor  = AvatarCatalog.HAIR_COLORS.getOrElse(config.hairColor) { AvatarCatalog.HAIR_COLORS[0] }
    val eyeColor   = AvatarCatalog.EYE_COLORS.getOrElse(config.eyeColor)  { AvatarCatalog.EYE_COLORS[0] }
    val bgColor    = AvatarCatalog.BACKGROUNDS.getOrElse(config.background){ AvatarCatalog.BACKGROUNDS[0] }

    // Idle bobbing animation
    val infiniteTransition = rememberInfiniteTransition(label = "avatar_anim")
    val bobOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = if (animate) 6f else 0f,
        animationSpec = infiniteRepeatable(tween(2000)),
        label = "bob"
    )
    val blink by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 0.05f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 150, delayMillis = 3000)),
        label = "blink"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val headTop = h * 0.10f + bobOffset
        val headR   = w * 0.32f
        val neckTop = headTop + headR * 1.55f
        val bodyTop = neckTop + headR * 0.18f

        // ── Layer 0: Background ──────────────────────────────────────────────
        drawRect(color = bgColor, size = size)
        // Subtle vignette
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)),
                center = Offset(cx, h / 2f),
                radius = maxOf(w, h) * 0.75f
            ),
            size = size
        )

        // ── Layer 1: Body ────────────────────────────────────────────────────
        drawBody(cx, bodyTop, w, h, skinColor, config.outfitTop, config.outfitBottom, config.outfitColor)

        // ── Layer 7: Hair (back) ─────────────────────────────────────────────
        drawHairBack(cx, headTop, headR, hairColor, config.hairStyle)

        // ── Layer 2: Face ────────────────────────────────────────────────────
        drawFace(cx, headTop, headR, skinColor, config.faceShape)

        // ── Layer 3: Eyes ────────────────────────────────────────────────────
        drawEyes(cx, headTop, headR, eyeColor, config.eyeShape, blink)

        // ── Layer 4: Eyebrows ────────────────────────────────────────────────
        drawEyebrows(cx, headTop, headR, hairColor, config.eyebrowStyle, config.expression)

        // ── Layer 5: Nose ────────────────────────────────────────────────────
        drawNose(cx, headTop, headR, skinColor, config.noseStyle)

        // ── Layer 6: Mouth ────────────────────────────────────────────────────
        drawMouth(cx, headTop, headR, config.mouthStyle, config.expression)

        // ── Layer 9: Hair (front) ────────────────────────────────────────────
        drawHairFront(cx, headTop, headR, hairColor, config.hairStyle)

        // ── Layer 10: Hat ────────────────────────────────────────────────────
        if (config.hat >= 0) drawHat(cx, headTop, headR, hairColor, config.hat)

        // ── Layer 10: Glasses ────────────────────────────────────────────────
        if (config.glasses >= 0) drawGlasses(cx, headTop, headR, config.glasses)
    }
}

// ── Layer Drawing Functions ────────────────────────────────────────────────────

private fun DrawScope.drawBody(cx: Float, bodyTop: Float, w: Float, h: Float,
                                skin: Color, topStyle: Int, bottomStyle: Int, outfitColorIdx: Int) {
    val outfitColors = listOf(
        Color(0xFF2D3436), Color(0xFFE65100), Color(0xFFE17055),
        Color(0xFFFF9800), Color(0xFFFFD700), Color(0xFFFFFFFF),
        Color(0xFFFF7675), Color(0xFFD63031)
    )
    val outfitColor = outfitColors.getOrElse(outfitColorIdx) { outfitColors[0] }
    val neckW = w * 0.12f
    val neckH = w * 0.1f

    // Neck
    drawRect(color = skin, topLeft = Offset(cx - neckW / 2f, bodyTop - neckH), size = Size(neckW, neckH + 4f))

    // Body outline (trapezoid approximated with a rounded rect)
    val bodyW = w * 0.70f
    val bodyH = h * 0.42f
    val bodyPath = Path().apply {
        moveTo(cx - w * 0.28f, bodyTop)
        lineTo(cx + w * 0.28f, bodyTop)
        lineTo(cx + bodyW / 2f, bodyTop + bodyH)
        lineTo(cx - bodyW / 2f, bodyTop + bodyH)
        close()
    }
    drawPath(bodyPath, color = outfitColor)

    // Details based on topStyle
    when (topStyle) {
        1 -> { // Hoodie (Draw hood and pocket)
            // Hood outline behind neck
            drawRoundRect(color = outfitColor.copy(alpha=0.8f), topLeft = Offset(cx - neckW * 1.5f, bodyTop - neckH * 0.5f), size = Size(neckW * 3f, neckH * 1.5f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(neckW))
            // Kangaroo pocket
            val pocketW = bodyW * 0.6f
            val pocketH = bodyH * 0.35f
            drawRoundRect(color = outfitColor.copy(alpha=0.9f), topLeft = Offset(cx - pocketW/2f, bodyTop + bodyH * 0.5f), size = Size(pocketW, pocketH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f))
            // Pocket lines
            drawLine(Color.Black.copy(alpha=0.2f), Offset(cx - pocketW/2f, bodyTop + bodyH * 0.5f), Offset(cx - pocketW/2f + 10f, bodyTop + bodyH * 0.8f), strokeWidth = 3f)
            drawLine(Color.Black.copy(alpha=0.2f), Offset(cx + pocketW/2f, bodyTop + bodyH * 0.5f), Offset(cx + pocketW/2f - 10f, bodyTop + bodyH * 0.8f), strokeWidth = 3f)
        }
        2 -> { // Bomber Jacket (collar and zipper)
            // Zipper
            drawLine(Color(0xFF888888), Offset(cx, bodyTop), Offset(cx, bodyTop + bodyH), strokeWidth = 4f)
            // Collar
            drawOval(Color(0xFF222222), topLeft = Offset(cx - neckW, bodyTop - 5f), size = Size(neckW * 2f, neckH * 0.8f))
        }
        3 -> { // Tee (Neckline)
            drawOval(skin, topLeft = Offset(cx - neckW * 0.8f, bodyTop - 5f), size = Size(neckW * 1.6f, neckH * 0.6f))
            // "AURA" text approximation or star logo
            drawCircle(Color.White.copy(alpha=0.8f), radius = bodyW * 0.15f, center = Offset(cx, bodyTop + bodyH * 0.4f))
        }
        4 -> { // Formal Blazer (Lapels and shirt)
            // White shirt underneath
            val shirtPath = Path().apply {
                moveTo(cx - neckW * 0.8f, bodyTop)
                lineTo(cx + neckW * 0.8f, bodyTop)
                lineTo(cx, bodyTop + bodyH * 0.6f)
                close()
            }
            drawPath(shirtPath, color = Color.White)
            // Tie
            drawLine(Color(0xFFB71C1C), Offset(cx, bodyTop + 10f), Offset(cx, bodyTop + bodyH * 0.45f), strokeWidth = 6f)
            // Lapels (darker shade of blazer)
            drawLine(outfitColor.copy(alpha=0.7f), Offset(cx - neckW * 0.8f, bodyTop), Offset(cx, bodyTop + bodyH * 0.6f), strokeWidth = 4f)
            drawLine(outfitColor.copy(alpha=0.7f), Offset(cx + neckW * 0.8f, bodyTop), Offset(cx, bodyTop + bodyH * 0.6f), strokeWidth = 4f)
        }
        5 -> { // Tank top (shoulders exposed)
            drawOval(skin, topLeft = Offset(cx - w * 0.28f, bodyTop - 5f), size = Size(w * 0.15f, bodyH * 0.4f))
            drawOval(skin, topLeft = Offset(cx + w * 0.13f, bodyTop - 5f), size = Size(w * 0.15f, bodyH * 0.4f))
            drawOval(skin, topLeft = Offset(cx - neckW, bodyTop - 5f), size = Size(neckW * 2f, neckH))
        }
        6 -> { // Denim Jacket (Buttons and seams)
            val seamColor = Color(0xFFE67E22) // Orange threading
            drawLine(Color(0xFF999999), Offset(cx, bodyTop), Offset(cx, bodyTop + bodyH), strokeWidth = 4f) // button lane
            for (i in 1..4) {
               drawCircle(Color(0xFFDDDDDD), radius = 3f, center = Offset(cx, bodyTop + bodyH * (i * 0.2f)))
            }
            // Collar
            drawRect(Color(0xFFECECEC), topLeft = Offset(cx - neckW, bodyTop - 8f), size = Size(neckW * 2f, neckH * 0.8f)) // sherpa collar
        }
        7 -> { // Jordan Fit (Sporty jersey look)
            // V-neck
            val vPath = Path().apply {
                moveTo(cx - neckW * 0.6f, bodyTop)
                lineTo(cx + neckW * 0.6f, bodyTop)
                lineTo(cx, bodyTop + neckH * 1.5f)
                close()
            }
            drawPath(vPath, color = skin)
            // Trim lines
            drawLine(Color.White, Offset(cx - w * 0.25f, bodyTop), Offset(cx - w * 0.25f, bodyTop + bodyH), strokeWidth = 8f)
            drawLine(Color.White, Offset(cx + w * 0.25f, bodyTop), Offset(cx + w * 0.25f, bodyTop + bodyH), strokeWidth = 8f)
            // Number "23" abstract shape
            drawRoundRect(Color.White, topLeft = Offset(cx - 15f, bodyTop + bodyH * 0.3f), size = Size(30f, 35f), style = Stroke(width = 6f))
        }
    }

    // Simple bottom (pants/skirt)
    val bottomColor = when (bottomStyle) {
        0 -> Color(0xFF1A1A1A)
        1 -> Color(0xFF2D2D2D)
        2 -> Color(0xFF27AE60)
        else -> Color(0xFF262626)
    }
    val bottomH = h * 0.22f
    val bottom = bodyTop + bodyH
    drawRect(color = bottomColor, topLeft = Offset(cx - bodyW / 2f * 0.85f, bottom), size = Size(bodyW * 0.85f, bottomH))

    // Legs hint
    val legColor = skin
    drawRect(color = legColor, topLeft = Offset(cx - bodyW * 0.38f, bottom + bottomH - 4f), size = Size(bodyW * 0.30f, h * 0.12f))
    drawRect(color = legColor, topLeft = Offset(cx + bodyW * 0.08f, bottom + bottomH - 4f), size = Size(bodyW * 0.30f, h * 0.12f))
}

private fun DrawScope.drawFace(cx: Float, headTop: Float, headR: Float, skin: Color, faceShape: Int) {
    when (faceShape) {
        1 -> { // Oval: wider
            drawOval(color = skin, topLeft = Offset(cx - headR * 1.05f, headTop), size = Size(headR * 2.1f, headR * 2f))
        }
        2 -> { // Square-ish
            drawRoundRect(color = skin, topLeft = Offset(cx - headR, headTop), size = Size(headR * 2f, headR * 1.85f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(headR * 0.3f))
        }
        3 -> { // Heart (diamond top)
            val path = Path().apply {
                moveTo(cx, headTop + headR * 1.9f)
                cubicTo(cx - headR * 1.2f, headTop + headR, cx - headR * 1.2f, headTop, cx, headTop + headR * 0.4f)
                cubicTo(cx + headR * 1.2f, headTop, cx + headR * 1.2f, headTop + headR, cx, headTop + headR * 1.9f)
            }
            drawPath(path, color = skin)
        }
        else -> { // Default circle
            drawCircle(color = skin, radius = headR, center = Offset(cx, headTop + headR))
        }
    }
}

private fun DrawScope.drawEyes(cx: Float, headTop: Float, headR: Float, eyeColor: Color, eyeShape: Int, blinkScale: Float) {
    val eyeY  = headTop + headR * 0.85f
    val eyeOffX = headR * 0.38f
    val eyeW  = headR * 0.26f
    val eyeH  = headR * 0.18f * blinkScale

    listOf(-eyeOffX, eyeOffX).forEach { offX ->
        val eyeCx = cx + offX
        // White
        drawOval(Color.White, topLeft = Offset(eyeCx - eyeW, eyeY - eyeH), size = Size(eyeW * 2f, eyeH * 2f))
        // Iris
        drawCircle(eyeColor, radius = eyeH * 0.75f, center = Offset(eyeCx, eyeY))
        // Pupil
        drawCircle(Color.Black.copy(alpha = 0.85f), radius = eyeH * 0.38f, center = Offset(eyeCx, eyeY))
        // Highlight
        drawCircle(Color.White.copy(alpha = 0.8f), radius = eyeH * 0.18f, center = Offset(eyeCx + eyeH * 0.2f, eyeY - eyeH * 0.2f))
        // Eye shape variants
        if (eyeShape == 1) {
            // Cat-eye top line
            val path = Path().apply {
                moveTo(eyeCx - eyeW * 1.1f, eyeY - eyeH * 0.5f)
                cubicTo(eyeCx, eyeY - eyeH * 1.6f, eyeCx + eyeW, eyeY - eyeH * 0.5f, eyeCx + eyeW * 1.2f, eyeY - eyeH * 0.3f)
            }
            drawPath(path, color = Color.Black, style = Stroke(width = eyeH * 0.3f, cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.drawEyebrows(cx: Float, headTop: Float, headR: Float, hairColor: Color, eyebrowStyle: Int, expression: Int) {
    val browY  = headTop + headR * 0.6f
    val browOffX = headR * 0.38f
    val browHalfW = headR * 0.28f
    val raised = if (expression == 3) -headR * 0.06f else 0f   // raised for surprised

    listOf(-browOffX, browOffX).forEach { offX ->
        val browCx = cx + offX
        val sign = if (offX < 0) 1f else -1f
        val path = Path().apply {
            when (eyebrowStyle) {
                0 -> { moveTo(browCx - browHalfW, browY + raised); lineTo(browCx + browHalfW, browY + raised) }
                1 -> { // arched
                    moveTo(browCx - browHalfW, browY + raised + headR * 0.04f)
                    cubicTo(browCx, browY + raised - headR * 0.1f, browCx, browY + raised - headR * 0.1f, browCx + browHalfW, browY + raised + headR * 0.04f)
                }
                2 -> { // angry slant
                    moveTo(browCx - browHalfW, browY + raised + headR * sign * 0.08f)
                    lineTo(browCx + browHalfW, browY + raised - headR * sign * 0.08f)
                }
                else -> { moveTo(browCx - browHalfW, browY + raised); lineTo(browCx + browHalfW, browY + raised) }
            }
        }
        drawPath(path, color = hairColor.copy(alpha = 0.9f), style = Stroke(width = headR * 0.055f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawNose(cx: Float, headTop: Float, headR: Float, skin: Color, noseStyle: Int) {
    val noseY = headTop + headR * 1.12f
    val shadow = skin.copy(alpha = 0.35f)
    when (noseStyle) {
        0 -> { // button nose (two small circles)
            drawCircle(shadow, radius = headR * 0.065f, center = Offset(cx - headR * 0.1f, noseY))
            drawCircle(shadow, radius = headR * 0.065f, center = Offset(cx + headR * 0.1f, noseY))
        }
        1 -> { // line nose
            drawLine(shadow.copy(alpha = 0.5f), Offset(cx - headR * 0.08f, noseY - headR * 0.08f), Offset(cx - headR * 0.06f, noseY + headR * 0.1f), strokeWidth = headR * 0.04f)
        }
        else -> {
            drawCircle(shadow, radius = headR * 0.055f, center = Offset(cx, noseY))
        }
    }
}

private fun DrawScope.drawMouth(cx: Float, headTop: Float, headR: Float, mouthStyle: Int, expression: Int) {
    val mouthY = headTop + headR * 1.42f
    val mouthW = headR * 0.38f
    val path = Path()
    when {
        expression == 1 || mouthStyle == 0 -> { // smile
            path.moveTo(cx - mouthW, mouthY)
            path.cubicTo(cx - mouthW / 2f, mouthY + headR * 0.22f, cx + mouthW / 2f, mouthY + headR * 0.22f, cx + mouthW, mouthY)
        }
        expression == 2 -> { // smirk
            path.moveTo(cx - mouthW * 0.5f, mouthY)
            path.cubicTo(cx, mouthY + headR * 0.18f, cx + mouthW, mouthY + headR * 0.05f, cx + mouthW, mouthY - headR * 0.04f)
        }
        expression == 3 -> { // surprised O
            drawOval(Color(0xFF8B4513).copy(alpha = 0.8f), topLeft = Offset(cx - headR * 0.16f, mouthY - headR * 0.1f), size = Size(headR * 0.32f, headR * 0.22f))
            return
        }
        else -> {
            path.moveTo(cx - mouthW, mouthY); path.lineTo(cx + mouthW, mouthY)
        }
    }
    drawPath(path, color = Color(0xFFC0392B), style = Stroke(width = headR * 0.07f, cap = StrokeCap.Round))
}

private fun DrawScope.drawHairBack(cx: Float, headTop: Float, headR: Float, hairColor: Color, hairStyle: Int) {
    when (hairStyle) {
        3, 4 -> { // long flowing / bun — draw back part first
            val path = Path().apply {
                moveTo(cx - headR * 1.1f, headTop + headR * 0.4f)
                cubicTo(cx - headR * 1.3f, headTop + headR * 2.5f, cx, headTop + headR * 3f, cx + headR * 1.3f, headTop + headR * 2.5f)
                lineTo(cx + headR * 1.0f, headTop + headR * 0.4f)
            }
            drawPath(path, color = hairColor)
        }
        5 -> { // mohawk back
            drawRect(hairColor, topLeft = Offset(cx - headR * 0.12f, headTop - headR * 0.8f), size = Size(headR * 0.24f, headR * 0.9f))
        }
    }
}

private fun DrawScope.drawHairFront(cx: Float, headTop: Float, headR: Float, hairColor: Color, hairStyle: Int) {
    val path = Path()
    when (hairStyle) {
        0 -> { // short natural
            path.addOval(Rect(cx - headR * 1.02f, headTop - headR * 0.1f, cx + headR * 1.02f, headTop + headR * 0.95f))
        }
        1 -> { // spiky fade
            for (i in -2..2) {
                val spikeCx = cx + i * headR * 0.28f
                path.moveTo(spikeCx - headR * 0.1f, headTop + headR * 0.05f)
                path.lineTo(spikeCx, headTop - headR * (0.35f + kotlin.math.abs(i) * 0.05f))
                path.lineTo(spikeCx + headR * 0.1f, headTop + headR * 0.05f)
                path.close()
            }
            // sides
            path.addRect(Rect(cx - headR * 1.02f, headTop + headR * 0.05f, cx - headR * 0.45f, headTop + headR * 0.8f))
            path.addRect(Rect(cx + headR * 0.45f, headTop + headR * 0.05f, cx + headR * 1.02f, headTop + headR * 0.8f))
        }
        2 -> { // braids
            path.addOval(Rect(cx - headR, headTop - headR * 0.05f, cx + headR, headTop + headR * 0.8f))
            // braid strands
            drawLine(hairColor.copy(alpha = 0.6f), Offset(cx - headR * 0.5f, headTop + headR * 0.7f), Offset(cx - headR * 0.6f, headTop + headR * 2.1f), strokeWidth = headR * 0.13f)
            drawLine(hairColor.copy(alpha = 0.6f), Offset(cx + headR * 0.5f, headTop + headR * 0.7f), Offset(cx + headR * 0.6f, headTop + headR * 2.1f), strokeWidth = headR * 0.13f)
        }
        3 -> { // long waves — front
            path.addOval(Rect(cx - headR, headTop - headR * 0.05f, cx + headR, headTop + headR * 0.8f))
        }
        4 -> { // bun + bangs
            path.addOval(Rect(cx - headR, headTop - headR * 0.05f, cx + headR, headTop + headR * 0.75f))
            // bun on top
            drawCircle(hairColor, radius = headR * 0.35f, center = Offset(cx, headTop - headR * 0.1f))
            // bangs
            path.addRect(Rect(cx - headR * 0.55f, headTop + headR * 0.5f, cx + headR * 0.55f, headTop + headR * 0.85f))
        }
        6 -> { // afro
            path.addOval(Rect(cx - headR * 1.35f, headTop - headR * 0.55f, cx + headR * 1.35f, headTop + headR * 1.8f))
        }
        7 -> { // high ponytail
            path.addOval(Rect(cx - headR, headTop - headR * 0.05f, cx + headR, headTop + headR * 0.7f))
            drawLine(hairColor, Offset(cx, headTop - headR * 0.1f), Offset(cx + headR * 0.5f, headTop - headR * 0.8f), strokeWidth = headR * 0.22f)
        }
        else -> {
            path.addOval(Rect(cx - headR * 1.02f, headTop - headR * 0.1f, cx + headR * 1.02f, headTop + headR * 0.9f))
        }
    }
    drawPath(path, color = hairColor)
}

private fun DrawScope.drawHat(cx: Float, headTop: Float, headR: Float, hairColor: Color, hatStyle: Int) {
    when (hatStyle) {
        0 -> { // bucket hat
            drawOval(Color(0xFF555555), topLeft = Offset(cx - headR * 1.2f, headTop + headR * 0.1f), size = Size(headR * 2.4f, headR * 0.3f))
            drawRoundRect(Color(0xFF4A4A4A), topLeft = Offset(cx - headR * 0.9f, headTop - headR * 0.5f), size = Size(headR * 1.8f, headR * 0.65f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(headR * 0.15f))
        }
        1 -> { // snapback cap
            drawOval(Color(0xFF212121), topLeft = Offset(cx - headR * 1.15f, headTop + headR * 0.1f), size = Size(headR * 2.3f, headR * 0.28f))
            drawRoundRect(Color(0xFF212121), topLeft = Offset(cx - headR, headTop - headR * 0.35f), size = Size(headR * 2f, headR * 0.5f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(headR * 0.25f))
            drawOval(Color(0xFF424242), topLeft = Offset(cx - headR * 0.9f, headTop + headR * 0.12f), size = Size(headR * 1.1f, headR * 0.22f))
        }
        2 -> { // flower crown — draw circles atop head
            for (i in -2..2) {
                val fx = cx + i * headR * 0.3f
                val fy = headTop - headR * 0.1f
                val petalColor = listOf(Color(0xFFF48FB1), Color(0xFFFF9800), Color(0xFFFFD700), Color(0xFFE65100), Color(0xFFFFF176))[kotlin.math.abs(i + 2) % 5]
                drawCircle(petalColor, radius = headR * 0.14f, center = Offset(fx, fy))
            }
        }
        3 -> { // beanie
            drawOval(Color(0xFFE65100), topLeft = Offset(cx - headR * 1.02f, headTop - headR * 0.45f), size = Size(headR * 2.04f, headR * 1.0f))
            drawOval(Color(0xFFFF9800), topLeft = Offset(cx - headR * 1.0f, headTop + headR * 0.35f), size = Size(headR * 2.0f, headR * 0.2f))
            drawCircle(Color.White.copy(alpha = 0.9f), radius = headR * 0.14f, center = Offset(cx, headTop - headR * 0.38f))
        }
        4 -> { // halo
            drawOval(Color(0xFFFFD700).copy(alpha = 0.6f), topLeft = Offset(cx - headR * 0.9f, headTop - headR * 0.6f), size = Size(headR * 1.8f, headR * 0.25f), style = Stroke(width = headR * 0.1f))
        }
    }
}

private fun DrawScope.drawGlasses(cx: Float, headTop: Float, headR: Float, glassesStyle: Int) {
    val eyeY = headTop + headR * 0.85f
    val eyeOffX = headR * 0.38f
    val frameR = headR * 0.28f
    val frameColor = when (glassesStyle) {
        0 -> Color(0xFF795548)
        1 -> Color(0xFF3D3D3D)
        2 -> Color(0xFFC62828)
        3 -> Color(0xFFFF9800)
        else -> Color.Black
    }
    listOf(-eyeOffX, eyeOffX).forEach { offX ->
        val eyeCx = cx + offX
        when (glassesStyle) {
            0, 2 -> drawCircle(frameColor, radius = frameR, center = Offset(eyeCx, eyeY), style = Stroke(width = headR * 0.045f))
            1 -> { // aviators
                val p = Path().apply {
                    addOval(Rect(eyeCx - frameR, eyeY - frameR * 0.65f, eyeCx + frameR, eyeY + frameR * 0.65f))
                }
                drawPath(p, frameColor, style = Stroke(width = headR * 0.04f))
                drawPath(p, frameColor.copy(alpha = 0.18f))
            }
            3 -> { // cyber visor — full bar
                drawRoundRect(frameColor.copy(alpha = 0.7f), topLeft = Offset(cx - headR * 0.9f, eyeY - frameR * 0.45f), size = Size(headR * 1.8f, frameR * 0.9f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(frameR * 0.2f))
            }
        }
    }
    // Bridge
    if (glassesStyle != 3) {
        drawLine(frameColor, Offset(cx - eyeOffX + frameR, eyeY), Offset(cx + eyeOffX - frameR, eyeY), strokeWidth = headR * 0.03f)
    }
    // Arms
    drawLine(frameColor, Offset(cx - eyeOffX - frameR, eyeY), Offset(cx - headR * 1.0f, eyeY + headR * 0.05f), strokeWidth = headR * 0.03f)
    drawLine(frameColor, Offset(cx + eyeOffX + frameR, eyeY), Offset(cx + headR * 1.0f, eyeY + headR * 0.05f), strokeWidth = headR * 0.03f)
}
