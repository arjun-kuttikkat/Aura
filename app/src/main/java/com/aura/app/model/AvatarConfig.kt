package com.aura.app.model

import androidx.compose.ui.graphics.Color

// ── Avatar Configuration ───────────────────────────────────────────────────────

data class AvatarConfig(
    // Base appearance
    val skinTone: Int       = 0,   // index into SKIN_TONES
    val eyeShape: Int       = 0,   // index into EYE_SHAPES
    val eyeColor: Int       = 0,   // index into EYE_COLORS
    val eyebrowStyle: Int   = 0,
    val noseStyle: Int      = 0,
    val mouthStyle: Int     = 0,
    val faceShape: Int      = 0,

    // Hair
    val hairStyle: Int      = 0,   // index into HAIR_STYLES
    val hairColor: Int      = 0,   // index into HAIR_COLORS

    // Outfit
    val outfitTop: Int      = 0,   // index into OUTFIT_TOPS
    val outfitBottom: Int   = 0,
    val outfitColor: Int    = 0,

    // Accessories (store-unlocked or default)
    val hat: Int            = -1,  // -1 = none
    val glasses: Int        = -1,
    val earring: Int        = -1,

    // Background
    val background: Int     = 0,   // index into BACKGROUNDS

    // Expression
    val expression: Int     = 0,   // idle, smile, wink, cool, surprised

    // Store-unlocked item IDs equipped
    val equippedItems: Set<String> = emptySet()
)

// ── Catalog Constants ─────────────────────────────────────────────────────────

object AvatarCatalog {
    val SKIN_TONES = listOf(
        Color(0xFFFDDDB4), // light
        Color(0xFFF5C5A3), // light-medium
        Color(0xFFD99E74), // medium
        Color(0xFFB07040), // medium-dark
        Color(0xFF7D4E28), // dark
        Color(0xFF4A2C12), // deep
    )

    val HAIR_COLORS = listOf(
        Color(0xFF1C1208), // black
        Color(0xFF5C3317), // dark brown
        Color(0xFF80450A), // brown
        Color(0xFFC68642), // light brown
        Color(0xFFD4AF37), // blonde
        Color(0xFFE8D5A3), // light blonde
        Color(0xFFBE2C2C), // red
        Color(0xFF9C27B0), // purple
        Color(0xFF1A1A1A), // charcoal
        Color(0xFF262626), // charcoal
        Color(0xFFE0E0E0), // silver/grey
    )

    val EYE_COLORS = listOf(
        Color(0xFF3E2723), // dark brown
        Color(0xFF795548), // brown
        Color(0xFF388E3C), // green
        Color(0xFF1A1A1A), // charcoal
        Color(0xFF4A4A4A), // neutral grey
        Color(0xFF8D6E63), // hazel
    )

    val BACKGROUNDS = listOf(
        Color(0xFF121212), // deep void
        Color(0xFF0F0F0F), // midnight black
        Color(0xFF0A0A0A), // pure black
        Color(0xFF1F1F1F), // dark grey
        Color(0xFF1B4332), // forest
        Color(0xFF5C4033), // warm earth
        Color(0xFF2D3436), // dark grey
        Color(0xFF1A1A1A), // pure black
        Color(0xFF9B2335), // crimson
    )

    // Counts of each variant
    const val HAIR_STYLE_COUNT  = 8
    const val EYE_SHAPE_COUNT   = 6
    const val EYEBROW_COUNT     = 5
    const val NOSE_COUNT        = 4
    const val MOUTH_COUNT       = 6
    const val FACE_SHAPE_COUNT  = 5
    const val OUTFIT_TOP_COUNT  = 8
    const val OUTFIT_BOTTOM_COUNT = 6
    const val HAT_COUNT         = 5  // -1 = none, 0-4 = hats
    const val GLASSES_COUNT     = 4
    const val EXPRESSION_COUNT  = 5
}
