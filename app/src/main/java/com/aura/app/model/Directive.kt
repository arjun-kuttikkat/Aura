package com.aura.app.model

/**
 * Hardware-bound Directives — daily missions that force Seeker hardware interaction
 * and maintain the user's Aura Core from degrading.
 */
enum class DirectiveType {
    /** Panoramic camera scan of a Hotzone to verify safety via edge AI. */
    SPATIAL_SWEEP,
    /** NFC-tap attestation for a stranger's high-value trade. */
    GUARDIAN_WITNESS,
    /** 108MP macro texture scan of a listing item for hardware verification. */
    TEXTURE_ARCHIVE,
}

data class Directive(
    val id: String,
    val type: DirectiveType,
    val title: String,
    val description: String,
    val rewardAura: Int,        // Aura score points earned on completion
    val expiresAt: Long,        // Epoch millis — Core degrades if expired uncompleted
    val isCompleted: Boolean,
    val hotzoneId: String?,     // Linked zone if localized
    val hotzoneLabel: String?,  // Human label for the linked zone
)
