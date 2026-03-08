package com.aura.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Stores buyer's preferred meetup location and time per listing.
 * Used when "Request your preferred location and timing" is set in chat;
 * MeetLocationScreen uses this location instead of listing default.
 */
@Serializable
data class MeetupPreferences(
    val listingId: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val preferredTime: String, // e.g. "14:00" (30min interval)
)

object MeetupPreferencesStore {
    private const val PREFS = "aura_meetup_preferences"
    private val json = Json { ignoreUnknownKeys = true }

    fun save(ctx: Context?, listingId: String, prefs: MeetupPreferences) {
        ctx ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key(listingId), json.encodeToString(prefs))
            .apply()
    }

    fun load(ctx: Context?, listingId: String): MeetupPreferences? {
        ctx ?: return null
        val jsonStr = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(listingId), null) ?: return null
        return runCatching { json.decodeFromString<MeetupPreferences>(jsonStr) }.getOrNull()
    }

    private fun key(listingId: String) = "meetup_$listingId"
}
