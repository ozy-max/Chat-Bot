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
     * Запрос БЕЗ RAG - прямой запрос к LLM
     */
    suspend fun queryWithoutRAG(
        question: String,
        model: String = "llama3"
    ): Result<String> {
        return try {
            Log.i(TAG, "Запрос БЕЗ RAG: \"$question\"")
            
            val prompt = """
                Ответь на вопрос на основе своих знаний.
                
                Вопрос: $question
                
                Ответ:
            """.trimIndent()
            
            val result = ollamaClient.generateText(prompt, model)
            
            if (result.isSuccess) {
                val answer = result.getOrNull()!!
                Log.i(TAG, "✅ Ответ БЕЗ RAG получен (${answer.length} символов)")
                Result.success(answer)
            } else {
                Result.failure(result.exceptionOrNull()!!)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка запроса БЕЗ RAG: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * СРАВНЕНИЕ: запрос С RAG и БЕЗ RAG
     */
    suspend fun compareRAG(
        question: String,
        model: String = "llama3",
        topK: Int = 10
    ): Result<RAGComparison> {
        return try {
            Log.i(TAG, "🔬 СРАВНЕНИЕ RAG для: \"$question\"")
            
            // 1. Запрос С RAG
            val withRAGResult = queryWithRAG(question, topK, model)
            val withRAG = if (withRAGResult.isSuccess) {
                val ragResponse = withRAGResult.getOrNull()!!
                RAGAnswer(
                    answer = ragResponse.answer,
                    sources = ragResponse.sources.map { it.docName }.distinct().take(3),
                    confidence = ragResponse.confidence
                )
            } else {
                RAGAnswer(
                    answer = "Ошибка: ${withRAGResult.exceptionOrNull()?.message}",
                    sources = emptyList(),
                    confidence = 0f
                )
            }
            
            // 2. Запрос БЕЗ RAG
            val withoutRAGResult = queryWithoutRAG(question, model)
            val withoutRAG = if (withoutRAGResult.isSuccess) {
                withoutRAGResult.getOrNull()!!
            } else {
                "Ошибка: ${withoutRAGResult.exceptionOrNull()?.message}"
            }
            
            // 3. Анализ разницы
            val analysis = analyzeComparison(question, withRAG, withoutRAG)
            
            Result.success(RAGComparison(
                question = question,
                withRAG = withRAG,
                withoutRAG = withoutRAG,
                analysis = analysis
            ))
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка сравнения: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Анализ разницы между ответами с RAG и без RAG
     */
    private fun analyzeComparison(question: String, withRAG: RAGAnswer, withoutRAG: String): String {
        return buildString {
            append("📊 АНАЛИЗ СРАВНЕНИЯ\n")
            append("━━━━━━━━━━━━━━━━━━━━\n\n")
            
            // Проверяем наличие источников
            if (withRAG.sources.isNotEmpty()) {
                append("✅ RAG использовал документы:\n")
                withRAG.sources.forEach { source ->
                    append("   • $source\n")
                }
                append("\n")
            }
            
            // Анализируем специфичность
            val hasSpecificInfo = withRAG.sources.any { source ->
                question.lowercase().contains(source.lowercase().substringBefore("_"))
            }
            
            if (hasSpecificInfo) {
                append("🎯 RAG ПОМОГ:\n")
                append("   • Найдены релевантные документы\n")
                append("   • Ответ основан на конкретных источниках\n")
                append("   • Уверенность: ${(withRAG.confidence * 100).toInt()}%\n\n")
            } else {
                append("⚠️ RAG НЕ ПОМОГ:\n")
                append("   • Найденные документы не релевантны вопросу\n")
                append("   • Ответ основан на общих знаниях LLM\n\n")
            }
            
            // Сравнение длины ответов
            val lengthDiff = kotlin.math.abs(withRAG.answer.length - withoutRAG.length)
            if (lengthDiff > 100) {
                append("📏 Длина ответов:\n")
                append("   • С RAG: ${withRAG.answer.length} символов\n")
                append("   • Без RAG: ${withoutRAG.length} символов\n")
                append("   • Разница: $lengthDiff символов\n\n")
            }
            
            append("💡 ВЫВОД:\n")
            if (hasSpecificInfo && withRAG.confidence > 0.6f) {
                append("   RAG эффективен для этого вопроса.\n")
                append("   Ответ содержит конкретную информацию из документов.")
            } else {
                append("   RAG малоэффективен для этого вопроса.\n")
                append("   LLM может ответить на основе общих знаний.")
            }
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

/**
 * Ответ с RAG (упрощённый для сравнения)
 */
data class RAGAnswer(
    val answer: String,
    val sources: List<String>,
    val confidence: Float
)

/**
 * Результат сравнения RAG vs No-RAG
 */
data class RAGComparison(
    val question: String,
    val withRAG: RAGAnswer,
    val withoutRAG: String,
    val analysis: String
) {
    fun toFormattedString(): String {
        return buildString {
            append("🔬 СРАВНЕНИЕ RAG\n")
            append("━━━━━━━━━━━━━━━━━━━━\n\n")
            
            append("❓ ВОПРОС:\n$question\n\n")
            append("━━━━━━━━━━━━━━━━━━━━\n\n")
            
            append("✅ ОТВЕТ С RAG:\n")
            append(withRAG.answer)
            if (withRAG.sources.isNotEmpty()) {
                append("\n\n📚 Источники: ${withRAG.sources.joinToString(", ")}")
            }
            append("\n\n")
            append("━━━━━━━━━━━━━━━━━━━━\n\n")
            
            append("🔄 ОТВЕТ БЕЗ RAG:\n")
            append(withoutRAG)
            append("\n\n")
            append("━━━━━━━━━━━━━━━━━━━━\n\n")
            
            append(analysis)
        }
    }
}

