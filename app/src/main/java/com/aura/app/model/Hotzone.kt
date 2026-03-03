package com.aura.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents an H3-indexed Hotzone — a physical GPS-bound territory
 * that sellers compete to control for algorithmic listing dominance.
 */
data class Hotzone(
    val id: String,           // H3 hex index or simplified grid ID
    val name: String,         // Reverse-geocoded human label
    val lat: Double,
    val lng: Double,
    val apexWallet: String?,  // Current Apex Local owner (null = unclaimed)
    val apexStreak: Int,      // Apex holder's unbroken zone streak
    val apexVolume: Int,      // Apex holder's trade count in zone
    val gravity: Double,      // Computed: streak * volume * recency
    val color: Long,          // Zone-specific hue (ARGB)
    val isRefined: Boolean,   // Has been camera-scanned by any user
    val distanceMeters: Int? = null, // Calculated client-side
)

/**
 * Supabase row DTO for the `hotzones` table.
 */
@Serializable
data class HotzoneRow(
    val id: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    @SerialName("apex_wallet") val apexWallet: String? = null,
    @SerialName("apex_streak") val apexStreak: Int = 0,
    @SerialName("apex_volume") val apexVolume: Int = 0,
    val gravity: Double = 0.0,
    val color: Long = 0xFFFF9800,
    @SerialName("is_refined") val isRefined: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
) {
    fun toDomain(userLat: Double? = null, userLng: Double? = null): Hotzone {
        val distance = if (userLat != null && userLng != null) {
            calculateDistanceMeters(userLat, userLng, lat, lng)
        } else null
        return Hotzone(
            id = id, name = name, lat = lat, lng = lng,
            apexWallet = apexWallet, apexStreak = apexStreak, apexVolume = apexVolume,
            gravity = gravity, color = color, isRefined = isRefined,
            distanceMeters = distance,
        )
    }
}

/** Haversine distance in meters between two GPS points. */
private fun calculateDistanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Int {
    val r = 6371000.0 // Earth radius in meters
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLng / 2) * Math.sin(dLng / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return (r * c).toInt()
}
