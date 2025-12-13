package com.test.chatbot.models

/**
 * Настройки компрессии диалога
 */
data class CompressionSettings(
    val enabled: Boolean = true,
    val threshold: Int = 10, // Количество сообщений до сжатия
    val keepRecentMessages: Int = 4 // Сколько последних сообщений сохранять без сжатия
)

/**
 * Состояние компрессии диалога
 */
data class CompressionState(
    val isEnabled: Boolean = false,
    val compressionCount: Int = 0, // Сколько раз было выполнено сжатие
    val originalTokenCount: Int = 0, // Токенов в оригинальной истории (до сжатия)
    val compressedTokenCount: Int = 0, // Токенов в summary
    val savedTokens: Int = 0, // Сэкономлено токенов на КАЖДОМ следующем запросе
    val savingsPercent: Float = 0f, // Процент экономии
    val hasSummary: Boolean = false, // Есть ли активное summary
    val summaryPreview: String = "", // Превью текущего summary
    // Новые поля для точного расчёта
    val currentHistoryTokens: Int = 0, // Текущий размер истории в токенах
    val virtualHistoryTokens: Int = 0, // Сколько было бы БЕЗ компрессии
    val totalSavedTokens: Int = 0 // Всего сэкономлено за все запросы после компрессии
)

/**
 * Результат компрессии
 */
data class CompressionResult(
    val success: Boolean,
    val summary: String,
    val originalMessages: Int,
    val originalTokens: Int,
    val compressedTokens: Int,
    val error: String? = null
)

/**
 * Системный промпт для суммаризации
 */
object SummarizationPrompts {
    // Промпт для извлечения информации о пользователе (только из сообщений пользователя)
    val SUMMARIZE_CONVERSATION = """
Извлеки ВСЮ информацию О ПОЛЬЗОВАТЕЛЕ из его сообщений.
Это могут быть: имя, возраст, профессия, интересы, предпочтения, планы, задачи, проекты и любые другие факты.

НЕ включай в резюме:
- Что делал или отвечал ассистент
- Технические детали работы
- Инструкции и команды

Формат: краткие пункты о пользователе.
Пример:
- Имя: Игорь
- Профессия: Android разработчик
- Работает над проектом ChatBot
""".trimIndent()

    // Более подробный промпт (опционально)
    val SUMMARIZE_DETAILED = """
Создай резюме информации о пользователе:
- Личные данные (имя, возраст, город)
- Профессия и навыки
- Текущие проекты и задачи
- Интересы и предпочтения
""".trimIndent()
}

