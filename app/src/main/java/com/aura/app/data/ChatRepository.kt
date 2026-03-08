package com.aura.app.data

import com.aura.app.model.ChatMessage
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.decodeRecord
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

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

    suspend fun markConversationAsRead(listingId: String, myWallet: String) {
        try {
            client.postgrest["chat_messages"].update(
                {
                    set("is_read", true)
                    set("read_at", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                }
            ) {
                filter {
                    eq("listing_id", listingId)
                    eq("receiver_wallet", myWallet)
                    eq("is_read", false)
                }
            }
        } catch (e: Exception) {
            // Keep non-fatal; old schemas may not include read fields yet.
            e.printStackTrace()
        }
    }

    /**
     * Observe new chat messages via Supabase Realtime.
     * Uses a unique channel ID per subscription so we never hit "You cannot call postgresChangeFlow
     * after joining the channel" when navigating back to this screen (the cached channel would
     * already be joined). Unsubscribes when the flow collector is cancelled (e.g. user leaves screen).
     */
    suspend fun observeMessages(listingId: String): Flow<ChatMessage> = callbackFlow {
        val channelId = "chat_room_${listingId}_${UUID.randomUUID()}"
        val ch = client.realtime.channel(channelId)
        val pgFlow = ch.postgresChangeFlow<PostgresAction.Insert>("public") {
            table = "chat_messages"
        }
        ch.subscribe()

        val job = launch {
            pgFlow.collect { action ->
                try {
                    val msg = action.decodeRecord<ChatMessage>()
                    if (msg.listingId == listingId) {
                        trySend(msg)
                    }
                } catch (_: Exception) {}
            }
        }

        awaitClose {
            job.cancel()
            runBlocking { try { ch.unsubscribe() } catch (_: Exception) {} }
        }
    }
}
