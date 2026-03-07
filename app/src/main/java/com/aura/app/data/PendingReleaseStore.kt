package com.aura.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Dead Zone fix: queue escrow release when offline; retry when back online */
@Serializable
data class PendingRelease(
    val tradeId: String,
    val listingId: String,
    val sdmDataHex: String,
    val receivedCmacHex: String,
    val escrowPdaBase58: String,
    val sellerWalletBase58: String,
    val buyerWalletBase58: String?,
    val assetUri: String,
    val assetTitle: String,
    val amount: Long,
)

object PendingReleaseStore {
    private const val PREFS = "aura_pending_release"
    private const val KEY = "pending_release"

    fun save(
        ctx: Context?,
        tradeId: String,
        listingId: String,
        sdmDataHex: String,
        receivedCmacHex: String,
        escrowPdaBase58: String,
        sellerWalletBase58: String,
        buyerWalletBase58: String?,
        assetUri: String,
        assetTitle: String,
        amount: Long,
    ) {
        ctx ?: return
        val pending = PendingRelease(
            tradeId, listingId, sdmDataHex, receivedCmacHex, escrowPdaBase58,
            sellerWalletBase58, buyerWalletBase58, assetUri, assetTitle, amount
        )
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, Json.encodeToString(pending))
            .apply()
    }

    fun load(ctx: Context?): PendingRelease? {
        ctx ?: return null
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        return runCatching { Json.decodeFromString<PendingRelease>(json) }.getOrNull()
    }

    fun clear(ctx: Context?) {
        ctx ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    fun hasPending(ctx: Context?): Boolean = load(ctx) != null
}
