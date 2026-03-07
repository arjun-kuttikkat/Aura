package com.aura.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Background Process Kill fix: persist pending escrow tx sig so we can resume confirmation check on app restart */
@Serializable
data class PendingEscrowConfirmation(
    val tradeId: String,
    val txSignature: String,
    val createdAtMs: Long,
)

object PendingEscrowConfirmationStore {
    private const val PREFS = "aura_pending_escrow_confirm"
    private const val KEY = "pending_escrow"

    fun save(
        ctx: Context?,
        tradeId: String,
        txSignature: String,
    ) {
        ctx ?: return
        val pending = PendingEscrowConfirmation(
            tradeId = tradeId,
            txSignature = txSignature,
            createdAtMs = System.currentTimeMillis(),
        )
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, Json.encodeToString(pending))
            .apply()
    }

    fun load(ctx: Context?): PendingEscrowConfirmation? {
        ctx ?: return null
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        return runCatching { Json.decodeFromString<PendingEscrowConfirmation>(json) }.getOrNull()
    }

    fun clear(ctx: Context?) {
        ctx ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    fun hasPending(ctx: Context?): Boolean = load(ctx) != null
}
