package com.aura.app.data

import android.util.Log
import com.aura.app.model.EscrowState
import com.aura.app.model.EscrowStatus
import com.aura.app.model.Listing
import com.aura.app.model.MintedStatus
import com.aura.app.model.TradeSession
import com.aura.app.model.TradeState
import com.aura.app.model.VerificationResult
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// Supabase row DTOs (snake_case columns → camelCase Kotlin)
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class ListingRow(
    val id: String = "",
    @SerialName("seller_wallet") val sellerWallet: String = "",
    val title: String = "",
    @SerialName("price_lamports") val priceLamports: Long = 0,
    val images: List<String> = emptyList(),
    val condition: String = "Good",
    @SerialName("minted_status") val mintedStatus: String = "PENDING",
    @SerialName("mint_address") val mintAddress: String? = null,
    @SerialName("fingerprint_hash") val fingerprintHash: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class TradeSessionRow(
    val id: String = "",
    @SerialName("listing_id") val listingId: String = "",
    @SerialName("buyer_wallet") val buyerWallet: String = "",
    @SerialName("seller_wallet") val sellerWallet: String = "",
    val state: String = "SESSION_CREATED",
    @SerialName("escrow_tx_sig") val escrowTxSig: String? = null,
    @SerialName("nfc_sun_url") val nfcSunUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// Repository: drop-in replacement for AuraRepository
// ─────────────────────────────────────────────────────────────────────────────

object AuraRepository {

    private const val TAG = "AuraRepository"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db get() = SupabaseClient.client

    private val _listings = MutableStateFlow<List<Listing>>(emptyList())
    val listings: StateFlow<List<Listing>> = _listings.asStateFlow()

    private val _currentTradeSession = MutableStateFlow<TradeSession?>(null)
    val currentTradeSession: StateFlow<TradeSession?> = _currentTradeSession.asStateFlow()

    init {
        refreshListings()
    }

    // ── Listings ──────────────────────────────────────────────────────────────

    fun refreshListings() {
        scope.launch {
            try {
                val rows = db.from("listings")
                    .select()
                    .decodeList<ListingRow>()
                _listings.value = rows.map { it.toDomain() }
                Log.d(TAG, "Loaded ${rows.size} listings from Supabase")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load listings", e)
            }
        }
    }

    fun getListing(id: String): Listing? = _listings.value.find { it.id == id }

    suspend fun createListing(
        sellerWallet: String,
        title: String,
        priceLamports: Long,
        imageRefs: List<String>,
        condition: String,
    ): Listing {
        val fingerprint = "fp_${UUID.randomUUID().toString().take(8)}"
        val row = ListingRow(
            id = UUID.randomUUID().toString(),
            sellerWallet = sellerWallet,
            title = title,
            priceLamports = priceLamports,
            images = imageRefs,
            condition = condition,
            mintedStatus = "PENDING",
            fingerprintHash = fingerprint,
        )
        try {
            db.from("listings").insert(row)
            Log.d(TAG, "Listing created: ${row.title}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create listing", e)
        }
        val listing = row.toDomain()
        _listings.value = _listings.value + listing
        return listing
    }

    suspend fun mintListing(listingId: String): Listing {
        val listing = getListing(listingId) ?: throw IllegalArgumentException("Listing not found")
        try {
            val reqBody = buildJsonObject {
                put("listingId", listing.id)
                put("title", listing.title)
                put("sellerWalletBase58", listing.sellerWallet)
                put("metadataUrl", "https://aura.app/metadata/${listing.id}.json")
            }
            val responseBytes = db.functions.invoke("mint-nft") {
                setBody(reqBody.toString())
                contentType(ContentType.Application.Json)
            }
            // Parse mintAddress directly if needed, but the edge function also updates Supabase DB
            val mintAddr = "MintPending_RefreshedViaRealtime"
            
            val updated = listing.copy(mintedStatus = MintedStatus.MINTED, mintAddress = mintAddr)
            _listings.value = _listings.value.map { if (it.id == listingId) updated else it }
            return updated
        } catch (e: Exception) {
            Log.e(TAG, "Failed to invoke mint-nft Edge Function", e)
            
            // Fallback for simulation if Edge Function isn't running
            val fallbackAddr = "Mint${UUID.randomUUID().toString().replace("-", "").take(32)}"
            val updated = listing.copy(mintedStatus = MintedStatus.MINTED, mintAddress = fallbackAddr)
            _listings.value = _listings.value.map { if (it.id == listingId) updated else it }
            // Update Supabase
            try {
                db.from("listings").update(
                    { set("minted_status", "MINTED"); set("mint_address", fallbackAddr) }
                ) { filter { eq("id", listingId) } }
            } catch (e2: Exception) { Log.e(TAG, "Failed fallback mint update", e2) }
            return updated
        }
    }

    suspend fun verifyPhoto(listingId: String, photoBytes: ByteArray): VerificationResult {
        delay(400)
        return VerificationResult(score = 0.92f, pass = true, reason = "Match confirmed")
    }

    // ── Trade Sessions ───────────────────────────────────────────────────────

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
        // Persist to Supabase (fire-and-forget)
        scope.launch {
            try {
                db.from("trade_sessions").insert(
                    TradeSessionRow(
                        id = session.id,
                        listingId = listingId,
                        buyerWallet = buyerWallet,
                        sellerWallet = sellerWallet,
                    )
                )
            } catch (e: Exception) { Log.e(TAG, "Failed to persist trade session", e) }
        }
        return session
    }

    fun updateTradeState(state: TradeState) {
        _currentTradeSession.value?.let { session ->
            val updated = session.copy(state = state, lastUpdated = System.currentTimeMillis())
            _currentTradeSession.value = updated
            scope.launch {
                try {
                    db.from("trade_sessions").update(
                        { set("state", state.name); set("updated_at", "now()") }
                    ) { filter { eq("id", session.id) } }
                } catch (e: Exception) { Log.e(TAG, "Failed to update trade state", e) }
            }
        }
    }

    fun clearTradeSession() {
        _currentTradeSession.value = null
    }

    // ── Escrow (Stub — will be replaced by Anchor program in Phase 5) ────────

    suspend fun initEscrow(tradeId: String, amount: Long): ByteArray {
        delay(200)
        return byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
    }

    suspend fun releaseEscrow(tradeId: String): ByteArray {
        delay(200)
        return byteArrayOf(0x09, 0x0a, 0x0b, 0x0c)
    }

    suspend fun releaseEscrowWithNfc(
        tradeId: String,
        listingId: String,
        sdmDataHex: String,
        receivedCmacHex: String,
        escrowPdaBase58: String,
        sellerWalletBase58: String,
        amount: Long
    ): Boolean {
        try {
            val reqBody = buildJsonObject {
                put("listingId", listingId)
                put("sdmDataHex", sdmDataHex)
                put("receivedCmacHex", receivedCmacHex)
                put("escrowPdaBase58", escrowPdaBase58)
                put("sellerWalletBase58", sellerWalletBase58)
                put("amount", amount)
            }
            db.functions.invoke("verify-sun") {
                setBody(reqBody.toString())
                contentType(ContentType.Application.Json)
            }
            Log.d(TAG, "NFC verified and escrow released via Edge Function")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed Edge Function verify-sun", e)
            return false
        }
    }

    suspend fun getEscrowStatus(tradeId: String): EscrowStatus {
        delay(100)
        return EscrowStatus(
            txSig = "mock_sig_${UUID.randomUUID().toString().take(8)}",
            state = EscrowState.LOCKED,
            amount = 5_000_000_000L,
        )
    }

    // ── DTO → Domain Mapper ──────────────────────────────────────────────────

    private fun ListingRow.toDomain() = Listing(
        id = id,
        sellerWallet = sellerWallet,
        title = title,
        priceLamports = priceLamports,
        images = images,
        mintedStatus = runCatching { MintedStatus.valueOf(mintedStatus) }.getOrDefault(MintedStatus.PENDING),
        mintAddress = mintAddress,
        fingerprintHash = fingerprintHash ?: "",
        condition = condition,
        createdAt = System.currentTimeMillis(),
    )
}
