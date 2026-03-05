package com.aura.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import com.aura.app.model.Hotzone
import com.aura.app.model.HotzoneRow
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Manages Hotzone territorial control — GPS-bound H3 hex grids
 * where sellers compete for Apex Local status and algorithmic listing dominance.
 *
 * Uses FusedLocationProviderClient for GPS and Supabase `hotzones` table for persistence.
 */
object HotzoneManager {
    private const val TAG = "HotzoneManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var fusedClient: FusedLocationProviderClient? = null

    // ── Observable State ─────────────────────────────────────────────
    private val _nearbyZones = MutableStateFlow<List<Hotzone>>(emptyList())
    val nearbyZones: StateFlow<List<Hotzone>> = _nearbyZones.asStateFlow()

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ── Initialization ───────────────────────────────────────────────
    fun init(context: Context) {
        fusedClient = LocationServices.getFusedLocationProviderClient(context)
    }

    // ── Location ─────────────────────────────────────────────────────
    @SuppressLint("MissingPermission")
    fun refreshLocation() {
        scope.launch {
            try {
                val location = fusedClient
                    ?.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    ?.await()
                if (location != null) {
                    _currentLocation.value = location
                    fetchNearbyZones(location.latitude, location.longitude)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Location fetch failed", e)
            }
        }
    }

    // ── Supabase CRUD ────────────────────────────────────────────────
    private suspend fun fetchNearbyZones(lat: Double, lng: Double, radiusKm: Double = 8.0) {
        _isLoading.value = true
        try {
            // Fetch all zones and filter by Haversine distance client-side
            // (Supabase Postgrest doesn't have built-in geo queries)
            val rows = SupabaseClient.client.postgrest
                .from("hotzones")
                .select()
                .decodeList<HotzoneRow>()

            val nearby = rows
                .map { it.toDomain(lat, lng) }
                .filter { (it.distanceMeters ?: Int.MAX_VALUE) <= (radiusKm * 1000).toInt() }
                .sortedBy { it.distanceMeters }

            _nearbyZones.value = nearby
        } catch (e: Exception) {
            Log.e(TAG, "Hotzone fetch failed", e)
            // Fallback to demo zones for hackathon demo
            _nearbyZones.value = generateDemoZones(lat, lng)
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun claimZone(zoneId: String, walletAddress: String, proofHash: String): Boolean {
        return try {
            SupabaseClient.client.postgrest
                .from("hotzones")
                .update({
                    set("apex_wallet", walletAddress)
                    set("apex_streak", 1)
                    set("apex_volume", 1)
                    set("is_refined", true)
                    set("gravity", 1.0)
                }) {
                    filter {
                        eq("id", zoneId)
                    }
                }
            // Refresh the zone list
            _currentLocation.value?.let { fetchNearbyZones(it.latitude, it.longitude) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Zone claim failed", e)
            false
        }
    }

    /** Increment trade volume and recalculate gravity for the user's zone. */
    suspend fun recordTradeInZone(zoneId: String, walletAddress: String, streakDays: Int) {
        try {
            // Fetch current zone data
            val rows = SupabaseClient.client.postgrest
                .from("hotzones")
                .select {
                    filter { eq("id", zoneId) }
                }
                .decodeList<HotzoneRow>()

            val zone = rows.firstOrNull() ?: return

            if (zone.apexWallet == walletAddress) {
                // Incumbent: increment volume and recalculate gravity
                val newVolume = zone.apexVolume + 1
                val newGravity = calculateGravity(streakDays, newVolume)
                SupabaseClient.client.postgrest
                    .from("hotzones")
                    .update({
                        set("apex_volume", newVolume)
                        set("apex_streak", streakDays)
                        set("gravity", newGravity)
                    }) {
                        filter { eq("id", zoneId) }
                    }
            } else {
                // Challenger: check if they surpass the Apex
                val challengerGravity = calculateGravity(streakDays, 1)
                if (challengerGravity > zone.gravity) {
                    // Dethrone the Apex
                    SupabaseClient.client.postgrest
                        .from("hotzones")
                        .update({
                            set("apex_wallet", walletAddress)
                            set("apex_streak", streakDays)
                            set("apex_volume", 1)
                            set("gravity", challengerGravity)
                        }) {
                            filter { eq("id", zoneId) }
                        }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Trade recording failed", e)
        }
    }

    // ── Gravity Calculation ──────────────────────────────────────────
    private fun calculateGravity(streak: Int, volume: Int): Double {
        // Gravity = streak * volume * recency_weight
        // Higher streak + higher trade volume = dominant gravitational pull
        return streak.toDouble() * volume.toDouble() * 1.0
    }

    // ── Demo Data ────────────────────────────────────────────────────
    private fun generateDemoZones(lat: Double, lng: Double): List<Hotzone> {
        return listOf(
            Hotzone("h3_central_mall", "Central Mall", lat + 0.001, lng + 0.002, null, 0, 0, 0.0, 0xFFFF9800, false, 45),
            Hotzone("h3_tech_hub", "Tech Hub Lobby", lat - 0.002, lng + 0.001, "AuRaVa...9821", 12, 28, 336.0, 0xFFE65100, true, 120),
            Hotzone("h3_university", "University Library", lat + 0.003, lng - 0.001, null, 0, 0, 0.0, 0xFFFF9800, false, 310),
            Hotzone("h3_station", "Downtown Station", lat - 0.005, lng + 0.004, "XyZ12...ABcD", 45, 92, 4140.0, 0xFFFF3B30, true, 890),
        )
    }
}
