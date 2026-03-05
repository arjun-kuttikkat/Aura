package com.aura.app.data

import android.util.Log
import com.aura.app.model.Listing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import com.aura.app.BuildConfig

/**
 * AI Mood Therapist — used for the Aura Official Bot chat.
 * When a user chats with any Aura-official listing, this
 * responds as a warm, empathetic wellness guide that tracks
 * the user's mood and suggests real-world actions.
 */
object AiChatResponder {

    const val AURA_OFFICIAL_WALLET = "AURA_OFFICIAL_BOT"
    private const val TAG = "AiChatResponder"
    private const val BASE_URL = "https://api.cerebras.ai/v1/chat/completions"
    private const val MODEL = "gpt-oss-120b"

    private fun buildSystemPrompt(listing: Listing): String = """
You are Aura's personal wellness companion — warm, empathetic, and human.
You are responding in the context of the Aura marketplace. The user is browsing:

Listing: "${listing.title}"
Description: "${listing.description}"
Location: ${listing.emirate ?: "UAE"}
Price: ${listing.priceLamports / 1_000_000_000.0} SOL

Your PRIMARY role is NOT to sell the item — it's to connect with the user emotionally.

Conversation flow:
1. On the very first message: warmly greet them and check in on how they are feeling TODAY.
   e.g. "Hey! 👋 Before anything else — how are you doing today? Sometimes life gets heavy and I'd love to check in."
2. Once they share their mood, empathize genuinely (1-2 sentences). Then:
   - Track their mood: Happy 😊 / Stressed 😤 / Sad 😔 / Neutral 😐 / Anxious 😰
   - Suggest ONE real-world action they can do TODAY to boost their wellbeing.
3. Keep responses SHORT (max 3 sentences). Warm, not clinical. 
4. If they ask about the listing, answer briefly then gently bring it back to them.
5. Always end with a gentle open question to continue the conversation.
""".trimIndent()

    suspend fun generateReply(
        listing: Listing,
        conversationHistory: List<com.aura.app.model.ChatMessage>
    ): String = withContext(Dispatchers.IO) {
        try {
            val messages = buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", buildSystemPrompt(listing))
                })
                conversationHistory.takeLast(10).forEach { msg ->
                    val role = if (msg.senderWallet == AURA_OFFICIAL_WALLET) "assistant" else "user"
                    add(buildJsonObject {
                        put("role", role)
                        put("content", msg.content)
                    })
                }
            }

            val requestBody = buildJsonObject {
                put("model", MODEL)
                put("temperature", 0.85)
                put("max_tokens", 300)
                put("messages", messages)
            }

            val url = URL(BASE_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.CEREBRAS_API_KEY}")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.outputStream.use { it.write(requestBody.toString().toByteArray()) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = BufferedReader(InputStreamReader(stream)).use { it.readText() }

            if (code !in 200..299) throw Exception("Cerebras error $code: $response")

            val json = Json.parseToJsonElement(response)
            json.jsonObject["choices"]
                ?.jsonArray?.get(0)
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content
                ?: defaultReply()

        } catch (e: Exception) {
            Log.e(TAG, "AiChatResponder failed", e)
            defaultReply()
        }
    }

    private fun defaultReply() =
        "Hey there! 💙 How are you feeling today? I'd love to check in before we chat about anything else."
}
