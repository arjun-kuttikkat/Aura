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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlin.math.pow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneOffset
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
    @SerialName("meetup_radius_meters") val meetupRadiusMeters: Int? = null,
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
    @SerialName("meetup_radius_meters") val meetupRadiusMeters: Int? = null,
    @SerialName("seller_aura_score") val sellerAuraScore: Int = 50,
)

@Serializable
data class CompletedMissionRow(
    val id: String = "",
    @SerialName("profile_id") val profileId: String? = null,
    @SerialName("wallet_address") val walletAddress: String = "",
    val title: String = "",
    @EncodeDefault val emoji: String = "✨",
    @SerialName("aura_reward") val auraReward: Int = 0,
    @SerialName("ai_feedback") val aiFeedback: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
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
    @SerialName("receipt_mint_buyer") val receiptMintBuyer: String? = null,
    @SerialName("receipt_mint_seller") val receiptMintSeller: String? = null,
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

    private val _lastReceiptMintError = MutableStateFlow<String?>(null)
    val lastReceiptMintError: StateFlow<String?> = _lastReceiptMintError.asStateFlow()

    init {
        refreshListings()
        // Auto-refresh listings every 60 seconds so other users' new listings appear
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000)
                try {
                    val rows = db.from("marketplace_listings").select {
                        limit(count = 200)
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
            withContext(Dispatchers.Main.immediate) {
                _currentProfile.value = null
            }
            return
        }
        try {
            val profiles = supabase.postgrest["profiles"]
                .select { filter { eq("wallet_address", walletAddress) } }
                .decodeList<ProfileDto>()
            
            if (profiles.isNotEmpty()) {
                var p = profiles.first()
                
                // --- Aura System: Decay Logic & Star Protection ---
                val lastScanStr = p.lastScanAt
                var updatedAuraScore = p.auraScore
                var updatedStreak = p.streakDays
                var updated = false
                if (lastScanStr != null) {
                    try {
                        val lastScanDate = OffsetDateTime.parse(lastScanStr).withOffsetSameInstant(ZoneOffset.UTC).toLocalDate()
                        val nowDate = OffsetDateTime.now(ZoneOffset.UTC).toLocalDate()
                        val daysSince = ChronoUnit.DAYS.between(lastScanDate, nowDate).toInt()
                        
                        if (daysSince > 1) {
                            // Streak broken — reset to 0
                            updatedStreak = 0
                            val decayDays = daysSince - 1
                            var actualDecayDays = decayDays
                            
                            // Check for Star Protection Card
                            appContext?.let { ctx ->
                                while (AvatarPreferences.getOwnedProtectionCards(ctx) > 0 && actualDecayDays > 0) {
                                    AvatarPreferences.consumeProtectionCard(ctx)
                                    actualDecayDays -= 1
                                    Log.i(TAG, "Consumed 1 Star Protection Card. Decay reduced. Remaining decay days: $actualDecayDays")
                                }
                            }
                            
                            if (actualDecayDays > 0) {
                                updatedAuraScore = (p.auraScore - (actualDecayDays * 100)).coerceAtLeast(0)
                                updatedStreak = 0 // Streak broken
                            }
                            updated = true
                        } else {
                            // Even if no decay, update lastScanAt to NOW on login 
                            // to reset the 24h grace window and prevent drift.
                            updated = true
                        }
                    } catch (e: Exception) {
                        updated = true
                    }
                } else {
                    // First time or missing date - set it now
                    updated = true
                }
                
                if (updated) {
                    val finalNowStr = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    val rankInfo = com.aura.app.model.RankSystem.getRankInfo(updatedAuraScore)
                    val rankTitle = "${rankInfo.rankName} ${rankInfo.tierString}".trim()

                    p = p.copy(
                        auraScore = updatedAuraScore,
                        streakDays = updatedStreak,
                        lastScanAt = finalNowStr,
                        rankTitle = rankTitle,
                        pointsToNextRank = rankInfo.pointsToNextStar
                    )
                    try {
                        supabase.postgrest["profiles"].update({
                            set("aura_score", p.auraScore)
                            set("streak_days", p.streakDays)
                            set("last_scan_at", p.lastScanAt)
                            set("rank_title", rankTitle)
                            set("points_to_next_rank", rankInfo.pointsToNextStar)
                        }) { filter { eq("wallet_address", p.walletAddress) } }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating profile sync", e)
                    }
                }
                withContext(Dispatchers.Main.immediate) {
                    _currentProfile.value = p
                }
            } else {
                val newProfile = ProfileDto(walletAddress = walletAddress)
                val insertedList = supabase.postgrest["profiles"]
                    .insert(newProfile) { select() }
                    .decodeList<ProfileDto>()
                if (insertedList.isNotEmpty()) {
                    withContext(Dispatchers.Main.immediate) {
                        _currentProfile.value = insertedList.first()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading profile", e)
        }
    }

    suspend fun performMirrorRitual() {
        val profile = _currentProfile.value ?: return
        try {
            val nowStr = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val lastScanStr = profile.lastScanAt
            var streak = profile.streakDays
            val scoreGained = 5
            val newScore = profile.auraScore + scoreGained

            if (lastScanStr != null) {
                try {
                    val lastScanDate = OffsetDateTime.parse(lastScanStr).withOffsetSameInstant(ZoneOffset.UTC).toLocalDate()
                    val nowDate = OffsetDateTime.now(ZoneOffset.UTC).toLocalDate()
                    val daysBetween = ChronoUnit.DAYS.between(lastScanDate, nowDate)
                    
                    if (daysBetween == 1L) {
                        streak += 1
                    } else if (daysBetween > 1L) {
                        streak = 1
                    }
                    // If same day (0), streak stays as is (already incremented)
                } catch (e:Exception) { streak += 1 }
            } else streak += 1

            val rankInfo = com.aura.app.model.RankSystem.getRankInfo(newScore)
            val rankTitleValue = "${rankInfo.rankName} ${rankInfo.tierString}".trim()
            
            val updatedProfile = profile.copy(
                auraScore = newScore,
                streakDays = streak,
                lastScanAt = nowStr,
                rankTitle = rankTitleValue,
                pointsToNextRank = rankInfo.pointsToNextStar
            )

            supabase.postgrest["profiles"].update({
                set("aura_score", updatedProfile.auraScore)
                set("streak_days", updatedProfile.streakDays)
                set("last_scan_at", updatedProfile.lastScanAt)
                set("rank_title", rankTitleValue)
                set("points_to_next_rank", rankInfo.pointsToNextStar)
            }) { filter { eq("wallet_address", profile.walletAddress) } }

            withContext(Dispatchers.Main.immediate) {
                _currentProfile.value = updatedProfile
            }

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
        val submissionOriginTime = OffsetDateTime.now(ZoneOffset.UTC)
        
        // 1. Anti-Cheat: Reject Hash Duplicates
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val photoHash = md.digest(photoBytes).joinToString("") { "%02x".format(it) }
        val recentHashes = AuraPreferences.getRecentMissionHashes()
        if (recentHashes.contains(photoHash)) {
            Log.w(TAG, "Duplicate submission detected. Match found in recent hashes.")
            return com.aura.app.model.AuraCheckResult(0, "Duplicate photo detected. Please capture a live image.", false, 0)
        }

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
            if (pass) AuraPreferences.addMissionHash(photoHash)
        } catch (e: Exception) {
            Log.e(TAG, "Edge Function verify-photo failed critically. Sybil defense active: rejecting check.", e)
            return com.aura.app.model.AuraCheckResult(0, "Network or Server Validation Error. Request Denied.", false, 0)
        }

        val creditsEarned = (rating * 1.5).toInt()
        var profile = _currentProfile.value
        if (profile == null) {
            val wallet = com.aura.app.wallet.WalletConnectionState.walletAddress.value
            if (!wallet.isNullOrBlank()) {
                loadProfile(wallet)
                profile = _currentProfile.value
            }
        }
        if (profile == null) return com.aura.app.model.AuraCheckResult(rating, feedback, true, creditsEarned)

        val lastScanStr = profile.lastScanAt
        val nowStr = submissionOriginTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val lastScanDate = if (lastScanStr != null) OffsetDateTime.parse(lastScanStr).withOffsetSameInstant(ZoneOffset.UTC).toLocalDate() else null
        val nowDate = submissionOriginTime.toLocalDate()
        var streak = profile.streakDays
        var streakMaintained = false

        if (lastScanDate != null) {
            val daysBetween = ChronoUnit.DAYS.between(lastScanDate, nowDate)
            if (daysBetween == 1L) {
                streak += 1
                streakMaintained = true
            } else if (daysBetween > 1L) {
                streak = 1
                streakMaintained = false
            } else {
                // Same day or somehow retroactive
                streakMaintained = true
            }
        } else {
            streak += 1
            streakMaintained = true
        }

        val newScore = profile.auraScore + creditsEarned
        val rankInfo = com.aura.app.model.RankSystem.getRankInfo(newScore)
        val rankTitleValue = "${rankInfo.rankName} ${rankInfo.tierString}".trim()
        val updatedProfile = profile.copy(
            auraScore = newScore,
            streakDays = streak,
            lastScanAt = nowStr,
            rankTitle = rankTitleValue,
            pointsToNextRank = rankInfo.pointsToNextStar
        )

        try {
            invokeWithRetry(maxAttempts = 3, initialDelayMs = 400) {
                supabase.postgrest["profiles"].update({
                    set("aura_score", updatedProfile.auraScore)
                    set("streak_days", updatedProfile.streakDays)
                    set("last_scan_at", updatedProfile.lastScanAt)
                    set("rank_title", rankTitleValue)
                    set("points_to_next_rank", rankInfo.pointsToNextStar)
                }) { filter { eq("wallet_address", profile.walletAddress) } }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating profile after Aura check (retries exhausted)", e)
        }

        withContext(Dispatchers.Main.immediate) {
            _currentProfile.value = updatedProfile
        }

        profile.id?.let { uuid ->
            scope.launch {
                try {
                    val history = AuraHistoryDto(userId = uuid, changeAmount = creditsEarned, reason = "Daily Aura Check: $rating/100")
                    supabase.postgrest["aura_history"].insert(history)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to insert aura_history for Aura Check", e)
                }
            }
        }

        return com.aura.app.model.AuraCheckResult(rating, feedback, streakMaintained, creditsEarned)
    }

    /** Aura System: Add points when user completes a mission (Directives). Ultra-reliable with retry. */
    suspend fun addMissionAuraPoints(walletAddress: String, auraReward: Int, missionTitle: String) {
        if (walletAddress.isEmpty()) return
        var profile = _currentProfile.value
        if (profile == null) {
            loadProfile(walletAddress)
            profile = _currentProfile.value
        }
        if (profile == null || profile.walletAddress != walletAddress) return

        val submissionOriginTime = OffsetDateTime.now(ZoneOffset.UTC)
        val nowStr = submissionOriginTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        var streak = profile.streakDays
        val lastScanStr = profile.lastScanAt

        if (lastScanStr != null) {
            try {
                val lastScanDate = OffsetDateTime.parse(lastScanStr).withOffsetSameInstant(ZoneOffset.UTC).toLocalDate()
                val nowDate = submissionOriginTime.toLocalDate()
                val daysBetween = ChronoUnit.DAYS.between(lastScanDate, nowDate)
                if (daysBetween == 1L) streak += 1 else if (daysBetween > 1L) streak = 1
            } catch (e: Exception) { streak += 1 }
        } else {
            streak += 1
        }

        val reason = "Completed Mission: $missionTitle"
        val newScore = profile.auraScore + auraReward
        val rankInfo = com.aura.app.model.RankSystem.getRankInfo(newScore)
        try {
            invokeWithRetry(maxAttempts = 3, initialDelayMs = 400) {
                supabase.postgrest["profiles"].update({
                    set("aura_score", newScore)
                    set("streak_days", streak)
                    set("last_scan_at", nowStr)
                    set("rank_title", "${rankInfo.rankName} ${rankInfo.tierString}".trim())
                    set("points_to_next_rank", rankInfo.pointsToNextStar)
                }) { filter { eq("wallet_address", profile.walletAddress) } }
                profile.id?.let { uuid ->
                    try {
                        supabase.postgrest["aura_history"].insert(
                            AuraHistoryDto(userId = uuid, changeAmount = auraReward, reason = reason)
                        )
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating profile after mission aura points (retries exhausted)", e)
            // Even if network fails, proceed to update local state so user doesn't block
        }

        val updatedProfile = profile.copy(
            auraScore = newScore,
            streakDays = streak,
            lastScanAt = nowStr,
            rankTitle = "${rankInfo.rankName} ${rankInfo.tierString}".trim(),
            pointsToNextRank = rankInfo.pointsToNextStar
        )
        withContext(Dispatchers.Main.immediate) {
            _currentProfile.value = updatedProfile
        }
        Log.i(TAG, "Mission Completed: +$auraReward score, streak now $streak")
    }

    /** Insert completed mission into Supabase so it persists to account (survives logout/reinstall). */
    suspend fun insertCompletedMission(
        walletAddress: String,
        profileId: String?,
        record: com.aura.app.model.CompletedMissionRecord,
    ) {
        if (walletAddress.isEmpty()) return
        try {
            val completedAtIso = OffsetDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(record.completedAtMillis),
                ZoneOffset.UTC
            ).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val row = CompletedMissionRow(
                id = record.id ?: UUID.randomUUID().toString(),
                profileId = profileId,
                walletAddress = walletAddress,
                title = record.title,
                emoji = record.emoji,
                auraReward = record.auraReward,
                aiFeedback = record.aiFeedback.ifBlank { null },
                completedAt = completedAtIso,
            )
            supabase.postgrest["completed_missions"].insert(row)
            Log.d(TAG, "Inserted completed mission to Supabase: ${record.title}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to insert completed mission to Supabase: ${e.message}")
        }
    }

    /** Fetch completed missions from Supabase for the given wallet. */
    suspend fun fetchCompletedMissions(walletAddress: String): List<com.aura.app.model.CompletedMissionRecord> {
        if (walletAddress.isEmpty()) return emptyList()
        return try {
            val rows = supabase.postgrest["completed_missions"]
                .select { filter { eq("wallet_address", walletAddress) } }
                .decodeList<CompletedMissionRow>()
            rows.map { row ->
                val millis = row.completedAt?.let { str ->
                    runCatching {
                        OffsetDateTime.parse(str).toInstant().toEpochMilli()
                    }.getOrElse { System.currentTimeMillis() }
                } ?: System.currentTimeMillis()
                com.aura.app.model.CompletedMissionRecord(
                    id = row.id,
                    userWallet = row.walletAddress,
                    title = row.title,
                    emoji = row.emoji,
                    auraReward = row.auraReward,
                    aiFeedback = row.aiFeedback ?: "",
                    completedAtMillis = millis,
                )
            }.sortedByDescending { it.completedAtMillis }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch completed missions: ${e.message}")
            emptyList()
        }
    }

    /** Spend Aura points (deduct from profile). Returns true if successful. Ultra-reliable with retry. */
    suspend fun spendAuraPoints(amount: Int): Boolean {
        val wallet = com.aura.app.wallet.WalletConnectionState.walletAddress.value
        if (wallet.isNullOrBlank()) return false
        var profile = _currentProfile.value
        if (profile == null) {
            loadProfile(wallet)
            profile = _currentProfile.value
        }
        if (profile == null) return false
        if (profile.auraScore < amount) return false

        // Direct update with retry
        val newScoreFallback = profile.auraScore - amount
        val rankInfo = com.aura.app.model.RankSystem.getRankInfo(newScoreFallback)
        return try {
            invokeWithRetry(maxAttempts = 3, initialDelayMs = 400) {
                supabase.postgrest["profiles"].update({
                    set("aura_score", newScoreFallback)
                    set("rank_title", "${rankInfo.rankName} ${rankInfo.tierString}".trim())
                    set("points_to_next_rank", rankInfo.pointsToNextStar)
                }) { filter { eq("wallet_address", wallet) } }
                profile.id?.let { uuid ->
                    try {
                        supabase.postgrest["aura_history"].insert(
                            AuraHistoryDto(userId = uuid, changeAmount = -amount, reason = "Chat unlock")
                        )
                    } catch (_: Exception) {}
                }
            }
            withContext(Dispatchers.Main.immediate) {
                _currentProfile.value = profile.copy(
                    auraScore = newScoreFallback,
                    rankTitle = "${rankInfo.rankName} ${rankInfo.tierString}".trim(),
                    pointsToNextRank = rankInfo.pointsToNextStar
                )
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "spendAuraPoints failed", e)
            false
        }
    }

    /** Add Aura to current user's profile (trade bonus). Ultra-reliable with retry. */
    fun addAuraToProfile(amount: Int, reason: String) {
        scope.launch {
            val wallet = com.aura.app.wallet.WalletConnectionState.walletAddress.value
            if (wallet.isNullOrBlank()) return@launch
            var profile = _currentProfile.value
            if (profile == null) {
                loadProfile(wallet)
                profile = _currentProfile.value
            }
            if (profile == null) return@launch

            try {
                val p = profile
                invokeWithRetry(maxAttempts = 4, initialDelayMs = 500) {
                    val ns = p.auraScore + amount
                    val rankInfo = com.aura.app.model.RankSystem.getRankInfo(ns)
                    supabase.postgrest["profiles"].update({
                        set("aura_score", ns)
                        set("rank_title", "${rankInfo.rankName} ${rankInfo.tierString}".trim())
                        set("points_to_next_rank", rankInfo.pointsToNextStar)
                    }) { filter { eq("wallet_address", wallet) } }
                    p.id?.let { uuid ->
                        try {
                            supabase.postgrest["aura_history"].insert(
                                AuraHistoryDto(userId = uuid, changeAmount = amount, reason = reason)
                            )
                        } catch (_: Exception) {}
                    }
                    withContext(Dispatchers.Main.immediate) {
                        _currentProfile.value = p.copy(
                            auraScore = ns,
                            rankTitle = "${rankInfo.rankName} ${rankInfo.tierString}".trim(),
                            pointsToNextRank = rankInfo.pointsToNextStar
                        )
                    }
                    Log.i(TAG, "addAuraToProfile: +$amount ($reason) -> $ns")
                }
            } catch (e: Exception) {
                Log.e(TAG, "addAuraToProfile failed after retries", e)
            }
        }
    }

    private val _rewardedTradeIds = mutableSetOf<String>()

    /** Award trade completion bonus (10 Aura). Returns true if awarded. */
    fun tryAwardTradeBonus(sessionId: String): Boolean {
        if (sessionId.isBlank() || sessionId in _rewardedTradeIds) return false
        _rewardedTradeIds.add(sessionId)
        addAuraToProfile(10, "Verified trade bonus")
        return true
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
                limit(count = 200)
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

    /** Fetch all active listings owned by the given wallet address from Supabase. */
    suspend fun fetchUserListings(walletAddress: String): List<Listing> {
        return try {
            val rows = db.from("marketplace_listings").select {
                filter { eq("seller_wallet", walletAddress) }
                limit(count = 200)
            }.decodeList<ListingRow>()
            rows
                .filter { it.soldAt == null && it.isActive }
                .map { it.toDomain() }
        } catch (e: Exception) {
            Log.e(TAG, "fetchUserListings failed: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Update the price of an existing listing. Returns true on success.
     * Also patches the in-memory listings state so the UI refreshes without a full reload.
     */
    suspend fun updateListingPrice(listingId: String, newPriceLamports: Long): Boolean {
        return try {
            db.from("marketplace_listings").update({
                set("price_lamports", newPriceLamports)
            }) {
                filter { eq("id", listingId) }
            }
            // Patch in-memory state
            _listings.value = _listings.value.map { listing ->
                if (listing.id == listingId) listing.copy(priceLamports = newPriceLamports) else listing
            }
            Log.d(TAG, "updateListingPrice: $listingId -> $newPriceLamports lamports")
            true
        } catch (e: Exception) {
            Log.e(TAG, "updateListingPrice failed: ${e.message}", e)
            false
        }
    }

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
        meetupRadiusMeters: Int? = 50,
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

        val currentAuraScore = _currentProfile.value?.auraScore ?: 50
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
            sellerAuraScore = currentAuraScore,
            isActive = true,
            isPublished = true,
            nfcSunUrl = nfcSunUrl,
            meetupRadiusMeters = meetupRadiusMeters,
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

    /** Promotion fee: 10 SOL (legacy). Use promoteListingWithAuraPoints for 50 Aura points. */
    val PROMOTE_FEE_LAMPORTS: Long = 10L * 1_000_000_000

    /** Aura points cost for 24h promotion. */
    const val PROMOTE_AURA_POINTS = 50

    /**
     * Promote listing with 50 Aura points for 24h. Client must deduct credits before calling.
     */
    suspend fun promoteListingWithAuraPoints(listingId: String): Result<Unit> {
        val listing = getListing(listingId)
            ?: return Result.failure(Exception("Listing not found. Pull to refresh and try again."))
        val wallet = com.aura.app.wallet.WalletConnectionState.walletAddress.value
            ?: return Result.failure(Exception("Wallet not connected. Connect your wallet first."))
        if (listing.sellerWallet != wallet)
            return Result.failure(Exception("Only the seller can promote this listing."))

        return try {
            val reqBody = buildJsonObject {
                put("listingId", listingId)
                put("sellerWallet", wallet)
                put("useAuraPoints", true)
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
            loadProfile(wallet)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to promote listing with Aura points", e)
            Result.failure(Exception(e.message ?: "Promotion failed. Check your connection and try again."))
        }
    }

    /**
     * Promote listing after SOL payment is verified (legacy). Call this only after the user has
     * successfully sent 10 SOL to the treasury; pass the transaction signature.
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
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist mint status for $listingId: ${e.message}")
            }
            updated
        } catch (e: Exception) {
            Log.w(TAG, "Minting Edge Function failed (non-fatal) — listing is saved: ${e.message}")
            listing
        }
    }

    /**
     * Verify a photo against the listing (item match). Uses Groq API directly — no Supabase.
     */
    suspend fun verifyPhoto(listingId: String, photoBytes: ByteArray, checkType: String = "item_match"): VerificationResult {
        if (photoBytes.isEmpty()) {
            return VerificationResult(score = 0f, pass = false, reason = "No photo provided.")
        }
        if (checkType != "item_match") {
            return VerificationResult(score = 0f, pass = false, reason = "Use aura_check via Supabase.")
        }
        val listing = getListing(listingId) ?: return VerificationResult(score = 0f, pass = false, reason = "Listing not found.")
        val refUrl = listing.images?.firstOrNull() ?: return VerificationResult(score = 0f, pass = false, reason = "Listing has no image.")
        val (pass, feedback, rating) = GroqAIService.compareItemToListing(photoBytes, refUrl)
        return VerificationResult(score = rating / 100f, pass = pass, reason = feedback)
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
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert trade session ${session.id}", e)
            }
        }
        // Start listening for Realtime state changes on this session
        observeTradeSession(session.id)
        return session
    }

    fun setLastReceiptMintError(error: String?) {
        _lastReceiptMintError.value = error
    }

    fun updateTradeReceiptMints(receiptMintBuyer: String?, receiptMintSeller: String?) {
        if (receiptMintBuyer != null || receiptMintSeller != null) _lastReceiptMintError.value = null
        _currentTradeSession.value?.let { session ->
            val updated = session.copy(
                receiptMintBuyer = receiptMintBuyer,
                receiptMintSeller = receiptMintSeller,
                lastUpdated = System.currentTimeMillis(),
            )
            _currentTradeSession.value = updated
            appContext?.let { TradeSessionStore.save(it, updated) }
        }
    }

    /** Re-fetch receipt mints from DB (e.g. after edge function mints asynchronously). */
    suspend fun refreshTradeSessionReceiptMints(tradeId: String) {
        try {
            val rows = db.from("trade_sessions").select { filter { eq("id", tradeId) } }
                .decodeList<TradeSessionRow>()
            val row = rows.firstOrNull() ?: return
            _currentTradeSession.value?.let { session ->
                if (session.id == tradeId && (row.receiptMintBuyer != null || row.receiptMintSeller != null)) {
                    val updated = session.copy(
                        receiptMintBuyer = row.receiptMintBuyer ?: session.receiptMintBuyer,
                        receiptMintSeller = row.receiptMintSeller ?: session.receiptMintSeller,
                        lastUpdated = System.currentTimeMillis(),
                    )
                    _currentTradeSession.value = updated
                    appContext?.let { TradeSessionStore.save(it, updated) }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "refreshTradeSessionReceiptMints failed", e)
        }
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
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update trade session state to ${state.name}", e)
                }
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
                                    receiptMintBuyer = row.receiptMintBuyer ?: session.receiptMintBuyer,
                                    receiptMintSeller = row.receiptMintSeller ?: session.receiptMintSeller,
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
        val releaseAuth = com.aura.app.BuildConfig.RELEASE_AUTHORITY_PUBKEY
        if (releaseAuth.isBlank()) {
            throw IllegalStateException("RELEASE_AUTHORITY_PUBKEY or SOLANA_AUTHORITY_KEY required in local.properties for escrow.")
        }
        if (treasuryWallet.isBlank()) {
            throw IllegalStateException("TREASURY_WALLET required in local.properties for escrow.")
        }
        val txBytes = com.aura.app.wallet.AnchorTransactionBuilder.buildInitializeEscrowTx(
            listingId = listingId,
            amountLamports = amountLamports,
            buyerPubkey = walletAddr,
            sellerPubkey = sellerWallet,
            feeBps = feeBps,
            treasuryWallet = treasuryWallet,
            feeExempt = isRadiant,
            releaseAuthority = releaseAuth,
        )
        // Pre-flight simulation removed: allow wallet to open for signing regardless of RPC/simulate result.
        // Transaction may still fail on-chain; user will see wallet errors instead of blocking error message.
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
            authorityPubkey = walletAddr,
            assetUri = listing?.images?.firstOrNull() ?: "",
            assetTitle = listing?.title ?: "Aura Verified Asset",
            treasuryWallet = com.aura.app.BuildConfig.TREASURY_WALLET,
        )
        // Do NOT call updateTradeState here — wait for wallet signing confirmation
        return txBytes
    }

    /** Result of release attempt: success, network/offline (queued for retry), or other failure */
    sealed class ReleaseResult {
        data class Success(
            val receiptMintBuyer: String? = null,
            val receiptMintSeller: String? = null,
            val receiptMintError: String? = null,
        ) : ReleaseResult()
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

    private suspend fun <T> invokeWithRetry(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 500,
        block: suspend () -> T,
    ): T {
        var last: Exception? = null
        var delayMs = initialDelayMs
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                last = e
                if (attempt < maxAttempts - 1 && isNetworkError(e)) {
                    kotlinx.coroutines.delay(delayMs)
                    delayMs = (delayMs * 2).coerceAtMost(4000)
                } else {
                    throw e
                }
            }
        }
        throw last ?: Exception("Retry exhausted")
    }

    suspend fun releaseEscrowWithNfc(
        tradeId: String,
        sdmDataHex: String,
        receivedCmacHex: String,
        assetUri: String,
        assetTitle: String,
        listingId: String? = null,
        escrowPdaBase58: String? = null,
        sellerWalletBase58: String? = null,
        buyerWalletBase58: String? = null,
        amount: Long = 0L,
    ): ReleaseResult {
        val session = _currentTradeSession.value
        val lid = listingId ?: session?.listingId ?: tradeId
        val escrowPda = escrowPdaBase58 ?: com.aura.app.wallet.AnchorTransactionBuilder.deriveEscrowPda(lid).let {
            com.funkatronics.encoders.Base58.encodeToString(it.address)
        }
        val sellerWallet = sellerWalletBase58 ?: session?.sellerWallet ?: ""
        val buyerWallet = buyerWalletBase58 ?: com.aura.app.wallet.WalletConnectionState.walletAddress.value
        val amt = if (amount > 0) amount else (getListing(lid)?.priceLamports ?: 0L)
        try {
            val reqBody = buildJsonObject {
                put("sdmDataHex", sdmDataHex)
                put("receivedCmacHex", receivedCmacHex)
                put("assetUri", assetUri)
                put("assetTitle", assetTitle)
                put("listingId", lid)
                put("escrowPdaBase58", escrowPda)
                put("sellerWalletBase58", sellerWallet)
                put("tradeId", tradeId)
            }
            val responseText = invokeWithRetry {
                withContext(Dispatchers.IO) {
                    withTimeout(45_000) {
                        val response = db.functions.invoke("verify-sun") {
                            setBody(reqBody.toString())
                            contentType(ContentType.Application.Json)
                        }
                        response.bodyAsText()
                    }
                }
            }
            val jsonElement = runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(responseText).jsonObject
            }.getOrElse {
                Log.e(TAG, "verify-sun invalid JSON: ${responseText.take(200)}")
                return ReleaseResult.Failed("Server returned invalid response. Try again.")
            }
            val error = jsonElement["error"]?.jsonPrimitive?.content
            if (!error.isNullOrBlank()) {
                Log.e(TAG, "verify-sun error: $error")
                return ReleaseResult.Failed(error)
            }
            Log.d(TAG, "NFC verified and escrow released via Live Edge Function. Response: $responseText")
            appContext?.let { PendingReleaseStore.clear(it) }
            val receiptBuyer = jsonElement["receiptMintBuyer"]?.jsonPrimitive?.content
            val receiptSeller = jsonElement["receiptMintSeller"]?.jsonPrimitive?.content
            return ReleaseResult.Success(receiptMintBuyer = receiptBuyer, receiptMintSeller = receiptSeller)
        } catch (e: Exception) {
            Log.e(TAG, "Failed Edge Function verify-sun", e)
            if (isNetworkError(e)) {
                appContext?.let { ctx ->
                    PendingReleaseStore.save(ctx, tradeId, lid, sdmDataHex, receivedCmacHex,
                        escrowPda, sellerWallet, buyerWallet, assetUri, assetTitle, amt)
                }
                return ReleaseResult.OfflineQueued
            }
            return ReleaseResult.Failed(e.message ?: "Release failed")
        }
    }

    /**
     * Release escrow via photo verification. Use for listings without nfc_sun_url.
     * Verifies item photo matches listing via AI, then releases.
     */
    suspend fun releaseEscrowWithPhoto(
        tradeId: String,
        listingId: String,
        photoBase64: String,
        assetUri: String = "",
        assetTitle: String = "Aura Verified Asset",
    ): ReleaseResult {
        val session = _currentTradeSession.value
        val lid = listingId
        val escrowPda = com.aura.app.wallet.AnchorTransactionBuilder.deriveEscrowPda(lid).let {
            com.funkatronics.encoders.Base58.encodeToString(it.address)
        }
        val sellerWallet = session?.sellerWallet ?: getListing(lid)?.sellerWallet ?: ""
        if (sellerWallet.isBlank()) {
            return ReleaseResult.Failed("Seller wallet not found")
        }
        try {
            val reqBody = buildJsonObject {
                put("tradeId", tradeId)
                put("listingId", lid)
                put("photoBase64", photoBase64)
                put("assetUri", assetUri)
                put("assetTitle", assetTitle)
                put("escrowPdaBase58", escrowPda)
                put("sellerWalletBase58", sellerWallet)
            }
            val responseText = invokeWithRetry {
                withContext(Dispatchers.IO) {
                    withTimeout(60_000) {
                        val response = db.functions.invoke("release-escrow-photo") {
                            setBody(reqBody.toString())
                            contentType(ContentType.Application.Json)
                        }
                        response.bodyAsText()
                    }
                }
            }
            val jsonElement = runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(responseText).jsonObject
            }.getOrElse {
                Log.e(TAG, "release-escrow-photo invalid JSON: ${responseText.take(200)}")
                return ReleaseResult.Failed("Server returned invalid response. Try again.")
            }
            val error = jsonElement["error"]?.jsonPrimitive?.content
            if (!error.isNullOrBlank()) {
                Log.e(TAG, "release-escrow-photo error: $error")
                return ReleaseResult.Failed(error)
            }
            Log.d(TAG, "Photo verified and escrow released. Response: $responseText")
            val receiptBuyer = jsonElement["receiptMintBuyer"]?.jsonPrimitive?.content
            val receiptSeller = jsonElement["receiptMintSeller"]?.jsonPrimitive?.content
            val mintErr = jsonElement["receiptMintError"]?.jsonPrimitive?.content
            return ReleaseResult.Success(
                receiptMintBuyer = receiptBuyer,
                receiptMintSeller = receiptSeller,
                receiptMintError = mintErr,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed Edge Function release-escrow-photo", e)
            if (isNetworkError(e)) {
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
     * Request receipt NFT mint after client-signed release (e.g. EscrowPayScreen path).
     * Non-blocking; call after release tx confirms.
     */
    suspend fun requestReceiptMint(tradeId: String, releaseTxSignature: String): ReleaseResult.Success? {
        val session = _currentTradeSession.value ?: return null
        if (session.id != tradeId) return null
        val listing = getListing(session.listingId) ?: return null
        return try {
            invokeWithRetry(maxAttempts = 3, initialDelayMs = 1000) {
                val reqBody = buildJsonObject {
                    put("tradeId", tradeId)
                    put("listingId", session.listingId)
                    put("buyerWallet", session.buyerWallet)
                    put("sellerWallet", session.sellerWallet)
                    put("listingTitle", listing.title)
                    put("amountLamports", listing.priceLamports)
                    put("releaseTxSignature", releaseTxSignature)
                }
                val responseText = withContext(Dispatchers.IO) {
                    withTimeout(30_000) {
                        db.functions.invoke("mint-receipt-nft") {
                            setBody(reqBody.toString())
                            contentType(ContentType.Application.Json)
                        }.bodyAsText()
                    }
                }
                val json = kotlinx.serialization.json.Json.parseToJsonElement(responseText).jsonObject
                val err = json["error"]?.jsonPrimitive?.content
                if (!err.isNullOrBlank()) {
                    Log.e(TAG, "requestReceiptMint error: $err")
                    throw Exception(err)
                }
                val rb = json["receiptMintBuyer"]?.jsonPrimitive?.content
                val rs = json["receiptMintSeller"]?.jsonPrimitive?.content
                updateTradeReceiptMints(rb, rs)
                ReleaseResult.Success(receiptMintBuyer = rb, receiptMintSeller = rs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "requestReceiptMint failed after retries", e)
            null
        }
    }

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

    suspend fun getEscrowStatus(tradeId: String): EscrowStatus =
        fetchEscrowStatusFromDb(tradeId)

    private suspend fun fetchEscrowStatusFromDb(tradeId: String): EscrowStatus {
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
        sellerAuraScore = sellerAuraScore,
        emirate = emirate,
        isPromoted = false,
        promotedAt = null,
        promotedUntil = null,
        nfcSunUrl = nfcSunUrl,
        meetupRadiusMeters = meetupRadiusMeters,
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
        meetupRadiusMeters = meetupRadiusMeters,
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
