package com.example.ai

import com.example.core.security.KeystoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class GeminiAiProvider(
    private val keystoreManager: KeystoreManager,
    private val configuration: AiConfiguration = AiConfiguration()
) : AiProvider {

    override val name: String = "Google Gemini"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun generateResponse(
        prompt: String,
        conversationHistory: List<ChatMessage>,
        memories: List<String>,
        systemInstruction: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = keystoreManager.getDecryptedApiKey()
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("API key is not configured. Please open Settings → AI Configuration."))
        }

        try {
            val rootJson = JSONObject()

            // System instructions
            val baseInstruction = buildSystemPrompt(systemInstruction, memories)
            val systemObj = JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", baseInstruction) })
                })
            }
            rootJson.put("systemInstruction", systemObj)

            // Contents array
            val contentsArray = JSONArray()

            // Add previous conversation turns
            for (msg in conversationHistory) {
                val turnObj = JSONObject().apply {
                    put("role", if (msg.role == "assistant" || msg.role == "model") "model" else "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", msg.text) })
                    })
                }
                contentsArray.put(turnObj)
            }

            // Add current prompt
            val currentTurn = JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", prompt) })
                })
            }
            contentsArray.put(currentTurn)
            rootJson.put("contents", contentsArray)

            // Generation config
            val genConfig = JSONObject().apply {
                put("temperature", configuration.temperature)
                put("maxOutputTokens", configuration.maxOutputTokens)
            }
            rootJson.put("generationConfig", genConfig)

            val requestBody = rootJson.toString().toRequestBody(jsonMediaType)
            val url = "https://generativelanguage.googleapis.com/v1beta/models/${configuration.modelName}:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseCode = response.code
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(responseCode, responseBody)
                return@withContext Result.failure(Exception(errorMsg))
            }

            val parsedText = parseCandidateText(responseBody)
            if (parsedText.isBlank()) {
                return@withContext Result.failure(Exception("J.A.R.V.I.S received an empty response from the AI core."))
            }

            Result.success(parsedText)
        } catch (e: UnknownHostException) {
            Result.failure(Exception("Network unavailable. Please check your internet connection."))
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("AI connection timed out. Please try again."))
        } catch (e: IOException) {
            Result.failure(Exception("Network error while contacting AI: ${e.localizedMessage}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun testConnection(): ConnectionTestResult = withContext(Dispatchers.IO) {
        val apiKey = keystoreManager.getDecryptedApiKey()
        if (apiKey.isNullOrBlank()) {
            return@withContext ConnectionTestResult.NotConfigured
        }

        try {
            val rootJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", "Ping. Respond with one word: 'Operational'.") })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("maxOutputTokens", 10)
                })
            }

            val requestBody = rootJson.toString().toRequestBody(jsonMediaType)
            val url = "https://generativelanguage.googleapis.com/v1beta/models/${configuration.modelName}:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseCode = response.code
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                ConnectionTestResult.Success("Connected ✓ (Latency normal)")
            } else {
                val reason = parseErrorMessage(responseCode, responseBody)
                ConnectionTestResult.Error(reason)
            }
        } catch (e: UnknownHostException) {
            ConnectionTestResult.Error("Network unavailable")
        } catch (e: SocketTimeoutException) {
            ConnectionTestResult.Error("Connection timed out")
        } catch (e: Exception) {
            ConnectionTestResult.Error(e.localizedMessage ?: "Unknown error")
        }
    }

    private fun buildSystemPrompt(customInstruction: String, memories: List<String>): String {
        val memorySection = if (memories.isNotEmpty()) {
            "\nApproved User Memories:\n" + memories.joinToString("\n") { "- $it" }
        } else ""

        return """
You are J.A.R.V.I.S, an advanced, highly intelligent personal voice-first AI assistant.
Your personality is calm, professional, articulate, polite, slightly British and quietly confident.

Core Guidelines:
1. Speech Optimization: Keep spoken responses concise, natural, and direct unless detailed analysis is requested. Avoid markdown styling like bold or code blocks in conversational speech unless necessary.
2. Objective Reasoning: Do NOT blindly agree with the user. Analyze the situation honestly: weigh pros, cons, potential risks, safer alternatives, and give clear justifications.
3. Safety: If a proposed action is harmful or unsafe, politely warn the user and suggest safer alternatives.
4. Problem Solving: When asked for advice, structure your thoughts logically and explain WHY you recommend a particular path.
5. Clarification: If user instructions are ambiguous or critical details are missing, ask a sharp, helpful clarifying question.
6. Contextual Awareness: Maintain conversation flow and refer naturally to prior context.
$memorySection
$customInstruction
""".trimIndent()
    }

    private fun parseCandidateText(jsonString: String): String {
        return try {
            val json = JSONObject(jsonString)
            val candidates = json.optJSONArray("candidates") ?: return ""
            if (candidates.length() == 0) return ""
            val first = candidates.getJSONObject(0)
            val content = first.optJSONObject("content") ?: return ""
            val parts = content.optJSONArray("parts") ?: return ""
            if (parts.length() == 0) return ""
            val textBuilder = StringBuilder()
            for (i in 0 until parts.length()) {
                textBuilder.append(parts.getJSONObject(i).optString("text", ""))
            }
            textBuilder.toString().trim()
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseErrorMessage(code: Int, body: String): String {
        return when (code) {
            400, 403 -> "Invalid API key or unauthorized request"
            404 -> "AI model endpoint not found"
            429 -> "Rate limited by AI provider. Please wait a moment."
            500, 503 -> "AI service temporarily unavailable (Server error)"
            else -> {
                try {
                    val json = JSONObject(body)
                    val errorObj = json.optJSONObject("error")
                    errorObj?.optString("message") ?: "Error HTTP $code"
                } catch (e: Exception) {
                    "Error HTTP $code"
                }
            }
        }
    }
}
