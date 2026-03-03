package com.aura.app.data

import com.aura.app.model.ChatMessage
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.decodeRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

object ChatRepository {
    private val client = SupabaseClient.client

    suspend fun getMessagesForListing(listingId: String): List<ChatMessage> {
        return try {
            client.postgrest["chat_messages"]
                .select() {
                    filter {
                        eq("listing_id", listingId)
                    }
                }
                .decodeList<ChatMessage>()
                .sortedBy { it.createdAt }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getMyConversations(myWallet: String): List<ChatMessage> {
        return try {
            val allMessages = client.postgrest["chat_messages"]
                .select() {
                    filter {
                        or {
                            eq("sender_wallet", myWallet)
                            eq("receiver_wallet", myWallet)
                        }
                    }
                }
                .decodeList<ChatMessage>()
                .sortedByDescending { it.createdAt }
            
            // Group by listing ID and just return the latest message per conversation
            allMessages.groupBy { it.listingId }.map { it.value.first() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun sendMessage(message: ChatMessage) {
        try {
            client.postgrest["chat_messages"].insert(message)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun observeMessages(listingId: String): Flow<ChatMessage> {
        val channel = client.realtime.channel("chat_room_$listingId")
        val flow = channel.postgresChangeFlow<PostgresAction.Insert>("public") {
            table = "chat_messages"
        }
        channel.subscribe()
        return flow.mapNotNull { action ->
            try {
                val msg = action.decodeRecord<ChatMessage>()
                if (msg.listingId == listingId) msg else null
            } catch (e: Exception) {
                null
            }
        }
    }
}
