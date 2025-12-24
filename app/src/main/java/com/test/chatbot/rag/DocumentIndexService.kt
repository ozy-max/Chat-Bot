package com.test.chatbot.rag

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Главный сервис для индексации документов
 * Объединяет chunking, embeddings и storage
 * Поддерживает Ollama для качественных эмбеддингов
 */
class DocumentIndexService(
    private val context: Context,
    ollamaClient: OllamaClient? = null
) {
    
    companion object {
        private const val TAG = "DocumentIndexService"
    }
    
    private val textChunker = TextChunker()
    private val embeddingService = EmbeddingService(ollamaClient)
    private val vectorStorage = VectorStorage(context)
    
    /**
     * Получить EmbeddingService для настройки
     */
    fun getEmbeddingService(): EmbeddingService = embeddingService
    
    /**
     * Проверить доступность Ollama перед индексацией
     */
    suspend fun checkOllamaAvailability(): Result<String> {
        val isAvailable = embeddingService.isOllamaAvailable()
        
        return if (isAvailable) {
            Result.success("✅ Ollama доступна. Будут использоваться качественные эмбеддинги.")
        } else {
            Result.failure(Exception(
                "⚠️ Ollama недоступна!\n\n" +
                "Для качественного поиска необходимо настроить Ollama:\n" +
                "1. Выполните: /ollama config http://10.0.2.2:11434\n" +
                "2. Убедитесь что Ollama запущена на компьютере\n" +
                "3. Проверьте: /ollama status\n\n" +
                "Без Ollama поиск будет работать плохо."
            ))
        }
    }
    
    /**
     * Индексировать текстовый документ
     */
    suspend fun indexDocument(
        name: String,
        content: String,
        type: String = "text",
        metadata: Map<String, String> = emptyMap(),
        chunkingStrategy: ChunkingStrategy = ChunkingStrategy.SMART
    ): Result<IndexResult> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Начало индексации документа: $name")
            
            // 1. Сохраняем документ
            val document = Document(
                name = name,
                type = type,
                content = content,
                metadata = metadata
            )
            
            val docIdResult = vectorStorage.saveDocument(document)
            if (docIdResult.isFailure) {
                return@withContext Result.failure(docIdResult.exceptionOrNull()!!)
            }
            val docId = docIdResult.getOrNull()!!
            
            // 2. Разбиваем на чанки
            val chunks = when (chunkingStrategy) {
                ChunkingStrategy.BY_SIZE -> textChunker.chunkBySize(content)
                ChunkingStrategy.BY_SENTENCES -> textChunker.chunkBySentences(content)
                ChunkingStrategy.BY_PARAGRAPHS -> textChunker.chunkByParagraphs(content)
                ChunkingStrategy.SMART -> textChunker.chunkSmart(content)
                ChunkingStrategy.CODE -> textChunker.chunkCode(content, metadata["language"] ?: "kotlin")
            }
            
            Log.i(TAG, "Создано ${chunks.size} чанков")
            
            // 3. Генерируем эмбеддинги и сохраняем
            var successCount = 0
            var failCount = 0
            
            for (chunk in chunks) {
                try {
                    // Сохраняем чанк
                    val chunkIdResult = vectorStorage.saveChunk(docId, chunk)
                    if (chunkIdResult.isFailure) {
                        failCount++
                        continue
                    }
                    val chunkId = chunkIdResult.getOrNull()!!
                    
                    // Генерируем эмбеддинг
                    val embeddingResult = embeddingService.generateEmbedding(chunk.text)
                    if (embeddingResult.isFailure) {
                        failCount++
                        continue
                    }
                    val embedding = embeddingResult.getOrNull()!!
                    
                    // Сохраняем эмбеддинг
                    val embIdResult = vectorStorage.saveEmbedding(chunkId, embedding)
                    if (embIdResult.isSuccess) {
                        successCount++
                    } else {
                        failCount++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка обработки чанка ${chunk.index}: ${e.message}")
                    failCount++
                }
            }
            
            Log.i(TAG, "✅ Индексация завершена: успешно $successCount, ошибок $failCount")
            
            Result.success(
                IndexResult(
                    docId = docId,
                    docName = name,
                    chunksTotal = chunks.size,
                    chunksIndexed = successCount,
                    chunksFailed = failCount
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка индексации: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Индексировать файл
     */
    suspend fun indexFile(
        filePath: String,
        chunkingStrategy: ChunkingStrategy = ChunkingStrategy.SMART
    ): Result<IndexResult> = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            
            if (!file.exists()) {
                return@withContext Result.failure(Exception("Файл не найден: $filePath"))
            }
            
            // Определяем тип файла
            val type = when (file.extension.lowercase()) {
                "md" -> "markdown"
                "kt", "java", "py", "js", "ts" -> "code"
                "txt" -> "text"
                else -> "text"
            }
            
            // Читаем содержимое
            val content = file.readText()
            
            // Определяем стратегию chunking для кода
            val strategy = if (type == "code") ChunkingStrategy.CODE else chunkingStrategy
            
            // Индексируем
            indexDocument(
                name = file.name,
                content = content,
                type = type,
                metadata = mapOf(
                    "path" to filePath,
                    "extension" to file.extension,
                    "language" to file.extension
                ),
                chunkingStrategy = strategy
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка индексации файла: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Индексировать директорию
     */
    suspend fun indexDirectory(
        dirPath: String,
        recursive: Boolean = true,
        fileExtensions: List<String> = listOf("md", "txt", "kt", "java", "py", "js")
    ): Result<List<IndexResult>> = withContext(Dispatchers.IO) {
        try {
            val dir = File(dirPath)
            
            if (!dir.exists() || !dir.isDirectory) {
                return@withContext Result.failure(Exception("Директория не найдена: $dirPath"))
            }
            
            val files = if (recursive) {
                dir.walkTopDown().filter { it.isFile }
            } else {
                dir.listFiles()?.asSequence() ?: emptySequence()
            }
            
            val results = mutableListOf<IndexResult>()
            
            files.forEach { file ->
                if (fileExtensions.contains(file.extension.lowercase())) {
                    val result = indexFile(file.absolutePath)
                    if (result.isSuccess) {
                        results.add(result.getOrNull()!!)
                    }
                }
            }
            
            Log.i(TAG, "✅ Проиндексировано ${results.size} файлов из директории")
            Result.success(results)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка индексации директории: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Семантический поиск по индексу
     */
    suspend fun search(
        query: String,
        topK: Int = 5
    ): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Поиск: \"$query\"")
            
            // Генерируем эмбеддинг для запроса
            val queryEmbeddingResult = embeddingService.generateEmbedding(query)
            if (queryEmbeddingResult.isFailure) {
                return@withContext Result.failure(queryEmbeddingResult.exceptionOrNull()!!)
            }
            val queryEmbedding = queryEmbeddingResult.getOrNull()!!
            
            // Ищем похожие векторы с keyword boosting (hybrid search)
            val searchResult = vectorStorage.searchSimilarWithKeywordBoost(
                query = query,
                queryEmbedding = queryEmbedding,
                topK = topK,
                keywordBoost = 0.3f  // +30% к similarity при keyword match (было 15%)
            )
            
            if (searchResult.isSuccess) {
                val results = searchResult.getOrNull()!!
                Log.i(TAG, "✅ Найдено ${results.size} результатов")
            }
            
            searchResult
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка поиска: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Получить список всех проиндексированных документов
     */
    suspend fun listDocuments(): Result<List<Document>> {
        return vectorStorage.getAllDocuments()
    }
    
    /**
     * Удалить документ из индекса
     */
    suspend fun deleteDocument(docId: Long): Result<Unit> {
        return vectorStorage.deleteDocument(docId)
    }
    
    /**
     * Получить статистику индекса
     */
    suspend fun getStats(): Result<StorageStats> {
        return vectorStorage.getStats()
    }
    
    /**
     * Очистить весь индекс
     */
    suspend fun clearIndex(): Result<Unit> {
        return vectorStorage.clearAll()
    }
    
    /**
     * Полный сброс базы данных (удаление файла БД)
     */
    suspend fun resetDatabase(context: Context): Result<Unit> {
        return vectorStorage.deleteDatabase(context)
    }
    
    /**
     * Проверить целостность индекса
     */
    suspend fun verifyIndex(): Result<IndexVerification> = withContext(Dispatchers.IO) {
        try {
            val stats = vectorStorage.getStats().getOrNull()
                ?: return@withContext Result.failure(Exception("Не удалось получить статистику"))
            
            val verification = IndexVerification(
                isHealthy = stats.documentCount > 0 && stats.embeddingCount > 0,
                documentCount = stats.documentCount,
                chunkCount = stats.chunkCount,
                embeddingCount = stats.embeddingCount,
                issues = mutableListOf<String>().apply {
                    if (stats.documentCount == 0) add("Нет проиндексированных документов")
                    if (stats.chunkCount == 0) add("Нет чанков")
                    if (stats.embeddingCount == 0) add("Нет эмбеддингов")
                    if (stats.chunkCount != stats.embeddingCount) {
                        add("Количество чанков и эмбеддингов не совпадает")
                    }
                }
            )
            
            Result.success(verification)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Стратегия разбиения на чанки
 */
enum class ChunkingStrategy {
    BY_SIZE,        // Фиксированный размер
    BY_SENTENCES,   // По предложениям
    BY_PARAGRAPHS,  // По параграфам
    SMART,          // Умное разбиение
    CODE            // Специально для кода
}

/**
 * Результат индексации
 */
data class IndexResult(
    val docId: Long,
    val docName: String,
    val chunksTotal: Int,
    val chunksIndexed: Int,
    val chunksFailed: Int
) {
    val successRate: Float get() = if (chunksTotal > 0) chunksIndexed.toFloat() / chunksTotal else 0f
    
    fun toSummary(): String {
        return buildString {
            append("✅ Документ проиндексирован: $docName\n")
            append("ID: $docId\n")
            append("Чанков: $chunksIndexed/$chunksTotal")
            if (chunksFailed > 0) {
                append(" (ошибок: $chunksFailed)")
            }
            append("\nУспешность: ${(successRate * 100).toInt()}%")
        }
    }
}

/**
 * Проверка целостности индекса
 */
data class IndexVerification(
    val isHealthy: Boolean,
    val documentCount: Int,
    val chunkCount: Int,
    val embeddingCount: Int,
    val issues: List<String>
) {
    fun toSummary(): String {
        return buildString {
            if (isHealthy) {
                append("✅ Индекс в порядке\n\n")
            } else {
                append("⚠️ Обнаружены проблемы\n\n")
            }
            
            append("📊 Статистика:\n")
            append("Документов: $documentCount\n")
            append("Чанков: $chunkCount\n")
            append("Эмбеддингов: $embeddingCount\n")
            
            if (issues.isNotEmpty()) {
                append("\n❌ Проблемы:\n")
                issues.forEach { issue ->
                    append("• $issue\n")
                }
            }
        }
    }
}

