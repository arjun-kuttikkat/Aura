package com.aura.app.util

import android.location.Location
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Utilities for meetup location validation.
 * Addresses: Urban Canyon GPS drift, Drive-By exploit, Cross-Hemisphere lockout.
 */
object MeetupLocationUtils {

    /** Max distance (meters) to allow starting a meetup — prevents cross-hemisphere lockout. */
    const val MAX_MEETUP_DISTANCE_METERS = 500_000 // 500 km

    /** Geofence radius for "I am here" — relaxed for urban canyon multipath (50m). */
    const val GEOFENCE_RADIUS_METERS = 50f

    /** Strict radius for release (MeetSessionScreen) — 20m for physical handover. */
    const val RELEASE_GEOFENCE_METERS = 20f

    /** Consecutive polls within range required to prevent Drive-By exploit (5 × 2s = 10s sustained). */
    const val SUSTAINED_PROXIMITY_POLLS = 5

    /** Haversine distance in meters. */
    fun haversineMeters(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun distanceMeters(user: Location, targetLat: Double, targetLng: Double): Float {
        val target = Location("target").apply {
            latitude = targetLat
            longitude = targetLng
        }
        return user.distanceTo(target)
    }

    /** Urban Canyon fix: use 50m radius instead of 10m for geofence pass. */
    fun isWithinGeofence(distMeters: Float?, useStrictRadius: Boolean = false, customRadiusMeters: Float? = null): Boolean {
        if (distMeters == null) return false
        val radius = when {
            customRadiusMeters != null -> customRadiusMeters
            useStrictRadius -> RELEASE_GEOFENCE_METERS
            else -> GEOFENCE_RADIUS_METERS
        }
        return distMeters <= radius
    }

    /** Drive-By fix: require sustained proximity. Call repeatedly; returns true after N consecutive passes. */
    fun checkSustainedProximity(
        currentDistMeters: Float?,
        consecutiveInRange: Int,
        useStrictRadius: Boolean = false
    ): Boolean {
        if (currentDistMeters == null) return false
        if (!isWithinGeofence(currentDistMeters, useStrictRadius)) return false
        return consecutiveInRange >= SUSTAINED_PROXIMITY_POLLS
    }

    /** Cross-Hemisphere fix: buyer and seller too far to physically meet. */
    fun isWithinMeetupRange(
        buyerLat: Double?, buyerLng: Double?,
        sellerLat: Double?, sellerLng: Double?
    ): Boolean {
        if (buyerLat == null || buyerLng == null || sellerLat == null || sellerLng == null) return true
        val dist = haversineMeters(buyerLat, buyerLng, sellerLat, sellerLng)
        return dist <= MAX_MEETUP_DISTANCE_METERS
    }
}
