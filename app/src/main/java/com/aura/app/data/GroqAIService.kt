package com.aura.app.data

import android.util.Base64
import android.util.Log
import com.aura.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * GroqAIService
 *
 * Uses Groq API with meta-llama/llama-4-scout-17b-16e-instruct for:
 * 1. Listing image analysis - verifying product relevance and auto-tagging
 * 2. Directives AI chat - personalized mission generation and verification
 */
object GroqAIService {

    private const val TAG = "GroqAIService"
    private const val BASE_URL = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL = "meta-llama/llama-4-scout-17b-16e-instruct"

    data class ListingAnalysis(
        val isRelevant: Boolean,
        val title: String,
        val description: String,
        val category: String,
        val tags: List<String>,
        val condition: String,
        val rejectionReason: String? = null
    )

    data class ChatMessage(
        val role: String, // "user" or "assistant"
        val content: String
    )

    /**
     * Analyzes a product image to determine if it's a valid physical item
     * suitable for the Aura marketplace, and generates tags/labels.
     *
     * @param imageBytes Raw bytes of the captured image
     * @return ListingAnalysis with all generated metadata
     */
    suspend fun analyzeProductImage(imageBytes: ByteArray): ListingAnalysis = withContext(Dispatchers.IO) {
        try {
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val systemPrompt = """You are an AI assistant for Aura, a peer-to-peer marketplace on Solana.
Your role is to analyze product photos and determine if they are valid physical items for sale.

A product is RELEVANT if it is a physical item someone might buy or sell:
- Electronics, gadgets, phones, computers, accessories
- Clothing, shoes, bags, watches, jewelry
- Sports equipment, fitness gear
- Home goods, furniture, appliances
- Books, collectibles, toys, games
- Vehicles parts, tools

A product is NOT RELEVANT if it is:
- A random photo of a person (selfie), nature, food, or non-product
- Screenshot, meme, or digital content
- A completely blurry or undiscernible image

Respond in JSON only with this exact format:
{
  "isRelevant": true/false,
  "title": "Concise product title (max 6 words)",
  "description": "Short description of the item (1-2 sentences)",
  "category": "One of: Electronics, Fashion, Sports, Home, Books, Collectibles, Tools, Other",
  "tags": ["tag1", "tag2", "tag3"],
  "condition": "One of: New, Like New, Good, Fair",
  "rejectionReason": "Reason if not relevant, else null"
}"""

            val requestBody = buildJsonObject {
                put("model", MODEL)
                put("temperature", 0.3)
                put("max_tokens", 512)
                putJsonArray("messages") {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    add(buildJsonObject {
                        put("role", "user")
                        putJsonArray("content") {
                            add(buildJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") {
                                    put("url", "data:image/jpeg;base64,$base64Image")
                                }
                            })
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", "Analyze this product image for the Aura marketplace and respond in JSON.")
                            })
                        }
                    })
                }
            }

            val responseText = makeApiCall(requestBody.toString())
            val jsonElement = Json.parseToJsonElement(responseText)
            val content = jsonElement.jsonObject["choices"]
                ?.jsonArray?.get(0)
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content ?: return@withContext defaultRejection("Empty response from AI")

            // Strip any markdown code fences
            val cleanContent = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val parsed = Json.parseToJsonElement(cleanContent).jsonObject

            val isRelevant = parsed["isRelevant"]?.jsonPrimitive?.content?.toBoolean() ?: false
            val title = parsed["title"]?.jsonPrimitive?.content ?: "Unknown Item"
            val description = parsed["description"]?.jsonPrimitive?.content ?: ""
            val category = parsed["category"]?.jsonPrimitive?.content ?: "Other"
            val tags = parsed["tags"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val condition = parsed["condition"]?.jsonPrimitive?.content ?: "Good"
            val rejectionReason = parsed["rejectionReason"]?.jsonPrimitive?.content?.takeIf { it != "null" }

            ListingAnalysis(
                isRelevant = isRelevant,
                title = title,
                description = description,
                category = category,
                tags = tags,
                condition = condition,
                rejectionReason = rejectionReason
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyze product image", e)
            // On failure, allow the upload to proceed with blank data
            ListingAnalysis(
                isRelevant = true,
                title = "",
                description = "",
                category = "Other",
                tags = emptyList(),
                condition = "Good",
                rejectionReason = null
            )
        }
    }

    /**
     * Chat with the Directive AI assistant for personalized mission generation.
     *
     * @param conversationHistory List of previous messages in the conversation
     * @param userMessage The latest user message
     * @return The AI's response message
     */
    suspend fun chatWithDirectiveAI(
        conversationHistory: List<ChatMessage>,
        userMessage: String
    ): String = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = """You are Aura's personal wellness and productivity guide. 
Your role is to help users earn Aura points through real-world actions that benefit their mental and physical wellbeing.

When a user tells you how they are feeling or what they're up to today:
1. Acknowledge their situation with empathy (1-2 sentences)
2. Gently suggest 1-2 personalized missions they can do RIGHT NOW to improve their Aura score
3. Make missions specific, actionable, and location-based when possible

Mission examples (adapt to user's context):
- "Go for a 15-minute walk outside and take a photo of something beautiful you find"
- "Visit a nearby cafe or park and take a photo of your environment"
- "Clean up one area of your home and photograph it before and after"
- "Do 10 minutes of stretching and capture a moment of calm"

Always end by asking if they'd like to accept a mission. Keep responses short, warm, and encouraging.
Format missions as: 🎯 **Mission:** [description] (+[X] Aura points)"""

            val messages = buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                conversationHistory.forEach { msg ->
                    add(buildJsonObject {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userMessage)
                })
            }

            val requestBody = buildJsonObject {
                put("model", MODEL)
                put("temperature", 0.8)
                put("max_tokens", 400)
                put("messages", messages)
            }

            val responseText = makeApiCall(requestBody.toString())
            val jsonElement = Json.parseToJsonElement(responseText)
            jsonElement.jsonObject["choices"]
                ?.jsonArray?.get(0)
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content ?: "I'm here to help you build your Aura! How are you feeling today?"

        } catch (e: Exception) {
            Log.e(TAG, "Chat with directive AI failed", e)
            "I'm here to help you build your Aura! Tell me how you're feeling today and I'll suggest a mission for you. 🌟"
        }
    }

    /**
     * Verifies a mission completion photo using AI vision.
     * 
     * @param missionDescription What the mission required
     * @param imageBytes The proof photo bytes
     * @return A pair of (passed: Boolean, feedback: String)
     */
    suspend fun verifyMissionCompletion(
        missionDescription: String,
        imageBytes: ByteArray
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val requestBody = buildJsonObject {
                put("model", MODEL)
                put("temperature", 0.2)
                put("max_tokens", 200)
                putJsonArray("messages") {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", "You are verifying if a user completed an Aura mission. Be generous but honest. Respond ONLY in JSON: {\"passed\": true/false, \"feedback\": \"1-2 sentences\"}")
                    })
                    add(buildJsonObject {
                        put("role", "user")
                        putJsonArray("content") {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", "Mission: $missionDescription\n\nDoes this photo show the mission was completed?")
                            })
                            add(buildJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") {
                                    put("url", "data:image/jpeg;base64,$base64Image")
                                }
                            })
                        }
                    })
                }
            }

            val responseText = makeApiCall(requestBody.toString())
            val jsonElement = Json.parseToJsonElement(responseText)
            val content = jsonElement.jsonObject["choices"]
                ?.jsonArray?.get(0)
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content ?: return@withContext Pair(false, "Verification failed")

            val cleanContent = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val parsed = Json.parseToJsonElement(cleanContent).jsonObject
            val passed = parsed["passed"]?.jsonPrimitive?.content?.toBoolean() ?: false
            val feedback = parsed["feedback"]?.jsonPrimitive?.content ?: "Verification complete."
            Pair(passed, feedback)
        } catch (e: Exception) {
            Log.e(TAG, "Mission verification failed", e)
            Pair(false, "Could not verify at this time. Please try again.")
        }
    }

    private fun makeApiCall(requestBody: String): String {
        val url = URL(BASE_URL)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000

        connection.outputStream.use { it.write(requestBody.toByteArray()) }

        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val response = BufferedReader(InputStreamReader(stream)).use { it.readText() }

        if (responseCode !in 200..299) {
            Log.e(TAG, "Groq API error $responseCode: $response")
            throw Exception("Groq API error: $responseCode - $response")
        }
        return response
    }

    private fun defaultRejection(reason: String) = ListingAnalysis(
        isRelevant = false,
        title = "",
        description = "",
        category = "Other",
        tags = emptyList(),
        condition = "Good",
        rejectionReason = reason
    )
}
