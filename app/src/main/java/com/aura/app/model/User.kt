package com.aura.app.model

data class User(
    val walletPubKey: String,
    val trustScore: Int, // 0-100
    val tier: TrustTier,
    val displayName: String,
)

enum class TrustTier {
    NEW,
    BRONZE,
    SILVER,
    GOLD,
    PLATINUM,
}
