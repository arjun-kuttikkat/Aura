package com.aura.app.model

data class Listing(
    val id: String,
    val sellerWallet: String,
    val title: String,
    val description: String = "",
    val priceLamports: Long,
    val images: List<String>,
    val mintedStatus: MintedStatus,
    val mintAddress: String?,
    val fingerprintHash: String,
    val condition: String,
    val createdAt: Long,
    // ── Regional Marketplace & Permanent Listings ──
    val latitude: Double? = null,
    val longitude: Double? = null,
    val location: String? = null,     // Human-readable e.g. "Dubai - Downtown"
    val soldAt: Long? = null,         // Null = active listing, non-null = archived permanent record
    val buyerWallet: String? = null,  // Set on sale completion
    val distanceMeters: Int? = null,  // Calculated client-side from user GPS
    val sellerAuraScore: Int = 50,    // Cached for AI Trade-Risk Oracle display
    val emirate: String? = null,      // UAE emirate this listing belongs to
    val isPromoted: Boolean = false,
    val promotedUntil: Long? = null,
    val promotedAt: Long? = null,
    /** SUN URL from NFC tap at publish; used to verify same physical tag at meetup. */
    val nfcSunUrl: String? = null,
    /** Seller-preferred meetup radius in meters (20–500). Default 50. */
    val meetupRadiusMeters: Int? = null,
)

enum class MintedStatus {
    PENDING,
    MINTED,
    VERIFIED,
    SOLD,
}
