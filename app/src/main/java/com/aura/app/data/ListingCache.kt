package com.aura.app.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aura.app.model.Listing
import com.aura.app.model.MintedStatus
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

private val Context.listingCacheStore by preferencesDataStore("listing_cache")
private val CACHE_KEY = stringPreferencesKey("cached_listings_json_v2")

@Serializable
data class CachedListing(
    val id: String,
    val sellerWallet: String,
    val title: String,
    val description: String = "",
    val priceLamports: Long,
    val images: List<String> = emptyList(),
    val condition: String = "Good",
    val mintedStatus: String = "PENDING",
    val mintAddress: String? = null,
    val fingerprintHash: String = "",
    val createdAt: Long = 0L,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val location: String? = null,
    val emirate: String? = null,
    val sellerAuraScore: Int = 50,
    val isPromoted: Boolean = false,
    val promotedAt: Long? = null,
    val promotedUntil: Long? = null,
)

object ListingCache {

    private const val TAG = "ListingCache"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun save(context: Context, listings: List<Listing>) {
        try {
            val cached = listings.map { it.toCached() }
            val encoded = json.encodeToString(cached)
            context.listingCacheStore.edit { it[CACHE_KEY] = encoded }
            Log.d(TAG, "Cached ${listings.size} listings to disk")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cache", e)
        }
    }

    suspend fun load(context: Context): List<Listing> {
        return try {
            val prefs = context.listingCacheStore.data.firstOrNull()
            val encoded = prefs?.get(CACHE_KEY) ?: return emptyList()
            val cached = json.decodeFromString<List<CachedListing>>(encoded)
            cached.map { it.toListing() }.also {
                Log.d(TAG, "Loaded ${it.size} listings from cache")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cache", e)
            emptyList()
        }
    }

    private fun Listing.toCached() = CachedListing(
        id = id,
        sellerWallet = sellerWallet,
        title = title,
        description = description,
        priceLamports = priceLamports,
        images = images,
        condition = condition,
        mintedStatus = mintedStatus.name,
        mintAddress = mintAddress,
        fingerprintHash = fingerprintHash,
        createdAt = createdAt,
        latitude = latitude,
        longitude = longitude,
        location = location,
        emirate = emirate,
        sellerAuraScore = sellerAuraScore,
        isPromoted = isPromoted,
        promotedAt = promotedAt,
        promotedUntil = promotedUntil
    )

    private fun CachedListing.toListing() = Listing(
        id = id,
        sellerWallet = sellerWallet,
        title = title,
        description = description,
        priceLamports = priceLamports,
        images = images,
        condition = condition,
        mintedStatus = runCatching { MintedStatus.valueOf(mintedStatus.trim().uppercase()) }.getOrDefault(MintedStatus.PENDING),
        mintAddress = mintAddress,
        fingerprintHash = fingerprintHash,
        createdAt = createdAt,
        latitude = latitude,
        longitude = longitude,
        location = location,
        emirate = emirate,
        sellerAuraScore = sellerAuraScore,
        isPromoted = isPromoted,
        promotedAt = promotedAt,
        promotedUntil = promotedUntil
    )
}
