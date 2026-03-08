package com.aura.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AuraPreferences {
    private const val PREFS_NAME = "aura_prefs"
    private const val SECURE_PREFS_NAME = "aura_secure_prefs"
    private lateinit var prefs: SharedPreferences
    private var securePrefs: SharedPreferences? = null

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _transactionAlerts = MutableStateFlow(true)
    val transactionAlerts: StateFlow<Boolean> = _transactionAlerts.asStateFlow()

    private val _biometricsEnabled = MutableStateFlow(false)
    val biometricsEnabled: StateFlow<Boolean> = _biometricsEnabled.asStateFlow()

    private val _seedBackedUp = MutableStateFlow(false)
    val seedBackedUp: StateFlow<Boolean> = _seedBackedUp.asStateFlow()

    private val _publicProfile = MutableStateFlow(true)
    val publicProfile: StateFlow<Boolean> = _publicProfile.asStateFlow()

    private val _walletAddress = MutableStateFlow<String?>(null)
    val walletAddress: StateFlow<String?> = _walletAddress.asStateFlow()

    private val _identityVerified = MutableStateFlow(false)
    val identityVerified: StateFlow<Boolean> = _identityVerified.asStateFlow()

    private val _totalAuraEarned = MutableStateFlow(0)
    val totalAuraEarned: StateFlow<Int> = _totalAuraEarned.asStateFlow()

    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _bio = MutableStateFlow("")
    val bio: StateFlow<String> = _bio.asStateFlow()

    private val _avatarColorIndex = MutableStateFlow(0)
    val avatarColorIndex: StateFlow<Int> = _avatarColorIndex.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Encrypted prefs for sensitive auth tokens
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            securePrefs = EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w("AuraPrefs", "EncryptedSharedPreferences unavailable, falling back to standard prefs", e)
            securePrefs = null
        }
        _isDarkMode.value = prefs.getBoolean("dark_mode", true)
        _notificationsEnabled.value = prefs.getBoolean("notifications", true)
        _transactionAlerts.value = prefs.getBoolean("transaction_alerts", true)
        _biometricsEnabled.value = prefs.getBoolean("biometrics", false)
        _seedBackedUp.value = prefs.getBoolean("seed_backed_up", false)
        _publicProfile.value = prefs.getBoolean("public_profile", true)
        _walletAddress.value = prefs.getString("wallet_address", null)
        _identityVerified.value = prefs.getBoolean("identity_verified", false)
        _totalAuraEarned.value = prefs.getInt("total_aura_earned", 0)
        _displayName.value = prefs.getString("display_name", "") ?: ""
        _bio.value = prefs.getString("bio", "") ?: ""
        _avatarColorIndex.value = prefs.getInt("avatar_color_index", 0)
    }

    private fun isInitialized(): Boolean = ::prefs.isInitialized

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        if (isInitialized()) prefs.edit().putBoolean("dark_mode", enabled).apply()
    }

    fun setNotifications(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        if (isInitialized()) prefs.edit().putBoolean("notifications", enabled).apply()
    }

    fun setTransactionAlerts(enabled: Boolean) {
        _transactionAlerts.value = enabled
        if (isInitialized()) prefs.edit().putBoolean("transaction_alerts", enabled).apply()
    }

    fun setBiometrics(enabled: Boolean) {
        _biometricsEnabled.value = enabled
        if (isInitialized()) prefs.edit().putBoolean("biometrics", enabled).apply()
    }

    fun setSeedBackedUp(enabled: Boolean) {
        _seedBackedUp.value = enabled
        if (isInitialized()) prefs.edit().putBoolean("seed_backed_up", enabled).apply()
    }

    fun setPublicProfile(enabled: Boolean) {
        _publicProfile.value = enabled
        if (isInitialized()) prefs.edit().putBoolean("public_profile", enabled).apply()
    }

    fun setIdentityVerified(verified: Boolean) {
        _identityVerified.value = verified
        if (isInitialized()) prefs.edit().putBoolean("identity_verified", verified).apply()
    }

    fun addAuraReward(amount: Int) {
        val newTotal = _totalAuraEarned.value + amount
        _totalAuraEarned.value = newTotal
        if (isInitialized()) prefs.edit().putInt("total_aura_earned", newTotal).apply()
    }

    private val _rewardedTradeIds = mutableSetOf<String>()

    /** Award trade completion bonus (10 Aura). Returns true if awarded, false if already awarded for this trade. */
    fun tryAwardTradeBonus(sessionId: String): Boolean {
        if (sessionId.isBlank() || sessionId in _rewardedTradeIds) return false
        _rewardedTradeIds.add(sessionId)
        addAuraReward(10)
        return true
    }

    /** Spend Aura points. Returns true if successful, false if insufficient balance. */
    fun spendAuraPoints(amount: Int): Boolean {
        val current = _totalAuraEarned.value
        if (current < amount) return false
        val newTotal = current - amount
        _totalAuraEarned.value = newTotal
        if (isInitialized()) prefs.edit().putInt("total_aura_earned", newTotal).apply()
        return true
    }

    fun setDisplayName(name: String) {
        _displayName.value = name
        if (isInitialized()) prefs.edit().putString("display_name", name).apply()
    }

    fun setBio(bio: String) {
        _bio.value = bio
        if (isInitialized()) prefs.edit().putString("bio", bio).apply()
    }

    fun setAvatarColorIndex(index: Int) {
        _avatarColorIndex.value = index
        if (isInitialized()) prefs.edit().putInt("avatar_color_index", index).apply()
    }

    fun getRecentMissionHashes(): List<String> {
        return if (isInitialized()) prefs.getString("recent_mission_hashes", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList() else emptyList()
    }

    fun addMissionHash(hash: String) {
        val currentHashes = getRecentMissionHashes().toMutableList()
        currentHashes.add(0, hash)
        val trimmed = currentHashes.take(5).joinToString(",")
        if (isInitialized()) prefs.edit().putString("recent_mission_hashes", trimmed).apply()
    }

    fun setWalletInfo(address: String?, authToken: String?) {
        _walletAddress.value = address
        if (isInitialized()) {
            prefs.edit().putString("wallet_address", address).apply()
            val sp = securePrefs ?: prefs
            sp.edit().putString("auth_token", authToken).apply()
        }
    }

    fun getAuthToken(): String? {
        if (!isInitialized()) return null
        val sp = securePrefs ?: prefs
        return sp.getString("auth_token", null)
    }

    fun setSupabaseJwt(jwt: String?) {
        if (isInitialized()) {
            val sp = securePrefs ?: prefs
            sp.edit().putString("supabase_jwt", jwt).apply()
        }
    }

    fun getSupabaseJwt(): String? {
        if (!isInitialized()) return null
        val sp = securePrefs ?: prefs
        return sp.getString("supabase_jwt", null)
    }
}
