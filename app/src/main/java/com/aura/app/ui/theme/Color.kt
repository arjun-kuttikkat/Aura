package com.aura.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ── Aura Web3 P2P Palette ───────────────────────────────────────────────
val UltraViolet = Color(0xFF5E00D7)
val SolanaGreen = Color(0xFF14F195)
val DarkVoid = Color(0xFF0F172A)
val SlateElevated = Color(0xFF1E293B)
val RadicalRed = Color(0xFFFF3B30)

// ── Lighter / Darker Tone Variations (calculated) ───────────────────────
val UltraVioletLight = Color(0xFF7E33E0)
val SolanaGreenLight = Color(0xFF67F5B6)
val SlateLight = Color(0xFF334155)

val DarkVoidVariant = Color(0xFF0B1120) // Deepest background

// ── Theming Surfaces ────────────────────────────────────────────────────
val DarkSurface = DarkVoid
val DarkSurfaceVariant = SlateElevated
val DarkOnSurface = Color(0xFFF8FAFC)
val DarkOnSurfaceVariant = Color(0xFF94A3B8)

// We maintain Light themes as dark mode defaults just in case
val LightSurface = Color(0xFFF1F5F9)
val LightSurfaceVariant = Color(0xFFE2E8F0)
val LightOnSurface = Color(0xFF0F172A)
val LightOnSurfaceVariant = Color(0xFF475569)

// ── Glassmorphism & Borders ─────────────────────────────────────────────
val GlassSurface = Color.White.copy(alpha = 0.08f)
val GlassBorder = SlateLight

// ── Semantic & Brand Colors ──────────────────────────────────────────────────
val SuccessGreen = SolanaGreen
val ErrorRed = RadicalRed
val SolanaGradientStart = Color(0xFF9945FF)
val SolanaGradientEnd = SolanaGreen

val Orange500 = Color(0xFFFF9800)
val Gold500 = Color(0xFFFFD700)

// ── Gradient Presets ────────────────────────────────────────────────────
val AuraGradient = Brush.linearGradient(listOf(UltraViolet, SolanaGradientStart))
val SolanaGradient = Brush.linearGradient(listOf(SolanaGradientStart, SolanaGradientEnd))
val PremiumDarkGradient = Brush.verticalGradient(
    listOf(DarkVoidVariant, DarkVoid),
)
