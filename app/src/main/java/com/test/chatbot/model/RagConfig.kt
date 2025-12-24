package com.test.chatbot.model

/**
 * Конфигурация RAG pipeline
 */
data class RagConfig(
    /** Использовать ли фильтрацию */
    val useFiltering: Boolean = false,
    
    /** 
     * Минимальный порог similarity для включения документа
     * Рекомендации:
     * - 0.3-0.4 - максимальное покрытие (hybrid search компенсирует)
     * - 0.4-0.5 - мягкая фильтрация
     * - 0.5-0.6 - оптимальная фильтрация
     */
    val similarityThreshold: Float = 0.4f,  // Понижено для максимального покрытия
    
    /** Использовать ли reranking через LLM */
    val useReranking: Boolean = false,
    
    /** 
     * Максимальное количество документов для reranking
     * Больше = точнее, но медленнее (каждый документ = LLM вызов)
     */
    val maxRerank: Int = 20,  // Увеличено для лучшего качества
    
    /** 
     * Финальное количество документов в контексте
     * Рекомендации:
     * - 10-15 для максимального покрытия
     * - 5-8 для коротких вопросов
     */
    val finalTopK: Int = 15  // Увеличено для максимального покрытия
) {
    companion object {
        /** Быстрая конфигурация без фильтрации */
        fun fast() = RagConfig(
            useFiltering = false,
            useReranking = false,
            finalTopK = 15  // Максимальное покрытие
        )
        
        /** Оптимальная конфигурация с threshold */
        fun balanced() = RagConfig(
            useFiltering = true,
            similarityThreshold = 0.4f,  // Мягкая фильтрация
            useReranking = false,
            finalTopK = 12  // Увеличено
        )
        
        /** Максимальная точность с LLM reranker */
        fun precise() = RagConfig(
            useFiltering = true,
            similarityThreshold = 0.35f,  // Минимальная фильтрация
            useReranking = true,
            maxRerank = 20,  // Максимум
            finalTopK = 15  // Максимальное покрытие
        )
    }
}

/**
 * Чанк для фильтрации и reranking
 */
data class VectorChunk(
    val content: String,
    val docName: String,
    val docType: String,
    val similarity: Float
)

/**
 * Результат фильтрации/reranking
 */
data class FilteredResult(
    val chunks: List<VectorChunk>,
    val totalBefore: Int,
    val totalAfter: Int,
    val filteredOut: Int,
    val filterMethod: String,
    val avgSimilarityBefore: Float,
    val avgSimilarityAfter: Float
)

