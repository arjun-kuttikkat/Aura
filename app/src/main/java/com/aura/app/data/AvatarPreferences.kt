package com.aura.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aura.app.model.AvatarConfig
import com.aura.app.model.StoreCatalog
import com.aura.app.model.StoreItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aura_prefs")

/**
 * Persists avatar config, credits balance, purchased items, and onboarding state.
 */
object AvatarPreferences {
    private val purchaseMutex = Mutex()


    private val KEY_CREDITS       = intPreferencesKey("credits")
    private val KEY_AVATAR_DONE   = booleanPreferencesKey("avatar_created")
    private val KEY_SKIN          = intPreferencesKey("av_skin")
    private val KEY_EYE_SHAPE     = intPreferencesKey("av_eye_shape")
    private val KEY_EYE_COLOR     = intPreferencesKey("av_eye_color")
    private val KEY_EYEBROW       = intPreferencesKey("av_eyebrow")
    private val KEY_NOSE           = intPreferencesKey("av_nose")
    private val KEY_MOUTH          = intPreferencesKey("av_mouth")
    private val KEY_FACE           = intPreferencesKey("av_face")
    private val KEY_HAIR_STYLE    = intPreferencesKey("av_hair_style")
    private val KEY_HAIR_COLOR    = intPreferencesKey("av_hair_color")
    private val KEY_OUTFIT_TOP    = intPreferencesKey("av_outfit_top")
    private val KEY_OUTFIT_BOTTOM = intPreferencesKey("av_outfit_bottom")
    private val KEY_OUTFIT_COLOR  = intPreferencesKey("av_outfit_color")
    private val KEY_HAT           = intPreferencesKey("av_hat")
    private val KEY_GLASSES       = intPreferencesKey("av_glasses")
    private val KEY_BACKGROUND    = intPreferencesKey("av_background")
    private val KEY_EXPRESSION    = intPreferencesKey("av_expression")
    private val KEY_UNLOCKED      = stringPreferencesKey("av_unlocked_items")
    private val KEY_EQUIPPED      = stringPreferencesKey("av_equipped_items")

    // ── Store Mechanics ────────────────────────────────────────────────────────
    private val KEY_OWNED_PROTECTION_CARDS = intPreferencesKey("owned_protection_cards")
    private val KEY_LAST_PROTECTION_PURCHASE = androidx.datastore.preferences.core.longPreferencesKey("last_protection_purchase_time")

    // ── Credits ──────────────────────────────────────────────────────────────

    fun creditsFlow(context: Context): Flow<Int> =
        context.dataStore.data.map { it[KEY_CREDITS] ?: 50 }

    suspend fun getCredits(context: Context): Int =
        context.dataStore.data.first()[KEY_CREDITS] ?: 50

    suspend fun addCredits(context: Context, amount: Int) {
        context.dataStore.edit { it[KEY_CREDITS] = (it[KEY_CREDITS] ?: 50) + amount }
    }

    suspend fun deductCredits(context: Context, amount: Int): Boolean {
        val current = getCredits(context)
        if (current < amount) return false
        context.dataStore.edit { it[KEY_CREDITS] = current - amount }
        return true
    }

    // ── Protection Cards ──────────────────────────────────────────────────────

    fun protectionCardsFlow(context: Context): Flow<Int> =
        context.dataStore.data.map { it[KEY_OWNED_PROTECTION_CARDS] ?: 0 }

    suspend fun getOwnedProtectionCards(context: Context): Int =
        context.dataStore.data.first()[KEY_OWNED_PROTECTION_CARDS] ?: 0

    suspend fun getLastProtectionPurchaseTime(context: Context): Long =
        context.dataStore.data.first()[KEY_LAST_PROTECTION_PURCHASE] ?: 0L

    suspend fun buyProtectionCard(context: Context): Boolean = purchaseMutex.withLock {
        // Allow buying once per week (7 * 24 * 60 * 60 * 1000)
        val ONE_WEEK_MS = 604800000L
        val lastBuy = getLastProtectionPurchaseTime(context)
        val now = System.currentTimeMillis()
        if (now - lastBuy < ONE_WEEK_MS) return false

        // Perform ATOMIC deduction and grant
        context.dataStore.edit { prefs ->
            val currentCredits = prefs[KEY_CREDITS] ?: 50
            if (currentCredits < 500) {
                // Return implicitly fails the edit if we throw or return early, 
                // but we can just check and return false later.
            } else {
                prefs[KEY_CREDITS] = currentCredits - 500
                prefs[KEY_OWNED_PROTECTION_CARDS] = (prefs[KEY_OWNED_PROTECTION_CARDS] ?: 0) + 1
                prefs[KEY_LAST_PROTECTION_PURCHASE] = now
            }
        }
        
        // Final verification check
        val finalLastBuy = getLastProtectionPurchaseTime(context)
        return finalLastBuy == now
    }

    suspend fun consumeProtectionCard(context: Context): Boolean {
        val owned = getOwnedProtectionCards(context)
        if (owned <= 0) return false
        
        context.dataStore.edit {
            it[KEY_OWNED_PROTECTION_CARDS] = owned - 1
        }
        return true
    }

    // ── Avatar Created Flag ───────────────────────────────────────────────────

