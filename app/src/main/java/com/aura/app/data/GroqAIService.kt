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

    data class AIMission(
        val title: String,
        val description: String,
        val steps: List<String>,
        val locationHint: String,
        val auraReward: Int,
        val emoji: String
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
     * Chat with the Directive AI assistant. After 1-2 exchanges, generates a personalized mission.
     * When a mission is ready to be proposed, the response MUST end with the marker: [MISSION_READY]
     */
    suspend fun chatWithDirectiveAI(
        conversationHistory: List<ChatMessage>,
        userMessage: String
    ): String = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = """You are Aura's personal wellness guide — warm, encouraging, and human.
Your goal: understand how the user is feeling, then suggest a real-world mission they can do TODAY.

Conversation flow:
- First message: greet them warmly and ask how they're doing
- If they share their mood/situation: empathize briefly (1 sentence), then propose ONE specific mission
- A mission should be a real-world physical action they can do nearby RIGHT NOW

Good mission examples:
- "Head to the nearest park and take a photo of something that made you smile"
- "Step outside for a 10-minute walk and capture your surroundings"
- "Find a quiet spot, sit for 5 minutes, and take a peaceful photo"
- "Visit the nearest cafe and capture your drink or the vibe"
- "Do a clean-up of one small area and photo the before or after"

When proposing a mission, ALWAYS:
1. Write 1-2 empathetic sentences
2. Then write: "Here's your mission for today:"
3. Write the mission description clearly (one paragraph, human and warm)
4. End your message with literally: [MISSION_READY]

Keep responses SHORT and warm. Never be clinical or robotic."""

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
                put("temperature", 0.85)
                put("max_tokens", 450)
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
            "Error: ${e.message ?: "Unknown API Error"} — Please try again."
        }
    }

    /**
     * Generates a structured mission from conversation context.
     * Called when the AI response contains [MISSION_READY] to create the Accept card.
     */
    suspend fun generateMission(
        conversationHistory: List<ChatMessage>
    ): AIMission = withContext(Dispatchers.IO) {
        try {
            val lastAiMessage = conversationHistory.lastOrNull { it.role == "assistant" }?.content ?: ""
            val systemPrompt = """Based on this conversation and the mission the AI just proposed, generate a structured JSON mission.
Respond ONLY in this exact JSON format:
{
  "title": "Short mission title (3-5 words)",
  "description": "Full mission description, 1-2 sentences, warm and specific",
  "steps": [
    "Step 1: Go to [a nearby location]",
    "Step 2: [Do the action]",
    "Step 3: Take a photo as proof",
    "Step 4: Submit your photo for Aura verification"
  ],
  "locationHint": "nearby park / local cafe / your street / etc.",
  "auraReward": 25,
  "emoji": "🌿"
}
Make steps specific to what the AI proposed. The location should be realistic and walkable."""

            val requestBody = buildJsonObject {
                put("model", MODEL)
                put("temperature", 0.4)
                put("max_tokens", 400)
                putJsonArray("messages") {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", "The AI just proposed this mission:\n\n$lastAiMessage\n\nGenerate the structured JSON for it.")
                    })
                }
            }

            val responseText = makeApiCall(requestBody.toString())
            val jsonElement = Json.parseToJsonElement(responseText)
            val content = jsonElement.jsonObject["choices"]
                ?.jsonArray?.get(0)
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content ?: return@withContext defaultMission()

            val clean = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val parsed = Json.parseToJsonElement(clean).jsonObject

            AIMission(
                title = parsed["title"]?.jsonPrimitive?.content ?: "Explore Nearby",
                description = parsed["description"]?.jsonPrimitive?.content ?: "Go outside and take in the world.",
                steps = parsed["steps"]?.jsonArray?.map { it.jsonPrimitive.content } ?: listOf("Go outside", "Take a photo", "Submit"),
                locationHint = parsed["locationHint"]?.jsonPrimitive?.content ?: "nearby",
                auraReward = parsed["auraReward"]?.jsonPrimitive?.content?.toIntOrNull() ?: 25,
                emoji = parsed["emoji"]?.jsonPrimitive?.content ?: "🌿"
            )
        } catch (e: Exception) {
            Log.e(TAG, "generateMission failed", e)
            defaultMission()
        }
    }

    private fun defaultMission() = AIMission(
        title = "Mindful Walk Outside",
        description = "Step outside and take a short walk. Find something beautiful and capture it.",
        steps = listOf(
            "Step 1: Head to a nearby park or your street",
            "Step 2: Walk for at least 5-10 minutes",
            "Step 3: Find something that catches your eye",
            "Step 4: Take a photo as your proof — then submit!"
        ),
        locationHint = "nearby park or street",
        auraReward = 20,
        emoji = "🚶"
    )


    /**
     * Verifies a mission completion photo using AI vision.
     *
     * @param missionDescription What the mission required
     * @param imageBytes The proof photo bytes
     * @return Triple of (passed: Boolean, feedback: String, score: Int 0-100)
     */
    suspend fun verifyMissionCompletion(
        missionDescription: String,
        imageBytes: ByteArray
    ): Triple<Boolean, String, Int> = withContext(Dispatchers.IO) {
        try {
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val requestBody = buildJsonObject {
                put("model", MODEL)
                put("temperature", 0.2)
                put("max_tokens", 300)
                putJsonArray("messages") {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", """You are an Aura score judge verifying mission completion photos.
Be honest but encouraging. A passed photo must clearly relate to the mission.
Respond ONLY in JSON:
{"passed": true/false, "feedback": "1-2 warm encouraging sentences", "score": 0-100}

Scoring rubric:
80-100: Photo clearly and creatively shows mission completion
60-79: Photo shows mission completion but could be clearer
40-59: Partially relevant, edge case
0-39: Does not show mission completion (set passed=false)""")
                    })
                    add(buildJsonObject {
                        put("role", "user")
                        putJsonArray("content") {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", "Mission: $missionDescription\n\nDoes this photo prove the mission was completed? Give an Aura score.")
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
                ?.jsonPrimitive?.content ?: return@withContext Triple(false, "Verification failed", 0)

            val cleanContent = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val parsed = Json.parseToJsonElement(cleanContent).jsonObject
            val passed   = parsed["passed"]?.jsonPrimitive?.content?.toBoolean() ?: false
            val feedback = parsed["feedback"]?.jsonPrimitive?.content ?: "Verification complete."
            val score    = parsed["score"]?.jsonPrimitive?.content?.toIntOrNull() ?: if (passed) 70 else 20
            Triple(passed, feedback, score)
        } catch (e: Exception) {
            Log.e(TAG, "Mission verification failed", e)
            Triple(false, "Could not verify at this time. Please try again.", 0)
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
