package com.aura.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuraHistoryDto(
    @SerialName("id") val id: Long? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("change_amount") val changeAmount: Int,
    @SerialName("reason") val reason: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)