    fun avatarCreatedFlow(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[KEY_AVATAR_DONE] ?: false }

    suspend fun isAvatarCreated(context: Context): Boolean =
        context.dataStore.data.first()[KEY_AVATAR_DONE] ?: false

    // ── Avatar Config ─────────────────────────────────────────────────────────

    suspend fun saveAvatarConfig(context: Context, config: AvatarConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AVATAR_DONE]   = true
            prefs[KEY_SKIN]          = config.skinTone
            prefs[KEY_EYE_SHAPE]     = config.eyeShape
            prefs[KEY_EYE_COLOR]     = config.eyeColor
            prefs[KEY_EYEBROW]       = config.eyebrowStyle
            prefs[KEY_NOSE]          = config.noseStyle
            prefs[KEY_MOUTH]         = config.mouthStyle
            prefs[KEY_FACE]          = config.faceShape
            prefs[KEY_HAIR_STYLE]    = config.hairStyle
            prefs[KEY_HAIR_COLOR]    = config.hairColor
            prefs[KEY_OUTFIT_TOP]    = config.outfitTop
            prefs[KEY_OUTFIT_BOTTOM] = config.outfitBottom
            prefs[KEY_OUTFIT_COLOR]  = config.outfitColor
            prefs[KEY_HAT]           = config.hat
            prefs[KEY_GLASSES]       = config.glasses
            prefs[KEY_BACKGROUND]    = config.background
            prefs[KEY_EXPRESSION]    = config.expression
            prefs[KEY_EQUIPPED]      = config.equippedItems.joinToString(",")
        }
    }

    fun avatarConfigFlow(context: Context): Flow<AvatarConfig> =
        context.dataStore.data.map { prefs ->
            AvatarConfig(
                skinTone    = prefs[KEY_SKIN]          ?: 0,
                eyeShape    = prefs[KEY_EYE_SHAPE]     ?: 0,
                eyeColor    = prefs[KEY_EYE_COLOR]     ?: 0,
                eyebrowStyle= prefs[KEY_EYEBROW]       ?: 0,
                noseStyle   = prefs[KEY_NOSE]          ?: 0,
                mouthStyle  = prefs[KEY_MOUTH]         ?: 0,
                faceShape   = prefs[KEY_FACE]          ?: 0,
                hairStyle   = prefs[KEY_HAIR_STYLE]    ?: 0,
                hairColor   = prefs[KEY_HAIR_COLOR]    ?: 0,
                outfitTop   = prefs[KEY_OUTFIT_TOP]    ?: 0,
                outfitBottom= prefs[KEY_OUTFIT_BOTTOM] ?: 0,
                outfitColor = prefs[KEY_OUTFIT_COLOR]  ?: 0,
                hat         = prefs[KEY_HAT]           ?: -1,
                glasses     = prefs[KEY_GLASSES]       ?: -1,
                background  = prefs[KEY_BACKGROUND]    ?: 0,
                expression  = prefs[KEY_EXPRESSION]    ?: 0,
                equippedItems = prefs[KEY_EQUIPPED]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            )
        }

    // ── Store Purchases ───────────────────────────────────────────────────────

    fun unlockedItemsFlow(context: Context): Flow<Set<String>> =
        context.dataStore.data.map { prefs ->
            prefs[KEY_UNLOCKED]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        }

    suspend fun purchaseItem(context: Context, item: StoreItem): Boolean {
        var success = false
        context.dataStore.edit { prefs ->
            val currentCredits = prefs[KEY_CREDITS] ?: 50
            if (currentCredits >= item.creditCost) {
                // Atomic deduction + Grant
                prefs[KEY_CREDITS] = currentCredits - item.creditCost
                
                val currentUnlocked = prefs[KEY_UNLOCKED] ?: ""
                val ids = currentUnlocked.split(",").filter { it.isNotBlank() }.toMutableSet()
                ids.add(item.id)
                prefs[KEY_UNLOCKED] = ids.joinToString(",")
                success = true
            }
        }
        return success
    }

    suspend fun equipItem(context: Context, item: StoreItem) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_EQUIPPED] ?: ""
            val ids = current.split(",").filter { it.isNotBlank() }.toMutableSet()
            // Only one item per slot per category
            val toRemove = StoreCatalog.byCategory(item.category).map { it.id }.toSet()
            ids.removeAll(toRemove)
            ids.add(item.id)
            prefs[KEY_EQUIPPED] = ids.joinToString(",")
            // Also update the avatar slot
            when (item.avatarSlot) {
                "hairStyle"   -> prefs[KEY_HAIR_STYLE]    = item.slotIndex
                "outfitTop"   -> prefs[KEY_OUTFIT_TOP]    = item.slotIndex
                "hat"         -> prefs[KEY_HAT]           = item.slotIndex
                "glasses"     -> prefs[KEY_GLASSES]       = item.slotIndex
                "background"  -> prefs[KEY_BACKGROUND]    = item.slotIndex
                "expression"  -> prefs[KEY_EXPRESSION]    = item.slotIndex
            }
        }
    }

    suspend fun resetAvatar(context: Context) {
        context.dataStore.edit { it[KEY_AVATAR_DONE] = false }
    }
}
