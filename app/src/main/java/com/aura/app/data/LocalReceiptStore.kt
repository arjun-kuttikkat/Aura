package com.aura.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Stores trade receipt locally when Supabase mint fails.
 * Provides proof of completed trade without relying on on-chain NFT.
 */
@Serializable
data class LocalReceipt(
    val tradeId: String,
    val listingTitle: String,
    val amountLamports: Long,
    val timestamp: Long,
    val role: String, // "buyer" or "seller"
    val counterpartyWallet: String,
)

object LocalReceiptStore {
    private const val PREFS = "aura_local_receipts"
    private const val PREFIX = "receipt_"

    fun save(ctx: Context?, tradeId: String, receipt: LocalReceipt) {
        ctx ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(PREFIX + tradeId, Json.encodeToString(receipt))
            .apply()
    }

    fun get(ctx: Context?, tradeId: String): LocalReceipt? {
        ctx ?: return null
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PREFIX + tradeId, null) ?: return null
        return runCatching { Json.decodeFromString<LocalReceipt>(json) }.getOrNull()
    }
}
