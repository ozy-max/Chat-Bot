package com.test.chatbot.models

/**
 * Статистика токенов за сессию
 */
data class TokenStats(
    // Текущий запрос
    val lastInputTokens: Int = 0,
    val lastOutputTokens: Int = 0,
    
    // Накопительная статистика за сессию
    val totalInputTokens: Int = 0,
    val totalOutputTokens: Int = 0,
    val totalTokens: Int = 0,
    
    // Количество запросов
    val requestCount: Int = 0,
    
    // Лимиты модели (для Claude Sonnet 4)
    val modelInputLimit: Int = 200_000,  // ~200K токенов контекст
    val modelOutputLimit: Int = 4096     // max_tokens в запросе
) {
    val lastTotalTokens: Int get() = lastInputTokens + lastOutputTokens
    
    // Процент использования контекста
    val contextUsagePercent: Float get() = 
        (totalInputTokens.toFloat() / modelInputLimit * 100).coerceIn(0f, 100f)
    
    // Статус контекста
    val contextStatus: ContextStatus get() = when {
        contextUsagePercent >= 90 -> ContextStatus.CRITICAL
        contextUsagePercent >= 70 -> ContextStatus.WARNING
        contextUsagePercent >= 50 -> ContextStatus.MODERATE
        else -> ContextStatus.NORMAL
    }
}

enum class ContextStatus {
    NORMAL,    // < 50% - зелёный
    MODERATE,  // 50-70% - жёлтый
    WARNING,   // 70-90% - оранжевый
    CRITICAL   // > 90% - красный
}

/**
 * Результат API запроса с информацией о токенах
 */
data class ApiResponseWithTokens(
    val text: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val stopReason: String? = null
)


