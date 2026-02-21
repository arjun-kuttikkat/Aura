package com.aura.app.data

import android.util.Log
import com.aura.app.model.AuraHistoryDto
import com.aura.app.model.ProfileDto
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object AuraRepository {

    private val supabase = SupabaseClient.client
    private val _currentProfile = MutableStateFlow<ProfileDto?>(null)
    val currentProfile: StateFlow<ProfileDto?> = _currentProfile.asStateFlow()

    suspend fun loadProfile(walletAddress: String) {
        if (walletAddress.isEmpty()) {
            _currentProfile.value = null
            return
        }

        try {
            val profiles = supabase.postgrest["profiles"]
                .select { 
                    filter { eq("wallet_address", walletAddress) }
                }
                .decodeList<ProfileDto>()
            
            if (profiles.isNotEmpty()) {
                _currentProfile.value = profiles.first()
            } else {
                // Creates a new profile
                val newProfile = ProfileDto(walletAddress = walletAddress)
                val insertedList = supabase.postgrest["profiles"]
                    .insert(newProfile) {
                        select()
                    }
                    .decodeList<ProfileDto>()
                if (insertedList.isNotEmpty()) {
                    _currentProfile.value = insertedList.first()
                }
            }
        } catch (e: Exception) {
            Log.e("AuraRepository", "Error loading profile", e)
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
                    
                    if (hoursSince > 48) {
                        streak = 1 // Streak broken
                    } else if (hoursSince >= 24) {
                        streak += 1
                    }
                    // Else, streak is maintained but not increased, wait for next day
                } catch (e:Exception) {
                    streak += 1
                }
            } else {
                streak += 1
            }

            val updatedProfile = profile.copy(
                auraScore = newScore,
                streakDays = streak,
                lastScanAt = nowStr
            )

            // Update profile
            supabase.postgrest["profiles"].update({
                set("aura_score", updatedProfile.auraScore)
                set("streak_days", updatedProfile.streakDays)
                set("last_scan_at", updatedProfile.lastScanAt)
            }) {
                filter { eq("wallet_address", profile.walletAddress) }
            }

            _currentProfile.value = updatedProfile

            // Record history event
            profile.id?.let { uuid ->
                val history = AuraHistoryDto(
                    userId = uuid,
                    changeAmount = scoreGained,
                    reason = "Completed Mirror Ritual"
                )
                supabase.postgrest["aura_history"].insert(history)
            }

            Log.i("AuraRepository", "Mirror Ritual Completed: +$scoreGained score, Streak: $streak")
        } catch (e: Exception) {
            Log.e("AuraRepository", "Error completing mirror ritual", e)
        }
    }
}
