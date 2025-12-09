package com.test.chatbot.repository

import com.test.chatbot.api.RetrofitClient
import com.test.chatbot.models.*
import com.test.chatbot.utils.SystemPrompts
import com.test.chatbot.utils.ToolsUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatRepository {
    private val claudeApiService = RetrofitClient.claudeApiService
    private val yandexApiService = RetrofitClient.yandexGptApiService
    
    // Отправка сообщения в Claude
    suspend fun sendMessageToClaude(
        apiKey: String,
        conversationHistory: List<ClaudeMessage>,
        temperature: Double = 0.7
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = ClaudeRequest(
                system = SystemPrompts.UNIVERSAL_AGENT,
                messages = conversationHistory,
                temperature = temperature,
                tools = ToolsUtils.tools
            )
            
            val response = claudeApiService.sendMessage(apiKey, request = request)
            
            if (response.isSuccessful && response.body() != null) {
                val textResponse = response.body()!!.content
                    .filter { it.type == "text" }
                    .joinToString("") { it.text ?: "" }
                Result.success(textResponse)
            } else {
                Result.failure(Exception("Claude API error: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Отправка сообщения в YandexGPT
    suspend fun sendMessageToYandexGpt(
        apiKey: String,
        folderId: String,
        messages: List<YandexGptMessage>,
        temperature: Double = 0.7
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = YandexGptRequest(
                modelUri = "gpt://$folderId/yandexgpt-lite",
                completionOptions = CompletionOptions(
                    stream = false,
                    temperature = temperature,
                    maxTokens = "2000"
                ),
                messages = messages
            )
            
            val response = yandexApiService.sendMessage(
                authorization = "Api-Key $apiKey",
                request = request
            )
            
            if (response.isSuccessful && response.body() != null) {
                val textResponse = response.body()!!.result.alternatives
                    .firstOrNull()?.message?.text ?: ""
                Result.success(textResponse)
            } else {
                Result.failure(Exception("YandexGPT API error: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun executeToolCall(toolName: String, input: Map<String, Any>): String {
        return ToolsUtils.executeToolCall(toolName, input)
    }
}
