package com.aura.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ── Aura Ultra Premium Palette (Black + Orange) ──────────────────────────
val Orange500 = Color(0xFFFF9800)
val Orange700 = Color(0xFFE65100)
val Gold500 = Color(0xFFFFD700)
val DarkVoid = Color(0xFF0A0A0A)
val SlateElevated = Color(0xFF141414)
val RadicalRed = Color(0xFFFF3B30)

// ── Brand aliases (orange-centric) ───────────────────────────────────────
val UltraViolet = Orange500
val SolanaGreen = Orange500
val UltraVioletLight = Gold500
val SolanaGreenLight = Gold500
val SlateLight = Color(0xFF2D2D2D)

val DarkVoidVariant = Color(0xFF080808)

// ── Theming Surfaces ────────────────────────────────────────────────────
val DarkSurface = DarkVoid
val DarkSurfaceVariant = SlateElevated
val DarkOnSurface = Color(0xFFF8FAFC)
val DarkOnSurfaceVariant = Color(0xFF94A3B8)
val DarkCard = Color(0xFF141414)
val DarkBase = DarkVoid

// Light theme fallbacks
val LightSurface = Color(0xFFF1F5F9)
val LightSurfaceVariant = Color(0xFFE2E8F0)
val LightOnSurface = Color(0xFF0A0A0A)
val LightOnSurfaceVariant = Color(0xFF404040)

// ── Glassmorphism & Borders ─────────────────────────────────────────────
val GlassSurface = Color.White.copy(alpha = 0.05f)
val GlassBorder = SlateLight

// ── Semantic & Brand Colors ──────────────────────────────────────────────
val SuccessGreen = Orange500
val ErrorRed = RadicalRed
val SolanaGradientStart = Orange500
val SolanaGradientEnd = Gold500

// ── Gradient Presets (Aura Orange) ───────────────────────────────────────
val AuraGradient = Brush.linearGradient(listOf(Orange500, Gold500))
val SolanaGradient = Brush.linearGradient(listOf(Orange500, Gold500))
val PremiumDarkGradient = Brush.verticalGradient(
    listOf(DarkVoidVariant, DarkVoid),
)
