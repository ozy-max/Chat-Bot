package com.test.chatbot.rag

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Клиент для работы с Ollama API
 * Документация: https://github.com/ollama/ollama/blob/main/docs/api.md
 */
class OllamaClient(
    private var baseUrl: String = "http://192.168.1.100:11434"
) {
    
    companion object {
        private const val TAG = "OllamaClient"
        private const val DEFAULT_EMBEDDING_MODEL = "nomic-embed-text"
        private const val DEFAULT_CHAT_MODEL = "llama3"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    /**
     * Обновить URL Ollama сервера
     */
    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
        Log.i(TAG, "Ollama URL обновлён: $baseUrl")
    }
    
    /**
     * Проверить доступность Ollama сервера
     */
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/tags")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            val available = response.isSuccessful
            
            Log.i(TAG, if (available) "✅ Ollama доступна" else "❌ Ollama недоступна")
            available
        } catch (e: Exception) {
            Log.w(TAG, "Ollama недоступна: ${e.message}")
            false
        }
    }
    
    /**
     * Получить список доступных моделей
     */
    suspend fun listModels(): Result<List<OllamaModel>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/tags")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            
            val json = response.body?.string() ?: ""
            val modelsResponse = gson.fromJson(json, ModelsResponse::class.java)
            
            Log.i(TAG, "✅ Получено ${modelsResponse.models.size} моделей")
            Result.success(modelsResponse.models)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка получения моделей: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Генерация эмбеддинга через Ollama
     */
    suspend fun generateEmbedding(
        text: String,
        model: String = DEFAULT_EMBEDDING_MODEL
    ): Result<FloatArray> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Генерация эмбеддинга через Ollama (модель: $model)")
            
            val requestBody = EmbeddingRequest(
                model = model,
                prompt = text
            )
            
            val json = gson.toJson(requestBody)
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val body = json.toRequestBody(mediaType)
            
            val request = Request.Builder()
                .url("$baseUrl/api/embeddings")
                .post(body)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("HTTP ${response.code}: ${response.message}")
                )
            }
            
            val responseJson = response.body?.string() ?: ""
            val embeddingResponse = gson.fromJson(responseJson, EmbeddingResponse::class.java)
            
            val embedding = embeddingResponse.embedding.map { it.toFloat() }.toFloatArray()
            
            Log.i(TAG, "✅ Эмбеддинг создан: ${embedding.size} измерений")
            Result.success(embedding)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка генерации эмбеддинга: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Генерация текста через Ollama (для RAG)
     */
    suspend fun generateText(
        prompt: String,
        model: String = DEFAULT_CHAT_MODEL,
        context: String? = null,
        temperature: Double = 0.7,
        maxTokens: Int = 2048
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Генерация текста через Ollama (модель: $model)")
            
            val fullPrompt = if (context != null) {
                """
                Контекст:
                $context
                
                Вопрос: $prompt
                
                Ответь на вопрос, используя информацию из контекста.
                """.trimIndent()
            } else {
                prompt
            }
            
            val requestBody = GenerateRequest(
                model = model,
                prompt = fullPrompt,
                stream = false,
                options = GenerateOptions(
                    temperature = temperature,
                    num_predict = maxTokens
                )
            )
            
            val json = gson.toJson(requestBody)
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val body = json.toRequestBody(mediaType)
            
            val request = Request.Builder()
                .url("$baseUrl/api/generate")
                .post(body)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("HTTP ${response.code}: ${response.message}")
                )
            }
            
            val responseJson = response.body?.string() ?: ""
            val generateResponse = gson.fromJson(responseJson, GenerateResponse::class.java)
            
            Log.i(TAG, "✅ Текст сгенерирован: ${generateResponse.response.length} символов")
            Result.success(generateResponse.response)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка генерации текста: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Скачать модель
     */
    suspend fun pullModel(modelName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Скачивание модели: $modelName")
            
            val requestBody = PullRequest(name = modelName)
            val json = gson.toJson(requestBody)
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val body = json.toRequestBody(mediaType)
            
            val request = Request.Builder()
                .url("$baseUrl/api/pull")
                .post(body)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("HTTP ${response.code}: ${response.message}")
                )
            }
            
            Log.i(TAG, "✅ Модель скачивается")
            Result.success("Модель $modelName скачивается")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка скачивания модели: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Получить информацию о модели
     */
    suspend fun getModelInfo(modelName: String): Result<ModelInfo> = withContext(Dispatchers.IO) {
        try {
            val requestBody = ShowRequest(name = modelName)
            val json = gson.toJson(requestBody)
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val body = json.toRequestBody(mediaType)
            
            val request = Request.Builder()
                .url("$baseUrl/api/show")
                .post(body)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("HTTP ${response.code}: ${response.message}")
                )
            }
            
            val responseJson = response.body?.string() ?: ""
            val modelInfo = gson.fromJson(responseJson, ModelInfo::class.java)
            
            Result.success(modelInfo)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка получения информации о модели: ${e.message}", e)
            Result.failure(e)
        }
    }
}

// ==================== Data Classes ====================

data class EmbeddingRequest(
    @SerializedName("model")
    val model: String,
    @SerializedName("prompt")
    val prompt: String
)

data class EmbeddingResponse(
    @SerializedName("embedding")
    val embedding: List<Double>
)

data class GenerateRequest(
    @SerializedName("model")
    val model: String,
    @SerializedName("prompt")
    val prompt: String,
    @SerializedName("stream")
    val stream: Boolean = false,
    @SerializedName("options")
    val options: GenerateOptions? = null
)

data class GenerateOptions(
    @SerializedName("temperature")
    val temperature: Double = 0.7,
    @SerializedName("num_predict")
    val num_predict: Int = 2048
)

data class GenerateResponse(
    @SerializedName("model")
    val model: String,
    @SerializedName("response")
    val response: String,
    @SerializedName("done")
    val done: Boolean
)

data class ModelsResponse(
    @SerializedName("models")
    val models: List<OllamaModel>
)

data class OllamaModel(
    @SerializedName("name")
    val name: String,
    @SerializedName("size")
    val size: Long,
    @SerializedName("modified_at")
    val modifiedAt: String
)

data class PullRequest(
    @SerializedName("name")
    val name: String
)

data class ShowRequest(
    @SerializedName("name")
    val name: String
)

data class ModelInfo(
    @SerializedName("modelfile")
    val modelfile: String? = null,
    @SerializedName("parameters")
    val parameters: String? = null,
    @SerializedName("template")
    val template: String? = null
)

