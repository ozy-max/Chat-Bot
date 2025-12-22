package com.test.chatbot.rag

import android.content.Context
import android.util.Log

/**
 * Full RAG (Retrieval-Augmented Generation) сервис
 * Комбинирует векторный поиск и генерацию ответов через Ollama
 */
class OllamaRAGService(
    private val documentIndexService: DocumentIndexService,
    private val ollamaClient: OllamaClient
) {
    
    companion object {
        private const val TAG = "OllamaRAGService"
    }
    
    /**
     * RAG: Поиск + Генерация ответа
     */
    suspend fun queryWithRAG(
        question: String,
        topK: Int = 3,
        model: String = "llama3",
        temperature: Double = 0.7
    ): Result<RAGResponse> {
        return try {
            Log.i(TAG, "RAG запрос: \"$question\"")
            
            // 1. Поиск релевантных документов
            val searchResult = documentIndexService.search(question, topK)
            
            if (searchResult.isFailure) {
                Log.e(TAG, "❌ Ошибка поиска: ${searchResult.exceptionOrNull()?.message}")
                return Result.failure(searchResult.exceptionOrNull()!!)
            }
            
            val searchResults = searchResult.getOrNull()!!
            
            // Логируем найденные документы
            Log.i(TAG, "📚 Найдено ${searchResults.size} документов:")
            searchResults.forEachIndexed { index, result ->
                Log.i(TAG, "  ${index + 1}. ${result.docName} - similarity: ${(result.similarity * 100).toInt()}%")
                // Логируем текст kotlin_basics для отладки
                if (result.docName.contains("kotlin")) {
                    Log.i(TAG, "    📝 Текст kotlin чанка: ${result.chunkText.take(200)}...")
                }
            }
            
            if (searchResults.isEmpty()) {
                return Result.success(
                    RAGResponse(
                        answer = "Извините, я не нашёл релевантной информации в проиндексированных документах.",
                        sources = emptyList(),
                        confidence = 0f
                    )
                )
            }
            
            // 2. Формирование контекста
            val context = buildContext(searchResults)
            
            Log.i(TAG, "Контекст сформирован: ${context.length} символов из ${searchResults.size} документов")
            
            // 3. Генерация ответа через Ollama
            val generateResult = ollamaClient.generateText(
                prompt = question,
                model = model,
                context = context,
                temperature = temperature
            )
            
            if (generateResult.isFailure) {
                return Result.failure(generateResult.exceptionOrNull()!!)
            }
            
            val answer = generateResult.getOrNull()!!
            
            // 4. Вычисление уверенности (средняя релевантность источников)
            val confidence = searchResults.map { it.similarity }.average().toFloat()
            
            val response = RAGResponse(
                answer = answer,
                sources = searchResults.map { 
                    RAGSource(
                        docName = it.docName,
                        docType = it.docType,
                        chunkText = it.chunkText,
                        similarity = it.similarity
                    )
                },
                confidence = confidence
            )
            
            Log.i(TAG, "✅ RAG ответ сгенерирован (${answer.length} символов, confidence: ${(confidence * 100).toInt()}%)")
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка RAG: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Построить контекст из найденных документов
     */
    private fun buildContext(searchResults: List<SearchResult>): String {
        return buildString {
            append("=== РЕЛЕВАНТНАЯ ИНФОРМАЦИЯ ИЗ ДОКУМЕНТОВ ===\n\n")
            
            searchResults.forEachIndexed { index, result ->
                append("Источник ${index + 1}: ${result.docName}\n")
                append("Релевантность: ${(result.similarity * 100).toInt()}%\n")
                append("Содержание:\n${result.chunkText}\n\n")
                append("---\n\n")
            }
            
            append("=== ИНСТРУКЦИИ ===\n")
            append("Используй информацию из источников выше для ответа.\n")
            append("Не придумывай информацию, которой нет в источниках.\n")
            append("Если информации недостаточно - скажи об этом.\n")
            append("Отвечай на русском языке, структурируй ответ.\n")
        }
    }
    
    /**
     * Получить только ответ без метаданных
     */
    suspend fun simpleQuery(
        question: String,
        topK: Int = 3
    ): Result<String> {
        val result = queryWithRAG(question, topK)
        
        return if (result.isSuccess) {
            Result.success(result.getOrNull()!!.answer)
        } else {
            result.map { it.answer }
        }
    }
    
    /**
     * Получить DocumentIndexService для индексации
     */
    fun getDocumentIndexService(): DocumentIndexService = documentIndexService
}

/**
 * Ответ RAG системы
 */
data class RAGResponse(
    val answer: String,
    val sources: List<RAGSource>,
    val confidence: Float
) {
    fun toFormattedString(): String {
        return buildString {
            // Только чистый ответ
            append(answer)
            
            // Краткий список источников в конце (если есть)
            if (sources.isNotEmpty()) {
                append("\n\n")
                append("━━━━━━━━━━━━━━━━━━━━\n")
                append("📚 Источники: ")
                append(sources.joinToString(", ") { it.docName.removeSuffix(".txt") })
            }
        }
    }
}

/**
 * Источник информации для RAG
 */
data class RAGSource(
    val docName: String,
    val docType: String,
    val chunkText: String,
    val similarity: Float
)

