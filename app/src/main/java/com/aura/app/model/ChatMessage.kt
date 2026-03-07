package com.aura.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    @SerialName("id") val id: String = "",
    @SerialName("listing_id") val listingId: String,
    @SerialName("sender_wallet") val senderWallet: String,
    @SerialName("receiver_wallet") val receiverWallet: String,
    @SerialName("content") val content: String,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
)
