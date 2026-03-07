package com.aura.app.data

import android.content.Context
import com.aura.app.model.TradeSession
import com.aura.app.model.TradeState
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists active trade session for Phantom Meetup State fix (#13) and Deep Link State Loss (#8).
 * When app restarts (e.g. after opening Google Maps), restore session so user can continue.
 */
@Serializable
private data class StoredTradeSession(
    val id: String,
    val listingId: String,
    val buyerWallet: String,
    val sellerWallet: String,
    val state: String,
    val createdAt: Long,
    val lastUpdated: Long,
)

object TradeSessionStore {
    private const val PREFS = "aura_trade_session"
    private const val KEY = "active_session"

    fun save(ctx: Context?, session: TradeSession?) {
        ctx ?: return
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (session == null) {
            prefs.edit().remove(KEY).apply()
            return
        }
        val stored = StoredTradeSession(
            id = session.id,
            listingId = session.listingId,
            buyerWallet = session.buyerWallet,
            sellerWallet = session.sellerWallet,
            state = session.state.name,
            createdAt = session.createdAt,
            lastUpdated = session.lastUpdated,
        )
        prefs.edit().putString(KEY, Json.encodeToString(stored)).apply()
    }

    fun load(ctx: Context?): TradeSession? {
        ctx ?: return null
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        return runCatching {
            val stored = Json.decodeFromString<StoredTradeSession>(json)
            TradeSession(
                id = stored.id,
                listingId = stored.listingId,
                buyerWallet = stored.buyerWallet,
                sellerWallet = stored.sellerWallet,
                state = runCatching { TradeState.valueOf(stored.state) }.getOrDefault(TradeState.SESSION_CREATED),
                createdAt = stored.createdAt,
                lastUpdated = stored.lastUpdated,
            )
        }.getOrNull()
    }

    fun hasActive(ctx: Context?): Boolean = load(ctx) != null
}
