package com.test.chatbot.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Клиент для работы с Ollama Chat API Server
 * 
 * Использует удаленный API сервер вместо прямого подключения к Ollama.
 * Это позволяет:
 * - Развернуть API на удаленной машине
 * - Централизованно управлять моделями
 * - Кэшировать результаты
 * - Добавить аутентификацию
 * 
 * @param baseUrl URL API сервера (по умолчанию localhost через ADB reverse)
 */
class OllamaApiClient(
    private var baseUrl: String = "http://localhost:8080"
) {
    
    companion object {
        private const val TAG = "OllamaApiClient"
        private const val TIMEOUT_SECONDS = 120L // 2 минуты
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    /**
     * Проверка доступности API сервера
     */
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext false
                val healthResponse = gson.fromJson(body, HealthResponse::class.java)
                
                Log.i(TAG, "✅ API доступен: ${healthResponse.status}")
                return@withContext healthResponse.status == "healthy"
            }
            
            Log.w(TAG, "⚠️ API недоступен: ${response.code}")
            return@withContext false
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка проверки доступности: ${e.message}")
            return@withContext false
        }
    }
    
    /**
     * Получить список доступных моделей
     */
    suspend fun getModels(): Result<List<ModelInfo>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/models")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("API вернул код ${response.code}")
                )
            }
            
            val body = response.body?.string() ?: return@withContext Result.failure(
                Exception("Пустой ответ")
            )
            
            val modelsResponse = gson.fromJson(body, ModelsResponse::class.java)
            
            Log.i(TAG, "📦 Получено моделей: ${modelsResponse.models.size}")
            
            return@withContext Result.success(modelsResponse.models)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка получения моделей: ${e.message}")
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * Отправить сообщение и получить ответ
     * 
     * @param message Текст сообщения
     * @param model Название модели (опционально)
     * @param temperature Температура генерации (0.0-1.0)
     * @param maxTokens Максимум токенов в ответе
     * @param contextWindow Размер контекстного окна (опционально)
     * @param systemPrompt Системный промпт (опционально)
     * @param history История диалога (опционально)
     */
    suspend fun chat(
        message: String,
        model: String = "llama3",
        temperature: Double = 0.7,
        maxTokens: Int = 2048,
        contextWindow: Int = 4096,
        systemPrompt: String? = null,
        history: List<ChatMessage> = emptyList()
    ): Result<ChatResponse> = withContext(Dispatchers.IO) {
        try {
            val chatRequest = ChatRequest(
                message = message,
                model = model,
                temperature = temperature,
                maxTokens = maxTokens,
                contextWindow = contextWindow,
                systemPrompt = systemPrompt,
                history = history
            )
            
            val requestBody = gson.toJson(chatRequest)
                .toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url("$baseUrl/chat")
                .post(requestBody)
                .build()
            
            Log.i(TAG, "💬 Отправка запроса: model=$model, message_length=${message.length}")
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "❌ API ошибка ${response.code}: $errorBody")
                return@withContext Result.failure(
                    Exception("API ошибка ${response.code}: $errorBody")
                )
            }
            
            val body = response.body?.string() ?: return@withContext Result.failure(
                Exception("Пустой ответ")
            )
            
            val chatResponse = gson.fromJson(body, ChatResponse::class.java)
            
            Log.i(TAG, "✅ Ответ получен: " +
                    "tokens=${chatResponse.totalTokens}, " +
                    "time=${chatResponse.generationTime}s")
            
            return@withContext Result.success(chatResponse)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка chat: ${e.message}", e)
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * Обновить базовый URL API сервера
     */
    fun updateBaseUrl(newBaseUrl: String) {
        baseUrl = newBaseUrl
        Log.i(TAG, "🔄 Обновлен базовый URL: $baseUrl")
    }
    
    // ===== Data Classes =====
    
    data class ChatRequest(
        @SerializedName("message") val message: String,
        @SerializedName("model") val model: String = "llama3",
        @SerializedName("temperature") val temperature: Double = 0.7,
        @SerializedName("max_tokens") val maxTokens: Int = 2048,
        @SerializedName("context_window") val contextWindow: Int = 4096,
        @SerializedName("system_prompt") val systemPrompt: String? = null,
        @SerializedName("history") val history: List<ChatMessage> = emptyList()
    )
    
    data class ChatMessage(
        @SerializedName("role") val role: String,  // "user" или "assistant"
        @SerializedName("content") val content: String
    )
    
    data class ChatResponse(
        @SerializedName("message") val message: String,
        @SerializedName("model") val model: String,
        @SerializedName("input_tokens") val inputTokens: Int,
        @SerializedName("output_tokens") val outputTokens: Int,
        @SerializedName("total_tokens") val totalTokens: Int,
        @SerializedName("generation_time") val generationTime: Double,
        @SerializedName("timestamp") val timestamp: String
    )
    
    data class HealthResponse(
        @SerializedName("status") val status: String,
        @SerializedName("ollama_status") val ollamaStatus: String,
        @SerializedName("ollama_host") val ollamaHost: String? = null,
        @SerializedName("available_models") val availableModels: Int = 0,
        @SerializedName("timestamp") val timestamp: String
    )
    
    data class ModelsResponse(
        @SerializedName("models") val models: List<ModelInfo>,
        @SerializedName("default_model") val defaultModel: String,
        @SerializedName("timestamp") val timestamp: String
    )
    
    data class ModelInfo(
        @SerializedName("name") val name: String,
        @SerializedName("size") val size: Long,
        @SerializedName("modified") val modified: String
    )
}
