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
     * Anti-spoofing: strict validation that the image shows a 3D physical object in a real environment.
     * Rejects screenshots, monitor photos, downloaded stock images, or AI-generated content.
     * Output: binary Pass/Fail with reason.
     */
    data class AntiSpoofResult(
        val pass: Boolean,
        val reason: String
    )

    suspend fun runAntiSpoofing(imageBytes: ByteArray): AntiSpoofResult = withContext(Dispatchers.IO) {
        try {
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val systemPrompt = """You are a strict anti-spoofing validator for Aura marketplace.
Analyze this image and determine if it shows a REAL 3D physical object in a REAL physical environment.

PASS (true) ONLY if:
- The image shows a tangible physical item (product, gadget, clothing, etc.)
- The item appears to be photographed in a real-world setting (room, outdoors, hand-held)
- There is evidence of depth, shadows, or natural lighting that suggests a physical scene

FAIL (false) if:
- Screenshot of a screen, monitor, or display
- Photo of a photo or printed image
- Downloaded stock image or catalog photo
- AI-generated or synthetic image
- Flat 2D representation without physical presence
- Blurry or undiscernible content

Respond ONLY with valid JSON:
{"pass": true/false, "reason": "Brief 1-sentence explanation"}
"""

            val requestBody = buildJsonObject {
                put("model", BuildConfig.GROQ_MODEL)
                put("temperature", 0.1)
                put("max_tokens", 128)
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
                                put("text", "Is this a real physical object in a real environment? Respond in JSON.")
                            })
                        }
                    })
                }
            }

            val responseText = makeApiCall(requestBody.toString())
            val jsonElement = Json.parseToJsonElement(responseText)
            val content = jsonElement.jsonObject["choices"]
                ?.jsonArray?.getOrNull(0)
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content ?: return@withContext AntiSpoofResult(false, "Empty response from AI")

            val cleanContent = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val parsed = Json.parseToJsonElement(cleanContent).jsonObject
            val pass = parsed["pass"]?.jsonPrimitive?.content?.toBoolean() ?: false
            val reason = parsed["reason"]?.jsonPrimitive?.content ?: (if (pass) "Physical object verified." else "Could not verify physical presence.")
            AntiSpoofResult(pass, reason)
        } catch (e: Exception) {
            Log.e(TAG, "Anti-spoofing failed", e)
            AntiSpoofResult(false, "Verification failed. Please try again.")
        }
    }

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
                put("model", BuildConfig.GROQ_MODEL)
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
- A mission should be a real-world physical action they can do RIGHT NOW.

CRITICAL RULE: If the user states a condition, limitation, or specific request (e.g., "I'm tired", "I can't leave my room", "give me something easy"), you MUST tailor the mission EXACTLY to that condition. Do not suggest leaving the house if they say they are stuck inside. 

CRITICAL ANTI-CHEAT RULE: You MUST ONLY suggest instant, real-world physical photo missions (e.g., "Take a photo of a real object on your desk", "Take a photo of your coffee cup"). You MUST NOT under any circumstances suggest missions that can be completed with a screenshot, a digital image, or taking a photo of a computer/phone screen.

Good mission examples for different states:
- Tired/Indoor: "Find a cozy spot, sit for 5 minutes, and take a peaceful photo of your physical view."
- Active/Outdoor: "Head to the nearest park and take a photo of a real tree or flower."
- Busy: "Do a 2-minute clean-up of your immediate physical workspace and photograph the after."

When proposing a mission, ALWAYS:
1. Write 1-2 empathetic sentences acknowledging their specific mood/condition.
2. Then write: "Here's your mission for today:"
3. Write the mission description clearly (one paragraph, human and warm).
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
                put("model", BuildConfig.GROQ_MODEL)
                put("temperature", 0.85)
                put("max_tokens", 450)
                put("messages", messages)
            }

            var responseText = ""
            var success = false
            for (attempt in 1..3) {
                try {
                    responseText = makeApiCall(requestBody.toString())
                    success = true
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Groq API attempt $attempt failed", e)
                    if (attempt < 3) kotlinx.coroutines.delay(2000L)
                }
            }

            if (!success) {
                return@withContext "I'm having a little trouble connecting to the network right now. Give it a moment and try again!"
            }

            val jsonElement = Json.parseToJsonElement(responseText)
            jsonElement.jsonObject["choices"]
                ?.jsonArray?.get(0)
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content ?: "I'm here to help you build your Aura! How are you feeling today?"

        } catch (e: Exception) {
            Log.e(TAG, "Chat with directive AI failed", e)
            "I ran into an unexpected issue. Mind trying that again?"
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
    "Step 1: Go to [location]",
    "Step 2: [Do the action]",
    "Step 3: Take a photo as proof",
    "Step 4: Submit your photo for Aura verification"
  ],
  "locationHint": "nearby park / your desk / etc.",
  "auraReward": 25,
  "emoji": "🌿"
}
CRITICAL: Make steps highly specific to what the AI proposed and account for any physical constraints or locations the user mentioned in the conversation history."""

            val requestBody = buildJsonObject {
                put("model", BuildConfig.GROQ_MODEL)
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
                put("model", BuildConfig.GROQ_MODEL)
                put("temperature", 0.2)
                put("max_tokens", 300)
                putJsonArray("messages") {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", """You are an Aura score judge verifying mission completion photos.
