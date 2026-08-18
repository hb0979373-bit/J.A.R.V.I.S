package com.example.ai

data class ChatMessage(
    val role: String, // "user" or "model"
    val text: String
)

data class AiConfiguration(
    val providerName: String = "Google Gemini",
    val modelName: String = "gemini-2.5-flash",
    val temperature: Float = 0.7f,
    val maxOutputTokens: Int = 1024
)

sealed class ConnectionTestResult {
    data object Testing : ConnectionTestResult()
    data class Success(val message: String = "Connected ✓") : ConnectionTestResult()
    data class Error(val reason: String) : ConnectionTestResult()
    data object NotConfigured : ConnectionTestResult()
}

interface AiProvider {
    val name: String
    suspend fun generateResponse(
        prompt: String,
        conversationHistory: List<ChatMessage> = emptyList(),
        memories: List<String> = emptyList(),
        systemInstruction: String = ""
    ): Result<String>

    suspend fun testConnection(): ConnectionTestResult
}
