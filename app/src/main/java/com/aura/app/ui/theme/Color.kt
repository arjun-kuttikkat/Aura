package com.aura.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ── AURA Premium Color System + Web3 P2P Palette ─────────────────────────
val Orange500 = Color(0xFFFF6B00)   // primary fire accent
val Gold500 = Color(0xFFFFB800)     // reward/premium accent
val AuraPulse = Color(0xFFFF8C00)   // animated glow color
val DarkBase = Color(0xFF0A0A0A)    // near-black background
val DarkCard = Color(0xFF111111)    // card background
val DarkGlass = Color(0x0AFFFFFF)   // rgba(255,255,255,0.04) glass surfaces
val DarkBorder = Color(0x26FF6B00)  // rgba(255,107,0,0.15) subtle orange borders
val SolanaPurple = Color(0xFF9945FF) // Solana co-branding

// Web3 P2P palette
val UltraViolet = Color(0xFF5E00D7)
val SolanaGreen = Color(0xFF14F195)
val DarkVoid = Color(0xFF0F172A)
val SlateElevated = Color(0xFF1E293B)
val RadicalRed = Color(0xFFFF3B30)

// Extended palette
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
val UltraVioletLight = Color(0xFF7E33E0)
val SolanaGreenLight = Color(0xFF67F5B6)
val SlateLight = Color(0xFF334155)
val DarkVoidVariant = Color(0xFF0B1120)

// ── Theming Surfaces (Aura orange/black) ────────────────────────────────
val DarkSurface = DarkBase
val DarkSurfaceVariant = DarkCard
val DarkOnSurface = Color(0xFFF8FAFC)
val DarkOnSurfaceVariant = Color(0xFF94A3B8)

// Light themes
val LightSurface = Color(0xFFF1F5F9)
val LightSurfaceVariant = Color(0xFFE2E8F0)
val LightOnSurface = Color(0xFF0F172A)
val LightOnSurfaceVariant = Color(0xFF475569)

// ── Glassmorphism & Borders (Aura) ───────────────────────────────────────
val GlassSurface = Color.White.copy(alpha = 0.06f)
val GlassBorder = Orange500.copy(alpha = 0.25f)
val GlassSurfaceLight = Color.Black.copy(alpha = 0.04f)
val GlassBorderLight = Color.Black.copy(alpha = 0.08f)

// ── Semantic Colors (Aura) ──────────────────────────────────────────────
val SuccessGreen = Gold500
val ErrorRed = RadicalRed
val SolanaGradientStart = Color(0xFF9945FF)
val SolanaGradientEnd = Gold500

// ── Gradient Presets (Aura) ─────────────────────────────────────────────
val AuraGradient = Brush.linearGradient(listOf(Orange500, Gold500))
val SolanaGradient = Brush.linearGradient(listOf(SolanaGradientStart, SolanaGradientEnd))
val PremiumDarkGradient = Brush.verticalGradient(
    listOf(DarkVoidVariant, DarkVoid),
)
