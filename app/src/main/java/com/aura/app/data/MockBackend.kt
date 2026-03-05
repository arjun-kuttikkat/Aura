package com.aura.app.data

import com.aura.app.model.EscrowState
import com.aura.app.model.EscrowStatus
import com.aura.app.model.Listing
import com.aura.app.model.MintedStatus
import com.aura.app.model.TradeSession
import com.aura.app.model.TradeState
import com.aura.app.model.VerificationResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object MockBackend {

    private val _listings = MutableStateFlow<List<Listing>>(emptyList())
    val listings: StateFlow<List<Listing>> = _listings.asStateFlow()

    private val _currentTradeSession = MutableStateFlow<TradeSession?>(null)
    val currentTradeSession: StateFlow<TradeSession?> = _currentTradeSession.asStateFlow()

    init {
        _listings.value = listOf(
            Listing(
                id = "1",
                sellerWallet = "SELLER_WALLET_111",
                title = "Vintage Camera",
                priceLamports = 5_000_000_000L, // 5 SOL
                images = listOf("https://images.unsplash.com/photo-1516035069371-29a1b244cc32?w=400"),
                mintedStatus = MintedStatus.MINTED,
                mintAddress = "Mint11111111111111111111111111111111",
                fingerprintHash = "abc123",
                condition = "Good",
                createdAt = System.currentTimeMillis() - 86400_000,
            ),
            Listing(
                id = "2",
                sellerWallet = "SELLER_WALLET_222",
                title = "Gaming Console",
                priceLamports = 2_000_000_000L,
                images = listOf("https://images.unsplash.com/photo-1606144042614-b2417e99c4e3?w=400"),
                mintedStatus = MintedStatus.VERIFIED,
                mintAddress = "Mint22222222222222222222222222222222",
                fingerprintHash = "def456",
                condition = "Like New",
                createdAt = System.currentTimeMillis() - 43200_000,
            ),
            Listing(
                id = "3",
                sellerWallet = "SELLER_WALLET_333",
                title = "Designer Watch",
                priceLamports = 8_500_000_000L,
                images = listOf("https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=400"),
                mintedStatus = MintedStatus.VERIFIED,
                mintAddress = "Mint33333333333333333333333333333333",
                fingerprintHash = "ghi789",
                condition = "Mint",
                createdAt = System.currentTimeMillis() - 36000_000,
            ),
        )
    }

    suspend fun createListing(
        sellerWallet: String,
        title: String,
        priceLamports: Long,
        imageRefs: List<String>,
        condition: String,
    ): Listing {
        delay(500)
        val fingerprint = "fp_${UUID.randomUUID().toString().take(8)}"
        val listing = Listing(
            id = UUID.randomUUID().toString(),
            sellerWallet = sellerWallet,
            title = title,
            priceLamports = priceLamports,
            images = imageRefs,
            mintedStatus = MintedStatus.PENDING,
            mintAddress = null,
            fingerprintHash = fingerprint,
            condition = condition,
            createdAt = System.currentTimeMillis(),
        )
        _listings.value = _listings.value + listing
        return listing
    }

    suspend fun mintListing(listingId: String): Listing {
        delay(300)
        val listing = _listings.value.find { it.id == listingId } ?: throw IllegalArgumentException("Listing not found")
        val updated = listing.copy(
            mintedStatus = MintedStatus.MINTED,
            mintAddress = "Mint${UUID.randomUUID().toString().replace("-", "").take(32)}",
        )
        _listings.value = _listings.value.map { if (it.id == listingId) updated else it }
        return updated
    }

    suspend fun verifyPhoto(listingId: String, photoBytes: ByteArray): VerificationResult {
        delay(400)
        // MVP: always pass with mock score
        return VerificationResult(
            score = 0.92f,
            pass = true,
            reason = "Match confirmed (mock)",
        )
    }

    suspend fun initEscrow(tradeId: String, amount: Long): ByteArray {
        delay(200)
        // Return placeholder tx bytes for MWA to sign
        return byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
    }

    suspend fun releaseEscrow(tradeId: String): ByteArray {
        delay(200)
        return byteArrayOf(0x09, 0x0a, 0x0b, 0x0c)
    }

    suspend fun getEscrowStatus(tradeId: String): EscrowStatus {
        delay(100)
        return EscrowStatus(
            txSig = "mock_sig_${UUID.randomUUID().toString().take(8)}",
            state = EscrowState.LOCKED,
            amount = 5_000_000_000L,
        )
    }

    fun createTradeSession(listingId: String, buyerWallet: String, sellerWallet: String): TradeSession {
        val session = TradeSession(
            id = UUID.randomUUID().toString(),
            listingId = listingId,
            buyerWallet = buyerWallet,
            sellerWallet = sellerWallet,
            state = TradeState.SESSION_CREATED,
            createdAt = System.currentTimeMillis(),
            lastUpdated = System.currentTimeMillis(),
        )
        _currentTradeSession.value = session
        return session
    }

    fun updateTradeState(state: TradeState) {
        _currentTradeSession.value?.let { session ->
            _currentTradeSession.value = session.copy(
                state = state,
                lastUpdated = System.currentTimeMillis(),
            )
        }
    }

    fun clearTradeSession() {
        _currentTradeSession.value = null
    }

    fun getListing(id: String): Listing? = _listings.value.find { it.id == id }
}
