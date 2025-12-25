package com.test.chatbot.rag

import android.content.Context
import android.util.Log
import com.test.chatbot.model.FilteredResult
import com.test.chatbot.model.RagConfig
import com.test.chatbot.model.VectorChunk
import com.test.chatbot.service.RerankerService

/**
 * Full RAG (Retrieval-Augmented Generation) сервис
 * Комбинирует векторный поиск и генерацию ответов через Ollama
 */
class OllamaRAGService(
    private val documentIndexService: DocumentIndexService,
    private val ollamaClient: OllamaClient,
    private val rerankerService: RerankerService
) {
    
    companion object {
        private const val TAG = "OllamaRAGService"
    }
    
    /**
     * RAG: Поиск + Генерация ответа
     */
    suspend fun queryWithRAG(
        question: String,
        topK: Int = 15,  // Максимальное покрытие (было 3 → 5 → 10 → 15)
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
        topK: Int = 15  // Максимальное покрытие (было 3 → 5 → 10 → 15)
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
     * RAG с конфигурируемой фильтрацией/reranking
     */
    suspend fun queryWithRAGFiltered(
        question: String,
        config: RagConfig,
        model: String = "llama3",
        temperature: Double = 0.7
    ): Result<RAGResponseFiltered> {
        return try {
            Log.i(TAG, "RAG запрос (filtered): \"$question\"")
            Log.i(TAG, "Config: filtering=${config.useFiltering}, reranking=${config.useReranking}, threshold=${config.similarityThreshold}")
            
            // 1. Поиск релевантных документов (берём больше для фильтрации)
            val searchResult = documentIndexService.search(question, config.maxRerank)
            
            if (searchResult.isFailure) {
                Log.e(TAG, "❌ Ошибка поиска: ${searchResult.exceptionOrNull()?.message}")
                return Result.failure(searchResult.exceptionOrNull()!!)
            }
            
            val rawResults = searchResult.getOrNull()!!
            
            // Конвертируем в VectorChunk для фильтрации
            val rawChunks = rawResults.map { result ->
                VectorChunk(
                    content = result.chunkText,
                    docName = result.docName,
                    docType = result.docType,
                    similarity = result.similarity
                )
            }
            
            Log.i(TAG, "📚 Найдено ${rawChunks.size} документов ДО фильтрации")
            
            // 2. Фильтрация и reranking
            val filterResult = rerankerService.filterAndRerank(question, rawChunks, config)
            
            Log.i(TAG, "📊 После фильтрации: ${filterResult.totalAfter} документов (метод: ${filterResult.filterMethod})")
            Log.i(TAG, "   Отфильтровано: ${filterResult.filteredOut}")
            Log.i(TAG, "   Avg similarity: ${(filterResult.avgSimilarityBefore * 100).toInt()}% → ${(filterResult.avgSimilarityAfter * 100).toInt()}%")
            
            if (filterResult.chunks.isEmpty()) {
                return Result.success(
                    RAGResponseFiltered(
                        answer = "Извините, после фильтрации не осталось релевантных документов.",
                        sources = emptyList(),
                        confidence = 0f,
                        filterStats = filterResult
                    )
                )
            }
            
            // 3. Формирование контекста из отфильтрованных документов
            val context = buildContextFromChunks(filterResult.chunks)
            
            Log.i(TAG, "Контекст сформирован: ${context.length} символов из ${filterResult.chunks.size} документов")
            
            // 4. Генерация ответа через Ollama
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
            
            // 5. Вычисление финальной уверенности
            val confidence = filterResult.avgSimilarityAfter
            
            val response = RAGResponseFiltered(
                answer = answer,
                sources = filterResult.chunks.map { 
                    RAGSource(
                        docName = it.docName,
                        docType = it.docType,
                        chunkText = it.content,
                        similarity = it.similarity
                    )
                },
                confidence = confidence,
                filterStats = filterResult
            )
            
            Log.i(TAG, "✅ RAG ответ (filtered) сгенерирован (${answer.length} символов, confidence: ${(confidence * 100).toInt()}%)")
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка RAG (filtered): ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Построить контекст из VectorChunk
     */
    private fun buildContextFromChunks(chunks: List<VectorChunk>): String {
        return buildString {
            append("=== РЕЛЕВАНТНАЯ ИНФОРМАЦИЯ ИЗ ДОКУМЕНТОВ ===\n\n")
            
            chunks.forEachIndexed { index, chunk ->
                append("Источник ${index + 1}: ${chunk.docName}\n")
                append("Релевантность: ${(chunk.similarity * 100).toInt()}%\n")
                append("Содержание:\n${chunk.content}\n\n")
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
     * СРАВНЕНИЕ фильтрации: без фильтра vs с фильтром vs с reranker
     */
    suspend fun compareFiltering(
        question: String,
        model: String = "llama3"
    ): Result<FilteringComparison> {
        return try {
            Log.i(TAG, "🔬 СРАВНЕНИЕ ФИЛЬТРАЦИИ для: \"$question\"")
            
            // 1. БЕЗ фильтрации
            val noFilterConfig = RagConfig(
                useFiltering = false,
                useReranking = false,
                finalTopK = 15  // Максимальное покрытие (было 10)
            )
            val noFilterResult = queryWithRAGFiltered(question, noFilterConfig, model)
            
            // 2. С threshold фильтром
            val thresholdConfig = RagConfig(
                useFiltering = true,
                similarityThreshold = 0.4f,  // Очень мягкая фильтрация (было 0.5)
                useReranking = false,
                finalTopK = 12  // Увеличено (было 8)
            )
            val thresholdResult = queryWithRAGFiltered(question, thresholdConfig, model)
            
            // 3. С LLM reranker
            val rerankConfig = RagConfig(
                useFiltering = true,
                similarityThreshold = 0.35f,  // Минимальная фильтрация (было 0.45)
                useReranking = true,
                maxRerank = 20,  // Максимум для rerank (было 15)
                finalTopK = 15  // Максимум (было 10)
            )
            val rerankResult = queryWithRAGFiltered(question, rerankConfig, model)
            
            // 4. Анализ
            val analysis = analyzeFilteringComparison(
                noFilterResult.getOrNull(),
                thresholdResult.getOrNull(),
                rerankResult.getOrNull()
            )
            
            Result.success(FilteringComparison(
                question = question,
                noFilter = noFilterResult.getOrNull(),
                withThreshold = thresholdResult.getOrNull(),
                withRerank = rerankResult.getOrNull(),
                analysis = analysis
            ))
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка сравнения фильтрации: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Анализ результатов разных методов фильтрации
     */
    private fun analyzeFilteringComparison(
        noFilter: RAGResponseFiltered?,
        withThreshold: RAGResponseFiltered?,
        withRerank: RAGResponseFiltered?
    ): String {
        return buildString {
            append("📊 АНАЛИЗ ФИЛЬТРАЦИИ\n")
            append("━━━━━━━━━━━━━━━━━━━━\n\n")
            
            // Сравнение количества источников
            append("📚 Количество источников:\n")
            append("   • Без фильтра: ${noFilter?.sources?.size ?: 0}\n")
            append("   • С threshold: ${withThreshold?.sources?.size ?: 0}\n")
            append("   • С rerank: ${withRerank?.sources?.size ?: 0}\n\n")
            
            // Сравнение качества (confidence)
            append("🎯 Средняя релевантность документов:\n")
            append("   • Без фильтра: ${noFilter?.let { (it.confidence * 100).toInt() } ?: 0}%\n")
            append("   • С threshold: ${withThreshold?.let { (it.confidence * 100).toInt() } ?: 0}%\n")
            append("   • С rerank: ${withRerank?.let { (it.confidence * 100).toInt() } ?: 0}%\n\n")
            
            // Статистика фильтрации
            withThreshold?.filterStats?.let { stats ->
                append("🔍 Threshold фильтр:\n")
                append("   • Отфильтровано: ${stats.filteredOut} из ${stats.totalBefore}\n")
                val change = ((stats.avgSimilarityAfter - stats.avgSimilarityBefore) * 100).toInt()
                append("   • Изменение similarity: ${if (change >= 0) "+" else ""}${change}%\n\n")
            }
            
            withRerank?.filterStats?.let { stats ->
                append("🧠 LLM Reranker:\n")
                append("   • Переранжировано: ${stats.totalBefore} → ${stats.totalAfter}\n")
                val change = ((stats.avgSimilarityAfter - stats.avgSimilarityBefore) * 100).toInt()
                append("   • Изменение similarity: ${if (change >= 0) "+" else ""}${change}%\n\n")
            }
            
            // УЛУЧШЕННАЯ ЛОГИКА ВЫВОДОВ
            append("💡 ВЫВОДЫ:\n\n")
            
            val noFilterConf = noFilter?.confidence ?: 0f
            val thresholdConf = withThreshold?.confidence ?: 0f
            val rerankConf = withRerank?.confidence ?: 0f
            
            // Анализ изменения similarity после rerank
            val rerankDrop = noFilterConf - rerankConf
            val significantDrop = rerankDrop > 0.2f // Падение > 20%
            val verySignificantDrop = rerankDrop > 0.5f // Падение > 50%
            
            // Анализ качества документов
            val highSimilarity = noFilterConf > 0.7f
            val mediumSimilarity = noFilterConf in 0.6f..0.75f
            val lowSimilarity = rerankConf < 0.5f
            val veryLowSimilarity = rerankConf < 0.3f
            
            // Проверка разнообразия источников
            val uniqueDocs = noFilter?.sources?.map { it.docName }?.distinct()?.size ?: 0
            val totalDocs = noFilter?.sources?.size ?: 0
            val lowDiversity = uniqueDocs < totalDocs / 2
            
            when {
                // Сценарий 1: Средний similarity + очень низкий после rerank = нужный документ не найден!
                mediumSimilarity && veryLowSimilarity && verySignificantDrop -> {
                    append("   ⚠️ Embeddings нашли похожие, но не целевые документы!\n\n")
                    append("   ДИАГНОСТИКА:\n")
                    append("   • Базовый поиск: ${(noFilterConf * 100).toInt()}% similarity\n")
                    append("   • После LLM rerank: ${(rerankConf * 100).toInt()}% (падение ${(rerankDrop * 100).toInt()}%!)\n")
                    append("   • Найдены документы ГДЕ УПОМИНАЕТСЯ тема\n")
                    append("   • Но НЕ найден документ ПРО тему\n\n")
                    append("   ПРИЧИНА:\n")
                    append("   • Embeddings модель недостаточно точна\n")
                    append("   • Специализированный документ на 6+ месте\n")
                    append("   • Top-K=${totalDocs} не захватил нужный документ\n\n")
                    append("   РЕШЕНИЕ:\n")
                    append("   • Увеличен Top-K до 10 (применится при следующем запросе)\n")
                    append("   • Понижен threshold до 0.5 для более мягкой фильтрации\n")
                    append("   • Попробуйте запрос еще раз - должно быть лучше\n")
                }
                
                // Сценарий 2: Reranker сильно понизил similarity
                significantDrop && lowSimilarity -> {
                    append("   🎯 LLM Reranker эффективен!\n\n")
                    append("   ПРИЧИНА:\n")
                    append("   • Базовый поиск нашел нерелевантные документы (${(noFilterConf * 100).toInt()}%)\n")
                    append("   • Reranker правильно определил что документы НЕ подходят\n")
                    append("   • Понизил similarity до ${(rerankConf * 100).toInt()}% (честная оценка)\n")
                    append("   • LLM сгенерировал ответ из своих знаний\n\n")
                    append("   РЕКОМЕНДАЦИЯ:\n")
                    append("   • В индексе может не быть документов по этой теме\n")
                    append("   • Или нужно улучшить embeddings модель\n")
                    append("   • Reranker работает правильно - честно оценивает качество\n")
                }
                
                // Сценарий 2: High similarity - документы действительно релевантны
                highSimilarity && rerankConf > 0.65f -> {
                    append("   ✅ Все методы работают хорошо!\n\n")
                    append("   ПРИЧИНА:\n")
                    append("   • Найдены релевантные документы (${(noFilterConf * 100).toInt()}%)\n")
                    append("   • Reranker подтвердил качество (${(rerankConf * 100).toInt()}%)\n")
                    append("   • Фильтрация не критична - все документы хороши\n\n")
                    append("   РЕКОМЕНДАЦИЯ:\n")
                    append("   • Для скорости используйте Threshold фильтр\n")
                    append("   • Reranker дает минимальное улучшение в этом случае\n")
                }
                
                // Сценарий 3: Threshold лучше всех
                thresholdConf > noFilterConf && thresholdConf >= rerankConf -> {
                    append("   ✅ Threshold фильтр оптимален!\n\n")
                    append("   ПРИЧИНА:\n")
                    append("   • Отсек нерелевантные документы\n")
                    append("   • Улучшил similarity: ${(noFilterConf * 100).toInt()}% → ${(thresholdConf * 100).toInt()}%\n")
                    append("   • Быстрая работа без LLM вызовов\n\n")
                    append("   РЕКОМЕНДАЦИЯ:\n")
                    append("   • Используйте Threshold (0.5-0.55) для продакшена\n")
                    append("   • Хорошее соотношение скорость/качество\n")
                }
                
                // Сценарий 4: Reranker улучшил similarity
                rerankConf > thresholdConf -> {
                    append("   ✅ LLM Reranker показал лучший результат!\n\n")
                    append("   ПРИЧИНА:\n")
                    append("   • Умная переоценка документов\n")
                    append("   • Улучшил качество: ${(noFilterConf * 100).toInt()}% → ${(rerankConf * 100).toInt()}%\n")
                    append("   • Нашел скрытые релевантные документы\n\n")
                    append("   РЕКОМЕНДАЦИЯ:\n")
                    append("   • Используйте Reranker когда нужна максимальная точность\n")
                    append("   • Минус: медленнее из-за LLM вызовов\n")
                }
                
                else -> {
                    append("   ⚠️ Все документы слабо релевантны\n\n")
                    append("   ПРИЧИНА:\n")
                    append("   • Similarity всех методов < 60%\n")
                    append("   • В индексе нет подходящих документов\n\n")
                    append("   РЕКОМЕНДАЦИЯ:\n")
                    append("   • Проиндексируйте документы по теме запроса\n")
                    append("   • Текущий индекс не покрывает эту тему\n")
                }
            }
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
            // Чистый ответ
            append(answer)
            
            // Детальный список источников
            if (sources.isNotEmpty()) {
                append("\n\n")
                append("━━━━━━━━━━━━━━━━━━━━\n")
                append("📚 ИСТОЧНИКИ (${sources.size}):\n")
                append("━━━━━━━━━━━━━━━━━━━━\n\n")
                
                sources.take(5).forEachIndexed { index, source ->
                    append("${index + 1}. ${source.docName.removeSuffix(".txt")}\n")
                    append("   📊 Релевантность: ${(source.similarity * 100).toInt()}%\n")
                    append("   📝 Фрагмент: ${source.chunkText.take(150).trim()}...")
                    if (index < sources.size - 1) append("\n\n")
                }
                
                if (sources.size > 5) {
                    append("\n\n...и ещё ${sources.size - 5} источников")
                }
                
                append("\n\n━━━━━━━━━━━━━━━━━━━━\n")
                append("🎯 Средняя уверенность: ${(confidence * 100).toInt()}%")
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

/**
 * RAG ответ с информацией о фильтрации
 */
data class RAGResponseFiltered(
    val answer: String,
    val sources: List<RAGSource>,
    val confidence: Float,
    val filterStats: FilteredResult
) {
    fun toFormattedString(): String {
        return buildString {
            // Чистый ответ
            append(answer)
            
            // Статистика фильтрации
            append("\n\n")
            append("━━━━━━━━━━━━━━━━━━━━\n")
            append("📊 Фильтрация: ${filterStats.filterMethod}\n")
            append("   • Документов: ${filterStats.totalBefore} → ${filterStats.totalAfter}\n")
            append("   • Качество: ${(filterStats.avgSimilarityBefore * 100).toInt()}% → ${(filterStats.avgSimilarityAfter * 100).toInt()}%\n")
            
            // Источники
            if (sources.isNotEmpty()) {
                append("\n📚 Источники:\n")
                sources.forEachIndexed { index, source ->
                    append("   ${index + 1}. ${source.docName} (${(source.similarity * 100).toInt()}%)\n")
                }
            }
        }
    }
}

/**
 * Результат сравнения разных методов фильтрации
 */
data class FilteringComparison(
    val question: String,
    val noFilter: RAGResponseFiltered?,
    val withThreshold: RAGResponseFiltered?,
    val withRerank: RAGResponseFiltered?,
    val analysis: String
) {
    fun toFormattedString(): String {
        return buildString {
            append("🔬 СРАВНЕНИЕ МЕТОДОВ ФИЛЬТРАЦИИ\n")
            append("━━━━━━━━━━━━━━━━━━━━\n\n")
            
            append("❓ ВОПРОС:\n$question\n\n")
            append("━━━━━━━━━━━━━━━━━━━━\n\n")
            
            // Без фильтра
            noFilter?.let {
                append("1️⃣ БЕЗ ФИЛЬТРА\n")
                append("━━━━━━━━━━━━━━━━━━━━\n")
                append(it.answer.take(200))
                if (it.answer.length > 200) append("...")
                append("\n\n📊 Источников: ${it.sources.size}, качество: ${(it.confidence * 100).toInt()}%\n\n")
            }
            
            // С threshold
            withThreshold?.let {
                append("2️⃣ С THRESHOLD ФИЛЬТРОМ\n")
                append("━━━━━━━━━━━━━━━━━━━━\n")
                append(it.answer.take(200))
                if (it.answer.length > 200) append("...")
                append("\n\n📊 Источников: ${it.sources.size}, качество: ${(it.confidence * 100).toInt()}%\n")
                append("   Отфильтровано: ${it.filterStats.filteredOut}\n\n")
            }
            
            // С reranker
            withRerank?.let {
                append("3️⃣ С LLM RERANKER\n")
                append("━━━━━━━━━━━━━━━━━━━━\n")
                append(it.answer.take(200))
                if (it.answer.length > 200) append("...")
                append("\n\n📊 Источников: ${it.sources.size}, качество: ${(it.confidence * 100).toInt()}%\n")
                append("   Переранжировано: ${it.filterStats.totalBefore} → ${it.filterStats.totalAfter}\n\n")
            }
            
            append("━━━━━━━━━━━━━━━━━━━━\n\n")
            append(analysis)
        }
    }
}