Be honest but encouraging. A passed photo must clearly relate to the mission.
Respond ONLY in JSON:
{"passed": true/false, "feedback": "1-2 warm encouraging sentences", "score": 0-100}

CRITICAL ANTI-CHEAT RULE: You must strictly reject screenshots, photos of computer/phone screens, AI-generated images, and downloaded images. If the image is a screenshot or a photo of a screen, you MUST set passed to false and score to 0, and tell them in the feedback that digital images/screenshots are not allowed!

Scoring rubric:
80-100: Real physical photo that clearly and creatively shows mission completion with good lighting
60-79: Real physical photo that shows mission completion but could be clearer
40-59: Partially relevant, edge case
0: Image is a screenshot, a photo of a screen, digital, or completely unrelated (set passed=false)""")
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

    /**
     * Compare buyer's photo to listing reference image. Uses Groq vision directly — no Supabase.
     * @param userPhotoBytes Buyer's captured photo
     * @param listingImageUrl URL of the listing's reference image (from listing.images.first())
     * @return pass, feedback, score (0-100)
     */
    suspend fun compareItemToListing(
        userPhotoBytes: ByteArray,
        listingImageUrl: String,
    ): Triple<Boolean, String, Int> = withContext(Dispatchers.IO) {
        try {
            val userBase64 = Base64.encodeToString(userPhotoBytes, Base64.NO_WRAP)
            val listingBase64 = runCatching {
                val conn = URL(listingImageUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.inputStream.use { Base64.encodeToString(it.readBytes(), Base64.NO_WRAP) }
            }.getOrNull()
            if (listingBase64 == null) {
                return@withContext Triple(false, "Could not load listing image.", 0)
            }
            val systemPrompt = """You are a lenient item-matching inspector.
Image 1: The buyer's photo of the physical item.
Image 2: The listing's reference photo.

Does Image 1 show the SAME physical item as Image 2? Be lenient — pass if it is reasonably likely the same product.
DO NOT reject for: different lighting, angle, packaging, shadows, partial view, slight blur. When in doubt, pass.
Respond ONLY with valid JSON: {"pass": true/false, "feedback": "1-2 sentences", "rating": 0-100}"""
            val requestBody = buildJsonObject {
                put("model", BuildConfig.GROQ_MODEL)
                put("temperature", 0.1)
                put("max_tokens", 256)
                put("response_format", buildJsonObject { put("type", "json_object") })
                putJsonArray("messages") {
                    add(buildJsonObject {
                        put("role", "user")
                        putJsonArray("content") {
                            add(buildJsonObject { put("type", "text"); put("text", systemPrompt) })
                            add(buildJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") { put("url", "data:image/jpeg;base64,$userBase64") }
                            })
                            add(buildJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") { put("url", "data:image/jpeg;base64,$listingBase64") }
                            })
                        }
                    })
                }
            }
            val responseText = makeApiCall(requestBody.toString())
            val content = Json.parseToJsonElement(responseText).jsonObject["choices"]
                ?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
                ?: return@withContext Triple(false, "No response from AI.", 0)
            val parsed = Json.parseToJsonElement(content).jsonObject
            val pass = parsed["pass"]?.jsonPrimitive?.content?.toBoolean() ?: false
            val feedback = parsed["feedback"]?.jsonPrimitive?.content ?: "Could not verify."
            val rating = parsed["rating"]?.jsonPrimitive?.content?.toIntOrNull() ?: (if (pass) 70 else 30)
            Triple(pass || rating >= 55, feedback, rating.coerceIn(0, 100))
        } catch (e: Exception) {
            Log.e(TAG, "compareItemToListing failed", e)
            Triple(false, e.message ?: "Verification failed. Please try again.", 0)
        }
    }

    private fun makeApiCall(requestBody: String): String {
        val url = URL(BASE_URL)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 60_000
        connection.readTimeout = 120_000

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
