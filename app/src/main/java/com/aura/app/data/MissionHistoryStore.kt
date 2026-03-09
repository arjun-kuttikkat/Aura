package com.aura.app.data

import android.content.Context
import android.util.Log
import com.aura.app.model.CompletedMissionRecord
import com.aura.app.wallet.WalletConnectionState
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Handles fetching and persisting completed missions via Supabase.
 */
object MissionHistoryStore {
    private const val TAG = "MissionHistoryStore"

    fun historyFlow(context: Context): Flow<List<CompletedMissionRecord>> = flow {
        WalletConnectionState.walletAddress.collect { walletPubkey ->
            if (walletPubkey.isNullOrBlank()) {
                emit(emptyList())
            } else {
                emit(fetchHistoryOnce(walletPubkey))
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun fetchHistoryOnce(walletPubkey: String): List<CompletedMissionRecord> {
        return withContext(Dispatchers.IO) {
            try {
                SupabaseClient.client.postgrest["completed_missions"]
                    .select {
                        filter { eq("user_wallet", walletPubkey) }
                        order("completed_at_millis", Order.DESCENDING)
                        limit(50)
                    }
                    .decodeList<CompletedMissionRecord>()
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching mission history", e)
                emptyList()
            }
        }
    }

    suspend fun addRecord(context: Context, record: CompletedMissionRecord) {
        withContext(Dispatchers.IO) {
            try {
                SupabaseClient.client.postgrest["completed_missions"].insert(record)
            } catch (e: Exception) {
                Log.e(TAG, "Error adding mission record", e)
            }
        }
    }
}
