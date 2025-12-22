package com.test.chatbot.rag

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/**
 * Сервис для генерации эмбеддингов
 * Поддерживает Ollama и локальный алгоритм (TF-IDF + хеширование)
 */
class EmbeddingService(
    private val ollamaClient: OllamaClient? = null
) {
    
    companion object {
        private const val TAG = "EmbeddingService"
        private const val LOCAL_EMBEDDING_DIMENSION = 384 // Размерность локальных векторов
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private var useOllama = true // Пытаться использовать Ollama по умолчанию
    private var ollamaModel = "nomic-embed-text" // Модель по умолчанию
    
    /**
     * Генерация эмбеддинга для текста
     * Пытается использовать Ollama, fallback на локальный алгоритм
     */
    suspend fun generateEmbedding(text: String): Result<FloatArray> = withContext(Dispatchers.IO) {
        try {
            if (text.isBlank()) {
                return@withContext Result.failure(Exception("Текст пустой"))
            }
            
            Log.i(TAG, "Генерация эмбеддинга для текста длиной ${text.length}")
            
            // Пытаемся использовать Ollama если доступна
            if (useOllama && ollamaClient != null) {
                val ollamaResult = tryGenerateOllamaEmbedding(text)
                if (ollamaResult.isSuccess) {
                    return@withContext ollamaResult
                } else {
                    Log.w(TAG, "Ollama недоступна, используем локальный алгоритм")
                }
            }
            
            // Fallback на локальный алгоритм
            val embedding = createLocalEmbedding(text)
            
            Log.i(TAG, "✅ Локальный эмбеддинг создан: ${embedding.size} измерений")
            Result.success(embedding)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка генерации эмбеддинга: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Попытка создать эмбеддинг через Ollama
     */
    private suspend fun tryGenerateOllamaEmbedding(text: String): Result<FloatArray> {
        return try {
            if (ollamaClient == null) {
                return Result.failure(Exception("Ollama клиент не инициализирован"))
            }
            
            Log.i(TAG, "Попытка использовать Ollama (модель: $ollamaModel)")
            
            val result = ollamaClient.generateEmbedding(text, ollamaModel)
            
            if (result.isSuccess) {
                val embedding = result.getOrNull()!!
                Log.i(TAG, "✅ Ollama эмбеддинг создан: ${embedding.size} измерений")
            }
            
            result
        } catch (e: Exception) {
            Log.w(TAG, "Ollama ошибка: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Включить/выключить использование Ollama
     */
    fun setUseOllama(use: Boolean) {
        useOllama = use
        Log.i(TAG, "Ollama ${if (use) "включена" else "выключена"}")
    }
    
    /**
     * Установить модель Ollama для эмбеддингов
     */
    fun setOllamaModel(model: String) {
        ollamaModel = model
        Log.i(TAG, "Модель Ollama установлена: $model")
    }
    
    /**
     * Проверить доступность Ollama
     */
    suspend fun isOllamaAvailable(): Boolean {
        return ollamaClient?.isAvailable() ?: false
    }
    
    /**
     * Генерация эмбеддингов для нескольких текстов
     */
    suspend fun generateEmbeddings(texts: List<String>): Result<List<FloatArray>> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Генерация эмбеддингов для ${texts.size} текстов")
            
            val embeddings = texts.map { text ->
                createLocalEmbedding(text)
            }
            
            Log.i(TAG, "✅ Создано ${embeddings.size} эмбеддингов")
            Result.success(embeddings)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка генерации эмбеддингов: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Локальный алгоритм создания эмбеддинга
     * Основан на TF-IDF + хешировании слов
     */
    private fun createLocalEmbedding(text: String): FloatArray {
        val embedding = FloatArray(LOCAL_EMBEDDING_DIMENSION)
        
        // Токенизация (разбиение на слова)
        val words = tokenize(text)
        
        // TF (Term Frequency) - частота слов
        val wordFrequency = mutableMapOf<String, Int>()
        words.forEach { word ->
            wordFrequency[word] = wordFrequency.getOrDefault(word, 0) + 1
        }
        
        // Заполнение эмбеддинга на основе хешей слов и их частот
        wordFrequency.forEach { (word, freq) ->
            // Хешируем слово в несколько позиций вектора
            val hash1 = word.hashCode()
            val hash2 = word.reversed().hashCode()
            val hash3 = word.lowercase().hashCode()
            
            // Распределяем значения по вектору
            val index1 = Math.abs(hash1) % LOCAL_EMBEDDING_DIMENSION
            val index2 = Math.abs(hash2) % LOCAL_EMBEDDING_DIMENSION
            val index3 = Math.abs(hash3) % LOCAL_EMBEDDING_DIMENSION
            
            // Добавляем вес с учётом частоты
            val weight = freq.toFloat() / words.size
            embedding[index1] += weight
            embedding[index2] += weight * 0.5f
            embedding[index3] += weight * 0.3f
        }
        
        // Нормализация вектора (L2 norm)
        normalizeVector(embedding)
        
        return embedding
    }
    
    /**
     * Токенизация текста
     */
    private fun tokenize(text: String): List<String> {
        return text
            .lowercase()
            .replace(Regex("[^a-zа-яё0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 } // Фильтруем короткие слова
    }
    
    /**
     * Нормализация вектора (L2 norm)
     */
    private fun normalizeVector(vector: FloatArray) {
        val norm = sqrt(vector.map { it * it }.sum())
        if (norm > 0) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }
    }
    
    /**
     * Вычисление косинусного сходства между двумя векторами
     */
    fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        if (vec1.size != vec2.size) {
            throw IllegalArgumentException("Векторы должны быть одинакового размера")
        }
        
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        
        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }
        
        val denominator = sqrt(norm1) * sqrt(norm2)
        return if (denominator > 0) dotProduct / denominator else 0f
    }
    
    /**
     * Вычисление евклидова расстояния
     */
    fun euclideanDistance(vec1: FloatArray, vec2: FloatArray): Float {
        if (vec1.size != vec2.size) {
            throw IllegalArgumentException("Векторы должны быть одинакового размера")
        }
        
        var sumSquares = 0f
        for (i in vec1.indices) {
            val diff = vec1[i] - vec2[i]
            sumSquares += diff * diff
        }
        
        return sqrt(sumSquares)
    }
    
    /**
     * Получить размерность эмбеддингов
     */
    fun getEmbeddingDimension(): Int {
        // Размерность зависит от используемого метода
        // nomic-embed-text: 768D
        // mxbai-embed-large: 1024D  
        // local: 384D
        return LOCAL_EMBEDDING_DIMENSION
    }
}

/**
 * Представление эмбеддинга с метаданными
 */
data class Embedding(
    val vector: FloatArray,
    val text: String,
    val metadata: Map<String, String> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as Embedding
        
        if (!vector.contentEquals(other.vector)) return false
        if (text != other.text) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = vector.contentHashCode()
        result = 31 * result + text.hashCode()
        return result
    }
    
    fun toJson(): Map<String, Any> {
        return mapOf(
            "vector" to vector.toList(),
            "text" to text,
            "metadata" to metadata,
            "dimension" to vector.size
        )
    }
}

