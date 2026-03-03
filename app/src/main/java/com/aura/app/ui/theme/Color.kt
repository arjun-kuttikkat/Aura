package com.aura.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ── AURA Premium Color System (DO NOT deviate) ──────────────────────────
val Orange500 = Color(0xFFFF6B00)   // primary fire accent
val Gold500 = Color(0xFFFFB800)     // reward/premium accent
val AuraPulse = Color(0xFFFF8C00)   // animated glow color
val DarkBase = Color(0xFF0A0A0A)    // near-black background
val DarkCard = Color(0xFF111111)    // card background
val DarkGlass = Color(0x0AFFFFFF)   // rgba(255,255,255,0.04) glass surfaces
val DarkBorder = Color(0x26FF6B00)  // rgba(255,107,0,0.15) subtle orange borders
val SuccessGreen = Color(0xFF00E676)
val ErrorRed = Color(0xFFFF3D00)
val SolanaPurple = Color(0xFF9945FF) // Solana co-branding

// ── Extended palette ────────────────────────────────────────────────────
val Gold900 = Color(0xFFB8860B)
val Gold700 = Color(0xFFD4A017)
val Gold300 = Color(0xFFFFD54F)
val Gold100 = Color(0xFFFFECB3)
val Gold50 = Color(0xFFFFF8E1)
val Orange900 = Color(0xFFE65100)
val Orange700 = Color(0xFFF57C00)
val Orange300 = Color(0xFFFFB74D)
val Orange100 = Color(0xFFFFE0B2)
val Orange50 = Color(0xFFFFF3E0)

// ── Dark Theme Surfaces ─────────────────────────────────────────────────
val DarkSurface = DarkBase
val DarkSurfaceVariant = Color(0xFF1A1A1A)
val DarkOnSurface = Color(0xFFF5F5F5)
val DarkOnSurfaceVariant = Color(0xFFB0B0B0)

// ── Light Theme Surfaces ────────────────────────────────────────────────
val LightSurface = Color(0xFFFFFBF7)
val LightSurfaceVariant = Color(0xFFFFF3E8)
val LightOnSurface = Color(0xFF1C1917)
val LightOnSurfaceVariant = Color(0xFF5C5349)

// ── Glassmorphism ───────────────────────────────────────────────────────
val GlassSurface = DarkGlass
val GlassBorder = DarkBorder
val GlassSurfaceLight = Color.Black.copy(alpha = 0.04f)
val GlassBorderLight = Color.Black.copy(alpha = 0.08f)

// ── Semantic Colors ─────────────────────────────────────────────────────
val SolanaGradientStart = SolanaPurple
val SolanaGradientEnd = Color(0xFF14F195)

// ── Gradient Presets ────────────────────────────────────────────────────
val AuraGradient = Brush.linearGradient(listOf(Orange500, Gold500))
val AuraGradientVertical = Brush.verticalGradient(listOf(Orange500, Gold500))
val SolanaGradient = Brush.linearGradient(listOf(SolanaGradientStart, SolanaGradientEnd))
val GoldShimmer = Brush.linearGradient(listOf(Gold300, Gold500, Gold300))
val PremiumDarkGradient = Brush.verticalGradient(
    listOf(Color(0xFF1A1A2E), DarkBase),
)
