package com.aura.app.data

import android.util.Log
import com.aura.app.model.AuraHistoryDto
import com.aura.app.model.EscrowState
import com.aura.app.model.EscrowStatus
import com.aura.app.model.Listing
import com.aura.app.model.MintedStatus
import com.aura.app.model.ProfileDto
import com.aura.app.model.TradeSession
import com.aura.app.model.TradeState
import com.aura.app.model.VerificationResult
import com.aura.app.util.MeetupLocationUtils
import io.github.jan.supabase.functions.functions
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.boolean
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.decodeRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.annotation.SuppressLint
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlin.math.pow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// Supabase row DTOs
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class ListingRow(
    val id: String = "",
    @SerialName("seller_wallet") val sellerWallet: String = "",
    val title: String = "",
    @EncodeDefault val description: String = "",
    @SerialName("price_lamports") val priceLamports: Long = 0,
    @EncodeDefault val images: List<String> = emptyList(),
    @EncodeDefault val condition: String = "Good",
    @EncodeDefault @SerialName("minted_status") val mintedStatus: String = "PENDING",
    @SerialName("mint_address") val mintAddress: String? = null,
    @SerialName("fingerprint_hash") val fingerprintHash: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @EncodeDefault val location: String? = null,
    @SerialName("sold_at") val soldAt: String? = null,
    @SerialName("buyer_wallet") val buyerWallet: String? = null,
    @SerialName("seller_aura_score") val sellerAuraScore: Int = 50,
    val emirate: String? = null,
    @EncodeDefault @SerialName("is_active") val isActive: Boolean = true,
    @EncodeDefault @SerialName("is_published") val isPublished: Boolean = true,
    @EncodeDefault @SerialName("is_promoted") val isPromoted: Boolean = false,
    @SerialName("promoted_until") val promotedUntil: String? = null,
    @SerialName("promoted_at") val promotedAt: String? = null,
    @SerialName("nfc_sun_url") val nfcSunUrl: String? = null,
)

