package com.aura.app.model

data class TradeSession(
    val id: String,
    val listingId: String,
    val buyerWallet: String,
    val sellerWallet: String,
    val state: TradeState,
    val createdAt: Long,
    val lastUpdated: Long,
    /** Receipt NFT mint (buyer's copy) — minted after escrow release */
    val receiptMintBuyer: String? = null,
    /** Receipt NFT mint (seller's copy) — minted after escrow release */
    val receiptMintSeller: String? = null,
)
