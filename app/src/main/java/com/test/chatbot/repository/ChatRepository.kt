package com.test.chatbot.repository

import com.test.chatbot.api.RetrofitClient
import com.test.chatbot.models.ClaudeMessage
import com.test.chatbot.models.ClaudeRequest
import com.test.chatbot.models.ClaudeResponse
import com.test.chatbot.utils.SystemPrompts
import com.test.chatbot.utils.ToolsUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatRepository {
    private val apiService = RetrofitClient.claudeApiService
    
    suspend fun sendMessage(
        apiKey: String,
        conversationHistory: List<ClaudeMessage>
    ): Result<ClaudeResponse> = withContext(Dispatchers.IO) {
        try {
            val request = ClaudeRequest(
                system = SystemPrompts.UNIVERSAL_AGENT,
                messages = conversationHistory,
                tools = ToolsUtils.tools
            )
            
            val response = apiService.sendMessage(apiKey, request = request)
            
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("API error: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun executeToolCall(toolName: String, input: Map<String, Any>): String {
        return ToolsUtils.executeToolCall(toolName, input)
    }
}
