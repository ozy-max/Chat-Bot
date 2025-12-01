package com.test.chatbot.repository

import com.test.chatbot.api.RetrofitClient
import com.test.chatbot.models.*
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
                messages = conversationHistory,
                tools = null
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
}
