package com.aura.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CompletedMissionRecord(
    @SerialName("id") val id: String? = null,
    @SerialName("user_wallet") val userWallet: String,
    @SerialName("title") val title: String,
    @SerialName("emoji") val emoji: String,
    @SerialName("aura_reward") val auraReward: Int,
    @SerialName("ai_feedback") val aiFeedback: String,
    @SerialName("completed_at_millis") val completedAtMillis: Long
)
