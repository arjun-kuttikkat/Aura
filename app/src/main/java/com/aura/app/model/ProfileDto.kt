package com.aura.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    @SerialName("id") val id: String? = null,
    @SerialName("wallet_address") val walletAddress: String,
    @SerialName("aura_score") val auraScore: Int = 50,
    @SerialName("streak_days") val streakDays: Int = 0,
    @SerialName("last_scan_at") val lastScanAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
