package com.test.chatbot.service

import com.test.chatbot.model.FilteredResult
import com.test.chatbot.model.RagConfig
import com.test.chatbot.model.VectorChunk
import com.test.chatbot.rag.OllamaClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/**
 * Сервис для фильтрации и реранкинга результатов поиска
 */
class RerankerService(
    private val ollamaClient: OllamaClient
) {
    
    /**
     * Применяет фильтрацию/reranking согласно конфигурации
     */
    suspend fun filterAndRerank(
        query: String,
        chunks: List<VectorChunk>,
        config: RagConfig
    ): FilteredResult = withContext(Dispatchers.Default) {
        
        val avgBefore = chunks.map { it.similarity }.average().toFloat()
        
        // 1. Threshold фильтрация (если включена)
        val afterThreshold = if (config.useFiltering) {
            filterByThreshold(chunks, config.similarityThreshold)
        } else {
            chunks
        }
        
        // 2. Reranking (если включен)
        val afterRerank = if (config.useReranking && afterThreshold.isNotEmpty()) {
            rerankWithLLM(query, afterThreshold.take(config.maxRerank))
        } else {
            afterThreshold
        }
        
        // 3. Финальная обрезка
        val final = afterRerank.take(config.finalTopK)
        
        val avgAfter = if (final.isNotEmpty()) {
            final.map { it.similarity }.average().toFloat()
        } else {
            0f
        }
        
        FilteredResult(
            chunks = final,
            totalBefore = chunks.size,
            totalAfter = final.size,
            filteredOut = chunks.size - final.size,
            filterMethod = getMethodName(config),
            avgSimilarityBefore = avgBefore,
            avgSimilarityAfter = avgAfter
        )
    }
    
    /**
     * Простая фильтрация по порогу similarity
     */
    private fun filterByThreshold(
        chunks: List<VectorChunk>,
        threshold: Float
    ): List<VectorChunk> {
        return chunks.filter { it.similarity >= threshold }
    }
    
    /**
     * LLM-based reranking: использует модель для оценки релевантности
     */
    private suspend fun rerankWithLLM(
        query: String,
        chunks: List<VectorChunk>
    ): List<VectorChunk> = withContext(Dispatchers.IO) {
        
        // Параллельная оценка релевантности для каждого чанка
        val scored = chunks.map { chunk ->
            async {
                val score = scoreRelevance(query, chunk)
                chunk to score
            }
        }.awaitAll()
        
        // Сортируем по новому score и обновляем similarity
        scored
            .sortedByDescending { it.second }
            .map { (chunk, newScore) ->
                chunk.copy(similarity = newScore)
            }
    }
    
    /**
     * Оценивает релевантность чанка к запросу через LLM
     */
    private suspend fun scoreRelevance(
        query: String,
        chunk: VectorChunk
    ): Float {
        val prompt = """
Ты эксперт по оценке релевантности. Оцени насколько текст релевантен к вопросу.
Ответь ТОЛЬКО числом от 0.0 до 1.0 (например: 0.85).

Вопрос: $query

Текст:
${chunk.content.take(300)}

Релевантность (только число 0.0-1.0):
""".trimIndent()
        
        return try {
            val result = ollamaClient.generateText(
                prompt = prompt,
                maxTokens = 10
            )
            
            if (result.isFailure) {
                return chunk.similarity
            }
            
            val response = result.getOrNull() ?: return chunk.similarity
            
            // Извлекаем число из ответа
            val score = response.trim()
                .replace(",", ".")
                .filter { it.isDigit() || it == '.' }
                .toFloatOrNull() ?: chunk.similarity
            
            // Нормализуем в диапазон 0-1
            score.coerceIn(0f, 1f)
        } catch (e: Exception) {
            // Fallback на оригинальный similarity
            chunk.similarity
        }
    }
    
    /**
     * Получить название метода фильтрации
     */
    private fun getMethodName(config: RagConfig): String {
        return when {
            config.useReranking && config.useFiltering -> 
                "Threshold (${config.similarityThreshold}) + LLM Rerank"
            config.useReranking -> 
                "LLM Rerank"
            config.useFiltering -> 
                "Threshold (${config.similarityThreshold})"
            else -> 
                "No filtering"
        }
    }
    
    /**
     * Автоматический подбор оптимального threshold
     */
    suspend fun suggestThreshold(
        chunks: List<VectorChunk>
    ): Float {
        if (chunks.isEmpty()) return 0.5f
        
        val similarities = chunks.map { it.similarity }.sorted()
        
        // Ищем "gap" - большой скачок в similarity
        var maxGap = 0f
        var bestThreshold = 0.5f
        
        for (i in 0 until similarities.size - 1) {
            val gap = similarities[i + 1] - similarities[i]
            if (gap > maxGap) {
                maxGap = gap
                bestThreshold = (similarities[i] + similarities[i + 1]) / 2
            }
        }
        
        // Если нет явного gap, используем медиану
        return if (maxGap > 0.1f) {
            bestThreshold
        } else {
            similarities[similarities.size / 2]
        }
    }
}

