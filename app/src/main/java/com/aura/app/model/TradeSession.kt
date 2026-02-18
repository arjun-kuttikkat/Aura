package com.aura.app.model

data class TradeSession(
    val id: String,
    val listingId: String,
    val buyerWallet: String,
    val sellerWallet: String,
    val state: TradeState,
    val createdAt: Long,
    val lastUpdated: Long,
)
