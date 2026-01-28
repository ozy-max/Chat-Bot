package com.test.chatbot.models

/**
 * Шаблоны промптов и настройки для различных задач
 */
object PromptTemplates {
    
    /**
     * Типы задач с рекомендованными параметрами
     */
    enum class TaskType(
        val displayName: String,
        val icon: String,
        val recommendedTemperature: Double,
        val recommendedMaxTokens: Int,
        val recommendedContextWindow: Int,
        val systemPrompt: String
    ) {
        CHAT(
            displayName = "Общение",
            icon = "💬",
            recommendedTemperature = 0.7,
            recommendedMaxTokens = 2048,
            recommendedContextWindow = 4096,
            systemPrompt = "Ты дружелюбный AI ассистент. Отвечай естественно и помогай пользователю."
        ),
        CODE(
            displayName = "Код",
            icon = "💻",
            recommendedTemperature = 0.2,
            recommendedMaxTokens = 4096,
            recommendedContextWindow = 8192,
            systemPrompt = "Ты опытный программист. Пиши чистый, эффективный код с комментариями."
        ),
        CREATIVE(
            displayName = "Креатив",
            icon = "🎨",
            recommendedTemperature = 0.9,
            recommendedMaxTokens = 3072,
            recommendedContextWindow = 4096,
            systemPrompt = "Ты креативный писатель. Создавай интересные и оригинальные тексты."
        ),
        ANALYSIS(
            displayName = "Анализ",
            icon = "📊",
            recommendedTemperature = 0.3,
            recommendedMaxTokens = 4096,
            recommendedContextWindow = 8192,
            systemPrompt = "Ты аналитик. Предоставляй точные, структурированные и детальные анализы."
        ),
        TRANSLATION(
            displayName = "Перевод",
            icon = "🌐",
            recommendedTemperature = 0.3,
            recommendedMaxTokens = 2048,
            recommendedContextWindow = 4096,
            systemPrompt = "Ты профессиональный переводчик. Делай точные переводы с сохранением смысла."
        ),
        SUMMARY(
            displayName = "Краткое изложение",
            icon = "📝",
            recommendedTemperature = 0.4,
            recommendedMaxTokens = 1024,
            recommendedContextWindow = 8192,
            systemPrompt = "Ты эксперт по краткому изложению. Выделяй главное и структурируй информацию."
        ),
        EXPLAIN(
            displayName = "Объяснение",
            icon = "🎓",
            recommendedTemperature = 0.5,
            recommendedMaxTokens = 3072,
            recommendedContextWindow = 4096,
            systemPrompt = "Ты учитель. Объясняй сложные вещи простым языком с примерами."
        ),
        DEBUG(
            displayName = "Отладка",
            icon = "🐛",
            recommendedTemperature = 0.2,
            recommendedMaxTokens = 4096,
            recommendedContextWindow = 8192,
            systemPrompt = "Ты эксперт по отладке. Находи ошибки и предлагай решения с объяснениями."
        ),
        DATA_ANALYSIS(
            displayName = "Анализ данных",
            icon = "📊",
            recommendedTemperature = 0.3,
            recommendedMaxTokens = 4096,
            recommendedContextWindow = 8192,
            systemPrompt = "Ты эксперт по анализу данных. Анализируй данные из файлов, находи паттерны и предоставляй статистику."
        )
    }
    
    /**
     * Конфигурация модели Ollama
     */
    data class ModelConfig(
        val modelName: String,
        val displayName: String,
        val quantization: String,
        val description: String,
        val approximateSize: String,
        val speedRating: Int // 1-5, где 5 = самая быстрая
    )
    
    /**
     * Доступные модели Ollama
     */
    val AVAILABLE_MODELS = listOf(
        ModelConfig(
            modelName = "llama3:latest",
            displayName = "Llama 3",
            quantization = "Q4_0",
            description = "Баланс качества и скорости",
            approximateSize = "4.7 GB",
            speedRating = 4
        ),
        ModelConfig(
            modelName = "llama3:8b",
            displayName = "Llama 3 8B",
            quantization = "Q4_K_M",
            description = "Высокое качество, средняя скорость",
            approximateSize = "4.7 GB",
            speedRating = 3
        ),
        ModelConfig(
            modelName = "llama3:8b-instruct-q8_0",
            displayName = "Llama 3 8B Q8",
            quantization = "Q8_0",
            description = "Максимальное качество, медленнее",
            approximateSize = "8.5 GB",
            speedRating = 2
        ),
        ModelConfig(
            modelName = "llama3:8b-instruct-q4_0",
            displayName = "Llama 3 8B Q4",
            quantization = "Q4_0",
            description = "Быстрая работа, компактная",
            approximateSize = "4.7 GB",
            speedRating = 5
        ),
        ModelConfig(
            modelName = "mistral:latest",
            displayName = "Mistral",
            quantization = "Q4_0",
            description = "Быстрая модель для общих задач",
            approximateSize = "4.1 GB",
            speedRating = 5
        ),
        ModelConfig(
            modelName = "codellama:latest",
            displayName = "Code Llama",
            quantization = "Q4_0",
            description = "Специализация на коде",
            approximateSize = "3.8 GB",
            speedRating = 4
        ),
        ModelConfig(
            modelName = "phi3:latest",
            displayName = "Phi-3",
            quantization = "Q4_K_M",
            description = "Компактная, очень быстрая",
            approximateSize = "2.3 GB",
            speedRating = 5
        )
    )
    
    /**
     * Опции контекстного окна
     */
    val CONTEXT_WINDOW_OPTIONS = listOf(
        2048 to "2K",
        4096 to "4K",
        8192 to "8K",
        16384 to "16K",
        32768 to "32K"
    )
    
    /**
     * Получить системный промпт для задачи
     */
    fun getSystemPrompt(taskType: TaskType): String {
        return taskType.systemPrompt
    }
    
    /**
     * Создать промпт с контекстом для Ollama
     */
    fun createContextualPrompt(
        taskType: TaskType,
        userMessage: String,
        previousContext: String? = null
    ): String {
        return buildString {
            appendLine("System: ${taskType.systemPrompt}")
            appendLine()
            
            if (!previousContext.isNullOrBlank()) {
                appendLine("Previous context:")
                appendLine(previousContext)
                appendLine()
            }
            
            appendLine("User: $userMessage")
        }
    }
    
    /**
     * Получить рекомендованные параметры для задачи
     */
    fun getRecommendedParams(taskType: TaskType): Triple<Double, Int, Int> {
        return Triple(
            taskType.recommendedTemperature,
            taskType.recommendedMaxTokens,
            taskType.recommendedContextWindow
        )
    }
}
