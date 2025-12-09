package com.test.chatbot.models

/**
 * Результат сравнения моделей
 */
data class ModelComparisonResult(
    val query: String,
    val claudeResult: ModelResponse?,
    val yandexResult: ModelResponse?,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Ответ от конкретной модели с метриками
 */
data class ModelResponse(
    val modelName: String,
    val responseText: String,
    val responseTimeMs: Long,
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val estimatedCostUsd: Double,
    val error: String? = null
)

/**
 * Тарифы моделей (USD за 1M токенов)
 */
object ModelPricing {
    // Claude Sonnet 4 pricing
    const val CLAUDE_INPUT_PER_1M = 3.0   // $3 per 1M input tokens
    const val CLAUDE_OUTPUT_PER_1M = 15.0 // $15 per 1M output tokens
    
    // YandexGPT Lite pricing (примерные тарифы в USD)
    const val YANDEX_INPUT_PER_1M = 0.20  // ~$0.20 per 1M input tokens
    const val YANDEX_OUTPUT_PER_1M = 0.40 // ~$0.40 per 1M output tokens
    
    fun calculateClaudeCost(inputTokens: Int, outputTokens: Int): Double {
        val inputCost = (inputTokens / 1_000_000.0) * CLAUDE_INPUT_PER_1M
        val outputCost = (outputTokens / 1_000_000.0) * CLAUDE_OUTPUT_PER_1M
        return inputCost + outputCost
    }
    
    fun calculateYandexCost(inputTokens: Int, outputTokens: Int): Double {
        val inputCost = (inputTokens / 1_000_000.0) * YANDEX_INPUT_PER_1M
        val outputCost = (outputTokens / 1_000_000.0) * YANDEX_OUTPUT_PER_1M
        return inputCost + outputCost
    }
}