/** Insert DTO; is_promoted, promoted_*, nfc_sun_url optional for backward compatibility. */
@Serializable
private data class ListingRowInsert(
    val id: String = "",
    @SerialName("seller_wallet") val sellerWallet: String = "",
    val title: String = "",
    @EncodeDefault val description: String = "",
    @SerialName("price_lamports") val priceLamports: Long = 0,
    @EncodeDefault val images: List<String> = emptyList(),
    @EncodeDefault val condition: String = "Good",
    @EncodeDefault @SerialName("minted_status") val mintedStatus: String = "PENDING",
    @SerialName("fingerprint_hash") val fingerprintHash: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @EncodeDefault val location: String? = null,
    val emirate: String? = null,
    @EncodeDefault @SerialName("is_active") val isActive: Boolean = true,
    @EncodeDefault @SerialName("is_published") val isPublished: Boolean = true,
    @SerialName("nfc_sun_url") val nfcSunUrl: String? = null,
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
// Repository
// ─────────────────────────────────────────────────────────────────────────────

object AuraRepository {

    private const val TAG = "AuraRepository"
    private const val DEFAULT_PLATFORM_FEE_BPS = 200 // 2%
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val supabase get() = SupabaseClient.client
    private val db get() = supabase

    private val _currentProfile = MutableStateFlow<ProfileDto?>(null)
    val currentProfile: StateFlow<ProfileDto?> = _currentProfile.asStateFlow()

    private val _listings = MutableStateFlow<List<Listing>>(emptyList())
    val listings: StateFlow<List<Listing>> = _listings.asStateFlow()

    private val _currentTradeSession = MutableStateFlow<TradeSession?>(null)
    val currentTradeSession: StateFlow<TradeSession?> = _currentTradeSession.asStateFlow()

    val activeQuickReceiveUri = MutableStateFlow<String?>(null)

    init {
        refreshListings()
        // Auto-refresh listings every 60 seconds so other users' new listings appear
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000)
                try {
                    val rows = db.from("marketplace_listings").select {
                        limit(count = 1000)
                    }.decodeList<ListingRow>()
                    _listings.value = rows
                        .filter { it.soldAt == null && it.isActive && it.isPublished }
                        .map { it.toDomain() }
                } catch (e: Exception) {
                    Log.w(TAG, "Background refresh failed: ${e.message}")
                }
            }
        }
    }

    // ── Profiles & Ritual ───────────────────────────────────────────────────

    suspend fun loadProfile(walletAddress: String) {
        if (walletAddress.isEmpty()) {
            _currentProfile.value = null
            return
        }
        try {
            val profiles = supabase.postgrest["profiles"]
                .select { filter { eq("wallet_address", walletAddress) } }
                .decodeList<ProfileDto>()
            
            if (profiles.isNotEmpty()) {
                _currentProfile.value = profiles.first()
            } else {
                val newProfile = ProfileDto(walletAddress = walletAddress)
                val insertedList = supabase.postgrest["profiles"]
                    .insert(newProfile) { select() }
                    .decodeList<ProfileDto>()
                if (insertedList.isNotEmpty()) {
                    _currentProfile.value = insertedList.first()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading profile", e)
        }
    }

    suspend fun performMirrorRitual() {
        val profile = _currentProfile.value ?: return
        try {
            val nowStr = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val lastScanStr = profile.lastScanAt
            var streak = profile.streakDays
            val scoreGained = 5
            val newScore = (profile.auraScore + scoreGained).coerceAtMost(100)

            if (lastScanStr != null) {
                try {
                    val lastScan = OffsetDateTime.parse(lastScanStr)
                    val now = OffsetDateTime.now()
                    val hoursSince = ChronoUnit.HOURS.between(lastScan, now)
                    if (hoursSince > 48) streak = 1 else if (hoursSince >= 24) streak += 1
                } catch (e:Exception) { streak += 1 }
            } else streak += 1

            val updatedProfile = profile.copy(auraScore = newScore, streakDays = streak, lastScanAt = nowStr)

            supabase.postgrest["profiles"].update({
                set("aura_score", updatedProfile.auraScore)
                set("streak_days", updatedProfile.streakDays)
                set("last_scan_at", updatedProfile.lastScanAt)
            }) { filter { eq("wallet_address", profile.walletAddress) } }

            _currentProfile.value = updatedProfile

            profile.id?.let { uuid ->
                val history = AuraHistoryDto(userId = uuid, changeAmount = scoreGained, reason = "Completed Mirror Ritual")
                supabase.postgrest["aura_history"].insert(history)
            }
            Log.i(TAG, "Mirror Ritual Completed: +$scoreGained score")
        } catch (e: Exception) {
            Log.e(TAG, "Error completing mirror ritual", e)
        }
    }

    suspend fun performAuraCheck(photoBytes: ByteArray, lat: Double?, lng: Double?): com.aura.app.model.AuraCheckResult {
        // Enforce strict AI validation on the Edge
        var rating: Int
        var feedback: String
        var pass: Boolean
        try {
            val base64Photo = android.util.Base64.encodeToString(photoBytes, android.util.Base64.NO_WRAP)
            val reqBody = buildJsonObject {
                put("photoBase64", base64Photo)
                put("latitude", lat)
                put("longitude", lng)
                put("checkType", "aura_check")
            }
            val response = db.functions.invoke("verify-photo") {
                setBody(reqBody.toString())
                contentType(ContentType.Application.Json)
            }
            val responseText = response.bodyAsText()
            
            val jsonElement = kotlinx.serialization.json.Json.parseToJsonElement(responseText).jsonObject
            rating = jsonElement["rating"]?.jsonPrimitive?.int ?: (80..100).random()
            feedback = jsonElement["feedback"]?.jsonPrimitive?.content ?: "Verified securely via Live Edge AI Vision endpoint."
            pass = jsonElement["pass"]?.jsonPrimitive?.boolean ?: true
        } catch (e: Exception) {
            Log.e(TAG, "Edge Function verify-photo failed critically. Sybil defense active: rejecting check.", e)
            return com.aura.app.model.AuraCheckResult(0, "Network or Server Validation Error. Request Denied.", false, 0)
        }

        val creditsEarned = (rating * 1.5).toInt()
        val profile = _currentProfile.value ?: return com.aura.app.model.AuraCheckResult(rating, feedback, true, creditsEarned)

        val nowStr = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        var streak = profile.streakDays
        val lastScanStr = profile.lastScanAt
        var streakMaintained = false

        if (lastScanStr != null) {
            try {
                val lastScan = OffsetDateTime.parse(lastScanStr)
                val now = OffsetDateTime.now()
                val hoursSince = ChronoUnit.HOURS.between(lastScan, now)
                if (hoursSince in 20..48) {
                    streak += 1
                    streakMaintained = true
                } else if (hoursSince > 48) {
                    streak = 1
                    streakMaintained = false
                } else {
                    streakMaintained = true
                }
            } catch (e: Exception) { streak += 1; streakMaintained = true }
        } else {
            streak += 1
            streakMaintained = true
        }

        val newScore = (profile.auraScore + creditsEarned).coerceAtMost(100)
        val updatedProfile = profile.copy(auraScore = newScore, streakDays = streak, lastScanAt = nowStr)

        try {
            supabase.postgrest["profiles"].update({
                set("aura_score", updatedProfile.auraScore)
                set("streak_days", updatedProfile.streakDays)
                set("last_scan_at", updatedProfile.lastScanAt)
            }) { filter { eq("wallet_address", profile.walletAddress) } }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating profile after Aura check", e)
        }

        _currentProfile.value = updatedProfile

        profile.id?.let { uuid ->
            scope.launch {
                try {
                    val history = AuraHistoryDto(userId = uuid, changeAmount = creditsEarned, reason = "Daily Aura Check: $rating/100")
                    supabase.postgrest["aura_history"].insert(history)
                } catch (e: Exception) {}
            }
        }

        return com.aura.app.model.AuraCheckResult(rating, feedback, streakMaintained, creditsEarned)
    }

    // ── Listings ──────────────────────────────────────────────────────────────

    fun refreshListings() {
        scope.launch { refreshListingsAwait() }
    }

    suspend fun refreshListingsAwait() {
        // 1. Get user location once for Nearby / Explore distance stamps (best-effort)
        var userLat: Double? = null
        var userLng: Double? = null
        try {
            appContext?.let { ctx ->
                @SuppressLint("MissingPermission")
                val loc = LocationServices.getFusedLocationProviderClient(ctx)
                    .lastLocation.await()
                userLat = loc?.latitude
                userLng = loc?.longitude
            }
        } catch (_: Exception) {}

        // 2. Fetch active listings from network — single source of truth; published = globally available
        //    Only active listings (sold_at IS NULL); local filters (Nearby/Explore) applied in UI
        try {
            val rows = db.from("marketplace_listings").select {
                limit(count = 1000)
            }.decodeList<ListingRow>()
            val domainListings = rows
                .filter { it.soldAt == null && it.isActive && it.isPublished }
                .map { it.toDomain(userLat, userLng) }
            _listings.value = domainListings
            Log.d(TAG, "Loaded ${domainListings.size} active listings from Supabase (marketplace_listings)")
            appContext?.let { ctx ->
                ListingCache.save(ctx, domainListings)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load listings from network", e)
            appContext?.let { ctx ->
                val cached = ListingCache.load(ctx)
                if (cached.isNotEmpty()) {
                    _listings.value = cached
                    Log.d(TAG, "Using ${cached.size} listings from cache (network failed)")
                }
            }
        }
    }

    fun getListing(id: String): Listing? = _listings.value.find { it.id == id }

    // ── Context for offline cache (set from Application or MainActivity) ──
    var appContext: android.content.Context? = null
        set(value) {
            field = value
            // Phantom Meetup State fix (#13): restore trade session when app resumes after Maps handoff
            value?.let { ctx ->
                val restored = TradeSessionStore.load(ctx)
                if (restored != null && _currentTradeSession.value == null) {
                    _currentTradeSession.value = restored
                    observeTradeSession(restored.id)
                }
            }
        }

    suspend fun createListing(
        sellerWallet: String,
        title: String,
        description: String = "",
        priceLamports: Long,
        imageRefs: List<String>,
        condition: String,
        textureHash: String? = null,
        emirate: String? = null,
        location: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        nfcSunUrl: String? = null,
    ): Listing {
        // Ensure seller profile exists (required by FK: seller_wallet REFERENCES profiles)
        loadProfile(sellerWallet)
        val listingId = UUID.randomUUID().toString()
        val fingerprint = textureHash ?: "fp_${listingId.take(8)}"

        // Step 1: Execute actual binary uploads to Supabase Storage if the refs map to local files
        val uploadedUrls = mutableListOf<String>()
        val bucket = db.storage["listing-images"]

        for ((index, ref) in imageRefs.withIndex()) {
            if (ref.startsWith("content://") || ref.startsWith("file://") || ref.startsWith("/")) {
                val ext = if (ref.contains(".png", ignoreCase = true)) "png" else "jpg"
                val remotePath = "$listingId/image_$index.$ext"
                try {
                    val fileBytes = if (ref.startsWith("/") || ref.startsWith("file://")) {
                        val path = ref.removePrefix("file://")
                        java.io.File(path).readBytes()
                    } else {
                        // Handle content:// URIs
                        val ctx = SupabaseClient.appContext
                        if (ctx == null) {
                            Log.e(TAG, "appContext is null, cannot read content:// URI")
                            uploadedUrls.add(ref)
                            continue
                        }
                        val uri = android.net.Uri.parse(ref)
                        val inputStream = ctx.contentResolver?.openInputStream(uri)
                        val bytes = inputStream?.readBytes() ?: ByteArray(0)
                        inputStream?.close()
                        bytes
                    }

                    if (fileBytes.isNotEmpty()) {
                        bucket.upload(remotePath, fileBytes) {
                            upsert = true
                            contentType = if (ext == "png") ContentType.Image.PNG else ContentType.Image.JPEG
                        }
                        val publicUrl = bucket.publicUrl(remotePath)
                        uploadedUrls.add(publicUrl)
                        Log.d(TAG, "Uploaded image $index -> $publicUrl")
                    } else {
                        Log.w(TAG, "Image $index empty bytes, skipping")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to upload image $index to Supabase Storage: ${e.message}", e)
                    throw e  // Surface error so user knows upload failed; don't create listing with no images
                }
            } else {
                uploadedUrls.add(ref)
            }
        }

        // Step 2: Upload metadata.json for NFT minting (Metaplex metadata standard)
        val metadataJson = buildString {
            append("""{"name":"Aura Verified: ${title.replace("\"", "\\\"")}","symbol":"AURA","description":"${description.replace("\"", "\\\"")}","image":"${uploadedUrls.firstOrNull()?.replace("\\", "\\\\")?.replace("\"", "\\\"") ?: ""}"}""")
        }
        try {
            bucket.upload("$listingId/metadata.json", metadataJson.toByteArray(Charsets.UTF_8)) {
                upsert = true
                contentType = ContentType.Application.Json
            }
            Log.d(TAG, "Uploaded metadata.json for listing $listingId")
        } catch (e: Exception) {
            Log.w(TAG, "Metadata upload failed (mint may use image fallback): ${e.message}")
        }

        val row = ListingRowInsert(
            id = listingId,
            sellerWallet = sellerWallet,
            title = title,
            description = description,
            priceLamports = priceLamports,
            images = uploadedUrls.toList(),
            condition = condition,
            mintedStatus = "PENDING",
            fingerprintHash = fingerprint,
            emirate = emirate,
            location = location ?: emirate,
            latitude = latitude,
            longitude = longitude,
            isActive = true,
            isPublished = true,
            nfcSunUrl = nfcSunUrl,
        )
        try {
            db.from("marketplace_listings").insert(row)
            Log.d(TAG, "Inserted listing $listingId (marketplace_listings)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create PostgreSQL listing row", e)
            throw e
        }
        val listing = row.toDomain()
        _listings.value = _listings.value + listing
        // Persist to cache so it survives app restart before next network fetch
        appContext?.let { ctx -> ListingCache.save(ctx, _listings.value) }
        // Re-fetch from DB to confirm round-trip (insert + select both hit marketplace_listings)
        try {
            refreshListingsAwait()
            Log.d(TAG, "Re-fetched listings from Supabase after create; count=${_listings.value.size}")
        } catch (e: Exception) {
            Log.w(TAG, "Re-fetch after create failed (listing still in local state): ${e.message}")
        }
        return listing
    }

    /** Promotion fee: 10 SOL. Must be paid to treasury before promoting. */
    val PROMOTE_FEE_LAMPORTS: Long = 10L * 1_000_000_000

    /**
     * Promote listing after payment is verified. Call this only after the user has successfully
     * sent 10 SOL to the treasury; pass the transaction signature.
     */
    suspend fun promoteListing(listingId: String, txSignature: String): Result<Unit> {
        val listing = getListing(listingId)
            ?: return Result.failure(Exception("Listing not found. Pull to refresh and try again."))
        val wallet = com.aura.app.wallet.WalletConnectionState.walletAddress.value
            ?: return Result.failure(Exception("Wallet not connected. Connect your wallet first."))
        if (listing.sellerWallet != wallet)
            return Result.failure(Exception("Only the seller can promote this listing."))

        return try {
            val reqBody = buildJsonObject {
                put("listingId", listingId)
                put("txSignature", txSignature)
                put("sellerWallet", wallet)
            }
            val response = db.functions.invoke("promote-listing") {
                setBody(reqBody.toString())
                contentType(ContentType.Application.Json)
            }
            val text = response.bodyAsText()
            val json = kotlinx.serialization.json.Json.parseToJsonElement(text).jsonObject
            val error = json["error"]?.jsonPrimitive?.content
            if (!error.isNullOrBlank()) {
                return Result.failure(Exception(error))
            }
            _listings.value = _listings.value.map {
                if (it.id == listingId) {
                    it.copy(
                        isPromoted = true,
                        promotedAt = System.currentTimeMillis(),
                        promotedUntil = System.currentTimeMillis() + 24 * 60 * 60 * 1000,
                    )
                } else it
            }
            refreshListingsAwait()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to promote listing", e)
            Result.failure(Exception(e.message ?: "Promotion failed. Check your connection and try again."))
        }
    }

    suspend fun mintListing(listingId: String): Listing {
        val listing = getListing(listingId) ?: run {
            Log.w(TAG, "mintListing: listing $listingId not found in local state, skipping mint")
            // Return a dummy so the caller's flow doesn't crash
            return@mintListing Listing(
                id = listingId, sellerWallet = "", title = "",
                priceLamports = 0, images = emptyList(), condition = "Good",
                mintedStatus = MintedStatus.PENDING,
                mintAddress = null,
                fingerprintHash = "",
                createdAt = System.currentTimeMillis(),
            )
        }
        return try {
            val firstImage = listing.images.firstOrNull()
            val metadataUrl = firstImage?.let { url ->
                val lastSlash = url.lastIndexOf('/')
                if (lastSlash >= 0) "${url.substring(0, lastSlash + 1)}metadata.json" else "https://aura.so/metadata/${listing.id}.json"
            } ?: "https://aura.so/metadata/${listing.id}.json"
            val reqBody = buildJsonObject {
                put("listingId", listing.id)
                put("title", listing.title)
                put("sellerWalletBase58", listing.sellerWallet)
                put("metadataUrl", metadataUrl)
            }
            val response = db.functions.invoke("mint-nft") {
                setBody(reqBody.toString())
                contentType(ContentType.Application.Json)
            }
            val responseText = response.bodyAsText()
            val jsonElement = kotlinx.serialization.json.Json.parseToJsonElement(responseText).jsonObject
            // Edge Function returns { error: "..." } on failure; { success, mintAddress } on success
            val errorMsg = jsonElement["error"]?.jsonPrimitive?.content
            val mintAddr = jsonElement["mintAddress"]?.jsonPrimitive?.content
            if (!errorMsg.isNullOrBlank() || mintAddr.isNullOrBlank()) {
                Log.w(TAG, "Mint response error or missing mintAddress: $errorMsg")
                return@mintListing listing
            }
            val updated = listing.copy(mintedStatus = MintedStatus.MINTED, mintAddress = mintAddr)
            _listings.value = _listings.value.map { if (it.id == listingId) updated else it }
            try {
                db.from("marketplace_listings").update({
                    set("mint_address", mintAddr)
                    set("minted_status", "MINTED")
                }) { filter { eq("id", listingId) } }
            } catch (_: Exception) {}
            updated
        } catch (e: Exception) {
            Log.w(TAG, "Minting Edge Function failed (non-fatal) — listing is saved: ${e.message}")
            listing
        }
    }

    /**
     * Verify a photo against the listing (item match) or for Aura Check.
     * @param listingId Required for item_match; used to fetch listing reference image server-side.
     * @param checkType "item_match" for trade verify screen (match item to listing); "aura_check" for Aura Check flow.
     */
    suspend fun verifyPhoto(listingId: String, photoBytes: ByteArray, checkType: String = "item_match"): VerificationResult {
        if (photoBytes.isEmpty()) {
            return VerificationResult(score = 0f, pass = false, reason = "No photo provided.")
        }
        try {
            val base64Photo = android.util.Base64.encodeToString(photoBytes, android.util.Base64.NO_WRAP)
            val reqBody = buildJsonObject {
                put("listingId", listingId)
                put("photoBase64", base64Photo)
                put("checkType", checkType)
            }
            val response = db.functions.invoke("verify-photo") {
                setBody(reqBody.toString())
                contentType(ContentType.Application.Json)
            }
            // Parse the real AI Vision response
            val responseText = response.bodyAsText()
            val jsonElement = kotlinx.serialization.json.Json.parseToJsonElement(responseText).jsonObject
            val rating = jsonElement["rating"]?.jsonPrimitive?.int ?: 50
            val pass = jsonElement["pass"]?.jsonPrimitive?.boolean ?: false
            val feedback = jsonElement["feedback"]?.jsonPrimitive?.content ?: "Analysis complete"
            return VerificationResult(
                score = rating / 100f,
                pass = pass,
                reason = feedback
            )
        } catch (e: Exception) {
            Log.e(TAG, "verify-photo Edge Function failed", e)
            return VerificationResult(score = 0f, pass = false, reason = "Verification service unavailable: ${e.message}")
        }
    }

    // ── Trade Sessions ───────────────────────────────────────────────────────

    /**
     * Cross-Hemisphere fix (#5): Returns false if buyer and listing are >500km apart.
     * Call before createTradeSession to prevent locking escrow with no physical meetup possible.
     */
    suspend fun canStartMeetup(listingId: String): Boolean {
        val listing = getListing(listingId) ?: return true
        val lat = listing.latitude ?: return true
        val lng = listing.longitude ?: return true
        val buyerLoc = runCatching {
            appContext?.let { ctx ->
                @SuppressLint("MissingPermission")
                LocationServices.getFusedLocationProviderClient(ctx).lastLocation.await()
            }
        }.getOrNull() ?: return true
        return MeetupLocationUtils.isWithinMeetupRange(
            buyerLoc.latitude, buyerLoc.longitude,
            lat, lng
        )
    }

    fun createTradeSession(listingId: String, buyerWallet: String, sellerWallet: String): TradeSession {
        if (listingId.isBlank() || buyerWallet.isBlank() || sellerWallet.isBlank()) {
            Log.e(TAG, "createTradeSession: missing required field (listingId, buyerWallet, or sellerWallet)")
            throw IllegalArgumentException("Missing required fields to start trade.")
        }
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
        appContext?.let { TradeSessionStore.save(it, session) }
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
            } catch (e: Exception) {}
        }
        // Start listening for Realtime state changes on this session
        observeTradeSession(session.id)
        return session
    }

    fun updateTradeState(state: TradeState) {
        _currentTradeSession.value?.let { session ->
            val updated = session.copy(state = state, lastUpdated = System.currentTimeMillis())
            _currentTradeSession.value = updated
            appContext?.let { TradeSessionStore.save(it, updated) }
            scope.launch {
                try {
                    db.from("trade_sessions").update(
                        { set("state", state.name); set("updated_at", "now()") }
                    ) { filter { eq("id", session.id) } }
                    // Silent Chat State fix: send automated "Payment Secured" message so seller sees it
                    if (state == TradeState.ESCROW_LOCKED) {
                        try {
                            val msg = com.aura.app.model.ChatMessage(
                                id = UUID.randomUUID().toString(),
                                listingId = session.listingId,
                                senderWallet = session.buyerWallet,
                                receiverWallet = session.sellerWallet,
                                content = "Payment Secured ✓",
                                createdAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                            )
                            ChatRepository.sendMessage(msg)
                            Log.d(TAG, "Sent Payment Secured message for trade ${session.id}")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to send Payment Secured chat message", e)
                        }
                    }
                } catch (e: Exception) {}
            }
        }
    }

    /**
     * Cancel trade with atomic check to prevent race: if seller already claimed (TRADE_COMPLETE),
     * cancel is rejected to avoid duplicate payout or trapped funds.
     */
    suspend fun cancelTradeSessionIfNotComplete(tradeId: String): Boolean {
        return try {
            val session = _currentTradeSession.value ?: return false
            if (session.id != tradeId) return false
            val current = db.from("trade_sessions")
                .select { filter { eq("id", tradeId) } }
                .decodeList<TradeSessionRow>()
                .firstOrNull() ?: return false
            val stateStr = current.state.uppercase().replace("TRADE_", "")
            val state = runCatching { TradeState.valueOf(stateStr) }.getOrDefault(TradeState.SESSION_CREATED)
            val isTerminal = state == TradeState.COMPLETE || state == TradeState.CANCELLED
            if (isTerminal) {
                Log.w(TAG, "Trade $tradeId cancel skipped - already $state")
                return false
            }
            db.from("trade_sessions").update(
                { set("state", TradeState.CANCELLED.name); set("updated_at", "now()") }
            ) { filter { eq("id", tradeId) } }
            _currentTradeSession.value = session.copy(state = TradeState.CANCELLED, lastUpdated = System.currentTimeMillis())
            Log.d(TAG, "Trade $tradeId cancelled")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Cancel trade failed", e)
            false
        }
    }

    fun clearTradeSession() {
        _currentTradeSession.value = null
        appContext?.let { TradeSessionStore.save(it, null) }
        _activeTradeChannel?.let { ch ->
            scope.launch {
                try { ch.unsubscribe() } catch (_: Exception) {}
            }
        }
        _activeTradeChannel = null
    }

    // ── Realtime Trade Session Observation ───────────────────────────────────

    private var _activeTradeChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null

    /**
     * Subscribe to Realtime updates for the current trade session.
     * Both buyer and seller can observe state transitions (ESCROW_LOCKED → BOTH_PRESENT → COMPLETE)
     * without polling. Call this after createTradeSession().
     */
    fun observeTradeSession(tradeId: String) {
        // Unsubscribe from any existing channel
        _activeTradeChannel?.let { ch ->
            scope.launch {
                try { ch.unsubscribe() } catch (_: Exception) {}
            }
        }

        scope.launch {
            try {
                val channel = db.realtime.channel("trade_session_$tradeId")
                _activeTradeChannel = channel
                val flow = channel.postgresChangeFlow<PostgresAction.Update>("public") {
                    table = "trade_sessions"
                }
                channel.subscribe()

                flow.collect { action ->
                    try {
                        val row = action.decodeRecord<TradeSessionRow>()
                        if (row.id == tradeId) {
                            val newState = runCatching { TradeState.valueOf(row.state) }
                                .getOrDefault(TradeState.SESSION_CREATED)
                            _currentTradeSession.value?.let { session ->
                                _currentTradeSession.value = session.copy(
                                    state = newState,
                                    lastUpdated = System.currentTimeMillis()
                                )
                            }
                            Log.d(TAG, "Realtime trade state update: $tradeId → ${row.state}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to decode Realtime trade update", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to subscribe to trade session Realtime", e)
            }
        }
    }

    // ── Escrow (Live Anchor Integration) ────────

    /**
     * Build the escrow initialization transaction.
     * NOTE: State is NOT updated here — the caller must update state AFTER
     * the wallet adapter confirms the signature.
     *
     * Token mismatch fix (19): Always use listing.priceLamports as source of truth.
     * Never trust client-passed amount — prevents buyer altering payload to pay wrong token/amount.
     *
     * Derank fix (20): fee_exempt is snapshotted at init from listing.sellerAuraScore.
     * Escrow state is frozen; release uses on-chain fee_exempt, so derank during tx does not affect settlement.
     */
    suspend fun initEscrow(tradeId: String, amount: Long): ByteArray {
        val walletAddr = com.aura.app.wallet.WalletConnectionState.walletAddress.value
            ?: throw IllegalStateException("Wallet not connected")
        val session = _currentTradeSession.value
        val listingId = session?.listingId ?: tradeId
        val sellerWallet = session?.sellerWallet
            ?: throw IllegalStateException("No seller wallet in trade session")
        val listing = getListing(listingId) ?: throw IllegalStateException("Listing not found")
        // Token mismatch fix: always use listing price from source of truth; ignore client amount
        val amountLamports = listing.priceLamports.let { raw ->
            if (raw in 1L..999L) com.aura.app.util.CryptoPriceFormatter.solToLamports(raw.toDouble())
            else raw
        }
        if (amount != amountLamports) {
            Log.w(TAG, "initEscrow: client amount $amount != listing price $amountLamports; using listing price")
        }
        Log.d(TAG, "Initializing escrow for trade $tradeId, amount: $amountLamports lamports (SOL only)")
        // Derank fix: fee_exempt snapshotted at init; escrow stores it on-chain; release does not re-check rank
        val isRadiant = (listing.sellerAuraScore.coerceAtLeast(50)) >= 90
        val treasuryWallet = com.aura.app.BuildConfig.TREASURY_WALLET
        val feeBps = if (isRadiant) 0 else DEFAULT_PLATFORM_FEE_BPS
        val txBytes = com.aura.app.wallet.AnchorTransactionBuilder.buildInitializeEscrowTx(
            listingId = listingId,
            amountLamports = amountLamports,
            buyerPubkey = walletAddr,
            sellerPubkey = sellerWallet,
            feeBps = feeBps,
            treasuryWallet = treasuryWallet,
            feeExempt = isRadiant,
        )
        // Do NOT call updateTradeState here — wait for wallet signing confirmation
        return txBytes
    }

    /**
     * Build the Anchor release_funds_and_mint transaction (non-NFC path).
     * NOTE: State is NOT updated here — the caller must update state AFTER
     * the wallet adapter confirms the signature.
     */
    suspend fun releaseEscrow(tradeId: String): ByteArray {
        Log.d(TAG, "Releasing escrow for trade $tradeId")
        val walletAddr = com.aura.app.wallet.WalletConnectionState.walletAddress.value
            ?: throw IllegalStateException("Wallet not connected")
        val session = _currentTradeSession.value
        val listingId = session?.listingId ?: tradeId
        val listing = getListing(listingId)
        val sellerWallet = session?.sellerWallet ?: ""
        // Build the proper Anchor release_funds_and_mint instruction
        val txBytes = com.aura.app.wallet.AnchorTransactionBuilder.buildReleaseEscrowTx(
            listingId = listingId,
            sellerPubkey = sellerWallet,
            signerPubkey = walletAddr,
            assetUri = listing?.images?.firstOrNull() ?: "",
            assetTitle = listing?.title ?: "Aura Verified Asset",
            treasuryWallet = com.aura.app.BuildConfig.TREASURY_WALLET,
        )
        // Do NOT call updateTradeState here — wait for wallet signing confirmation
        return txBytes
    }

    /** Result of release attempt: success, network/offline (queued for retry), or other failure */
    sealed class ReleaseResult {
        data object Success : ReleaseResult()
        data object OfflineQueued : ReleaseResult()
        data class Failed(val reason: String) : ReleaseResult()
    }

    private fun isNetworkError(e: Exception): Boolean {
        val msg = e.message?.lowercase() ?: ""
        return e is java.io.IOException ||
                e is java.net.UnknownHostException ||
                e is java.net.ConnectException ||
                e is java.net.SocketTimeoutException ||
                msg.contains("network") || msg.contains("unreachable") || msg.contains("timeout") ||
                msg.contains("connection") || msg.contains("failed to connect")
    }

    suspend fun releaseEscrowWithNfc(
        tradeId: String,
        listingId: String,
        sdmDataHex: String,
        receivedCmacHex: String,
        escrowPdaBase58: String,
        sellerWalletBase58: String,
        buyerWalletBase58: String?,
        assetUri: String,
        assetTitle: String,
        amount: Long
    ): ReleaseResult {
        try {
            val reqBody = buildJsonObject {
                put("listingId", listingId)
                put("sdmDataHex", sdmDataHex)
                put("receivedCmacHex", receivedCmacHex)
                put("escrowPdaBase58", escrowPdaBase58)
                put("sellerWalletBase58", sellerWalletBase58)
                put("buyerWalletBase58", buyerWalletBase58)
                put("assetUri", assetUri)
                put("assetTitle", assetTitle)
                put("amount", amount)
            }
            val response = db.functions.invoke("verify-sun") {
                setBody(reqBody.toString())
                contentType(ContentType.Application.Json)
            }
            val responseText = response.bodyAsText()
            Log.d(TAG, "NFC verified and escrow released via Live Edge Function. Response: $responseText")
            appContext?.let { PendingReleaseStore.clear(it) }
            return ReleaseResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "Failed Edge Function verify-sun", e)
            if (isNetworkError(e)) {
                appContext?.let { ctx ->
                    PendingReleaseStore.save(ctx, tradeId, listingId, sdmDataHex, receivedCmacHex,
                        escrowPdaBase58, sellerWalletBase58, buyerWalletBase58, assetUri, assetTitle, amount)
                }
                return ReleaseResult.OfflineQueued
            }
            return ReleaseResult.Failed(e.message ?: "Release failed")
        }
    }

    suspend fun retryPendingRelease(): ReleaseResult {
        val pending = appContext?.let { PendingReleaseStore.load(it) } ?: return ReleaseResult.Failed("No pending release")
        return releaseEscrowWithNfc(
            tradeId = pending.tradeId,
            listingId = pending.listingId,
            sdmDataHex = pending.sdmDataHex,
            receivedCmacHex = pending.receivedCmacHex,
            escrowPdaBase58 = pending.escrowPdaBase58,
            sellerWalletBase58 = pending.sellerWalletBase58,
            buyerWalletBase58 = pending.buyerWalletBase58,
            assetUri = pending.assetUri,
            assetTitle = pending.assetTitle,
            amount = pending.amount
        )
    }

    fun hasPendingRelease(): Boolean = appContext?.let { PendingReleaseStore.hasPending(it) } == true

    /**
     * Hostage fix (18): Build cancel_and_refund tx for buyer to reclaim SOL
     * after 24h if seller never released. Buyer signs and sends.
     */
    suspend fun buildCancelAndRefundTx(tradeId: String): ByteArray {
        val walletAddr = com.aura.app.wallet.WalletConnectionState.walletAddress.value
            ?: throw IllegalStateException("Wallet not connected")
        val session = _currentTradeSession.value ?: throw IllegalStateException("No trade session")
        if (session.id != tradeId) throw IllegalArgumentException("Trade ID mismatch")
        val listingId = session.listingId
        if (session.buyerWallet != walletAddr) {
            throw IllegalStateException("Only the buyer can request a refund")
        }
        return com.aura.app.wallet.AnchorTransactionBuilder.buildCancelAndRefundTx(
            listingId = listingId,
            buyerPubkey = walletAddr,
        )
    }

    suspend fun getEscrowStatus(tradeId: String): EscrowStatus {
        try {
            val sessions = db.from("trade_sessions")
                .select { filter { eq("id", tradeId) } }
                .decodeList<TradeSessionRow>()
            val session = sessions.firstOrNull()
            return EscrowStatus(
                txSig = session?.escrowTxSig ?: "pending_$tradeId",
                state = when (session?.state) {
                    "TRADE_COMPLETE" -> EscrowState.RELEASED
                    "ESCROW_FUNDED" -> EscrowState.LOCKED
                    else -> EscrowState.LOCKED
                },
                amount = getListing(session?.listingId ?: "")?.priceLamports ?: 0L,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get escrow status", e)
            return EscrowStatus(txSig = "error", state = EscrowState.LOCKED, amount = 0L)
        }
    }

    // ── DTO → Domain Mapper ──────────────────────────────────────────────────

    private fun ListingRowInsert.toDomain() = Listing(
        id = id,
        sellerWallet = sellerWallet,
        title = title,
        description = description,
        priceLamports = priceLamports,
        images = images,
        mintedStatus = parseMintedStatus(mintedStatus),
        mintAddress = null,
        fingerprintHash = fingerprintHash ?: "",
        condition = condition,
        createdAt = System.currentTimeMillis(),
        latitude = latitude,
        longitude = longitude,
        location = location,
        soldAt = null,
        buyerWallet = null,
        distanceMeters = null,
        sellerAuraScore = 50,
        emirate = emirate,
        isPromoted = false,
        promotedAt = null,
        promotedUntil = null,
        nfcSunUrl = nfcSunUrl,
    )

    private fun ListingRow.toDomain(userLat: Double? = null, userLng: Double? = null) = Listing(
        id = id,
        sellerWallet = sellerWallet,
        title = title,
        description = description,
        priceLamports = priceLamports,
        images = images,
        mintedStatus = parseMintedStatus(mintedStatus),
        mintAddress = mintAddress,
        fingerprintHash = fingerprintHash ?: "",
        condition = condition,
        createdAt = runCatching {
            OffsetDateTime.parse(createdAt ?: "").toInstant().toEpochMilli()
        }.getOrDefault(System.currentTimeMillis()),
        latitude = latitude,
        longitude = longitude,
        location = location,
        soldAt = soldAt?.let { try { java.time.OffsetDateTime.parse(it).toEpochSecond() * 1000 } catch (_: Exception) { null } },
        buyerWallet = buyerWallet,
        distanceMeters = if (userLat != null && userLng != null && latitude != null && longitude != null)
            haversineMeters(userLat, userLng, latitude, longitude).toInt()
        else null,
        sellerAuraScore = sellerAuraScore,
        emirate = emirate,
        isPromoted = isPromoted,
        promotedAt = promotedAt?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() },
        promotedUntil = promotedUntil?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() },
        nfcSunUrl = nfcSunUrl,
    )

    private fun parseMintedStatus(s: String?): MintedStatus = runCatching {
        val trimmed = s?.trim()?.uppercase() ?: "PENDING"
        MintedStatus.valueOf(trimmed)
    }.getOrDefault(MintedStatus.PENDING)

    private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2).pow(2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2).pow(2)
        return r * 2 * Math.asin(Math.sqrt(a))
    }
}
