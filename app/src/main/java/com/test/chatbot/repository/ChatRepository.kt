package com.test.chatbot.repository

import com.test.chatbot.api.RetrofitClient
import com.test.chatbot.models.*
import com.test.chatbot.utils.SystemPrompts
import com.test.chatbot.utils.ToolsUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    
    /**
     * Сравнение моделей: отправляет один запрос на обе модели параллельно
     * и возвращает результаты с метриками
     */
    suspend fun compareModels(
        query: String,
        claudeApiKey: String,
        yandexApiKey: String,
        yandexFolderId: String,
        temperature: Double = 0.7
    ): ModelComparisonResult = coroutineScope {
        
        // Запускаем оба запроса параллельно
        val claudeDeferred = async {
            sendMessageToClaudeWithMetrics(claudeApiKey, query, temperature)
        }
        
        val yandexDeferred = async {
            sendMessageToYandexWithMetrics(yandexApiKey, yandexFolderId, query, temperature)
        }
        
        val claudeResult = claudeDeferred.await()
        val yandexResult = yandexDeferred.await()
        
        ModelComparisonResult(
            query = query,
            claudeResult = claudeResult,
            yandexResult = yandexResult
        )
    }
    
    /**
     * Отправка в Claude с измерением метрик
     */
    private suspend fun sendMessageToClaudeWithMetrics(
        apiKey: String,
        query: String,
        temperature: Double
    ): ModelResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            val request = ClaudeRequest(
                system = SystemPrompts.UNIVERSAL_AGENT,
                messages = listOf(ClaudeMessage(role = "user", content = query)),
                temperature = temperature,
                tools = null // Без tools для чистого сравнения
            )
            
            val response = claudeApiService.sendMessage(apiKey, request = request)
            val responseTime = System.currentTimeMillis() - startTime
            
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val textResponse = body.content
                    .filter { it.type == "text" }
                    .joinToString("") { it.text ?: "" }
                
                val inputTokens = body.usage?.inputTokens ?: estimateTokens(query)
                val outputTokens = body.usage?.outputTokens ?: estimateTokens(textResponse)
                
                ModelResponse(
                    modelName = "Claude Sonnet 4",
                    responseText = textResponse,
                    responseTimeMs = responseTime,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    totalTokens = inputTokens + outputTokens,
                    estimatedCostUsd = ModelPricing.calculateClaudeCost(inputTokens, outputTokens)
                )
            } else {
                ModelResponse(
                    modelName = "Claude Sonnet 4",
                    responseText = "",
                    responseTimeMs = responseTime,
                    inputTokens = 0,
                    outputTokens = 0,
                    totalTokens = 0,
                    estimatedCostUsd = 0.0,
                    error = "Ошибка: ${response.code()} - ${response.errorBody()?.string()}"
                )
            }
        } catch (e: Exception) {
            ModelResponse(
                modelName = "Claude Sonnet 4",
                responseText = "",
                responseTimeMs = System.currentTimeMillis() - startTime,
                inputTokens = 0,
                outputTokens = 0,
                totalTokens = 0,
                estimatedCostUsd = 0.0,
                error = "Ошибка: ${e.message}"
            )
        }
    }
    
    /**
     * Отправка в YandexGPT с измерением метрик
     */
    private suspend fun sendMessageToYandexWithMetrics(
        apiKey: String,
        folderId: String,
        query: String,
        temperature: Double
    ): ModelResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            val request = YandexGptRequest(
                modelUri = "gpt://$folderId/yandexgpt-lite",
                completionOptions = CompletionOptions(
                    stream = false,
                    temperature = temperature,
                    maxTokens = "2000"
                ),
                messages = listOf(
                    YandexGptMessage(role = "system", text = SystemPrompts.UNIVERSAL_AGENT),
                    YandexGptMessage(role = "user", text = query)
                )
            )
            
            val response = yandexApiService.sendMessage(
                authorization = "Api-Key $apiKey",
                request = request
            )
            val responseTime = System.currentTimeMillis() - startTime
            
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val textResponse = body.result.alternatives
                    .firstOrNull()?.message?.text ?: ""
                
                val inputTokens = body.result.usage?.inputTextTokens?.toIntOrNull() ?: estimateTokens(query)
                val outputTokens = body.result.usage?.completionTokens?.toIntOrNull() ?: estimateTokens(textResponse)
                
                ModelResponse(
                    modelName = "YandexGPT Lite",
                    responseText = textResponse,
                    responseTimeMs = responseTime,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    totalTokens = inputTokens + outputTokens,
                    estimatedCostUsd = ModelPricing.calculateYandexCost(inputTokens, outputTokens)
                )
            } else {
                ModelResponse(
                    modelName = "YandexGPT Lite",
                    responseText = "",
                    responseTimeMs = responseTime,
                    inputTokens = 0,
                    outputTokens = 0,
                    totalTokens = 0,
                    estimatedCostUsd = 0.0,
                    error = "Ошибка: ${response.code()} - ${response.errorBody()?.string()}"
                )
            }
        } catch (e: Exception) {
            ModelResponse(
                modelName = "YandexGPT Lite",
                responseText = "",
                responseTimeMs = System.currentTimeMillis() - startTime,
                inputTokens = 0,
                outputTokens = 0,
                totalTokens = 0,
                estimatedCostUsd = 0.0,
                error = "Ошибка: ${e.message}"
            )
        }
    }
    
    /**
     * Примерная оценка токенов (1 токен ≈ 4 символа для английского, 2-3 для русского)
     */
    private fun estimateTokens(text: String): Int {
        return (text.length / 3.0).toInt().coerceAtLeast(1)
    }
    
    fun executeToolCall(toolName: String, input: Map<String, Any>): String {
        return ToolsUtils.executeToolCall(toolName, input)
    }
}
