package com.aura.app.model

data class Listing(
    val id: String,
    val sellerWallet: String,
    val title: String,
    val priceLamports: Long,
    val images: List<String>,
    val mintedStatus: MintedStatus,
    val mintAddress: String?,
    val fingerprintHash: String,
    val condition: String,
    val createdAt: Long,
)

enum class MintedStatus {
    PENDING,
    MINTED,
    VERIFIED,
}
