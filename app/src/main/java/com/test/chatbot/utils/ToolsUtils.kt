package com.test.chatbot.utils

import com.test.chatbot.models.InputSchema
import com.test.chatbot.models.Property
import com.test.chatbot.models.Tool

object ToolsUtils {
    
    // Список доступных инструментов
    val tools = listOf(
        Tool(
            name = "get_weather",
            description = "Получить информацию о погоде для указанного города. Используй этот инструмент когда пользователь спрашивает о погоде.",
            inputSchema = InputSchema(
                type = "object",
                properties = mapOf(
                    "city" to Property(
                        type = "string",
                        description = "Название города на русском или английском языке"
                    ),
                    "units" to Property(
                        type = "string",
                        description = "Единицы измерения температуры",
                        enum = listOf("celsius", "fahrenheit")
                    )
                ),
                required = listOf("city")
            )
        ),
        Tool(
            name = "calculator",
            description = "Выполнить математическое вычисление. Используй этот инструмент для любых математических операций.",
            inputSchema = InputSchema(
                type = "object",
                properties = mapOf(
                    "expression" to Property(
                        type = "string",
                        description = "Математическое выражение для вычисления (например, '2 + 2', '10 * 5', '100 / 4')"
                    )
                ),
                required = listOf("expression")
            )
        ),
        Tool(
            name = "get_time",
            description = "Получить текущее время. Используй этот инструмент когда пользователь спрашивает о времени.",
            inputSchema = InputSchema(
                type = "object",
                properties = mapOf(
                    "timezone" to Property(
                        type = "string",
                        description = "Часовой пояс (например, 'Europe/Moscow', 'UTC', 'America/New_York')"
                    )
                ),
                required = emptyList()
            )
        ),
        Tool(
            name = "search_web",
            description = "Поиск статей в интернете по запросу. Используй этот инструмент когда пользователь хочет найти информацию в интернете.",
            inputSchema = InputSchema(
                type = "object",
                properties = mapOf(
                    "query" to Property(
                        type = "string",
                        description = "Поисковый запрос"
                    ),
                    "max_results" to Property(
                        type = "number",
                        description = "Максимальное количество результатов (по умолчанию 3)"
                    )
                ),
                required = listOf("query")
            )
        ),
        Tool(
            name = "run_pipeline",
            description = "Запустить автоматический пайплайн: поиск статей в интернете → суммаризация → сохранение в файл. Используй когда нужно найти, обработать и сохранить информацию.",
            inputSchema = InputSchema(
                type = "object",
                properties = mapOf(
                    "search_query" to Property(
                        type = "string",
                        description = "Запрос для поиска статей в интернете"
                    ),
                    "summary_prompt" to Property(
                        type = "string",
                        description = "Промпт для суммаризации (опционально)"
                    ),
                    "filename" to Property(
                        type = "string",
                        description = "Имя файла для сохранения результата (опционально)"
                    )
                ),
                required = listOf("search_query")
            )
        ),
        Tool(
            name = "list_files",
            description = "Получить список всех сохранённых файлов результатов пайплайна. Используй когда пользователь спрашивает о сохранённых файлах.",
            inputSchema = InputSchema(
                type = "object",
                properties = mapOf(),
                required = emptyList()
            )
        )
    )
    
    // Выполнение инструментов
    fun executeToolCall(toolName: String, input: Map<String, Any>): String {
        return when (toolName) {
            "get_weather" -> executeGetWeather(input)
            "calculator" -> executeCalculator(input)
            "get_time" -> executeGetTime(input)
            else -> "Инструмент '$toolName' не найден"
        }
    }
    
    private fun executeGetWeather(input: Map<String, Any>): String {
        val city = input["city"] as? String ?: "Unknown"
        val units = input["units"] as? String ?: "celsius"
        val temp = (15..30).random()
        val conditions = listOf("☀️ ясно", "⛅ облачно", "🌧️ дождь", "❄️ снег").random()
        val unitSymbol = if (units == "celsius") "°C" else "°F"
        return "Погода в городе $city: $temp$unitSymbol, $conditions"
    }
    
    private fun executeCalculator(input: Map<String, Any>): String {
        val expression = input["expression"] as? String ?: "0"
        return try {
            val result = evaluateExpression(expression)
            "Результат: $result"
        } catch (e: Exception) {
            "Ошибка вычисления: ${e.message}"
        }
    }
    
    private fun executeGetTime(input: Map<String, Any>): String {
        val timezone = input["timezone"] as? String ?: "Europe/Moscow"
        val currentTime = java.text.SimpleDateFormat("HH:mm:ss dd.MM.yyyy", java.util.Locale.getDefault())
            .format(java.util.Date())
        return "Текущее время ($timezone): $currentTime"
    }
    
    private fun evaluateExpression(expression: String): Double {
        val cleanExpression = expression.replace(" ", "")
        return when {
            "+" in cleanExpression -> {
                val parts = cleanExpression.split("+")
                parts[0].toDouble() + parts[1].toDouble()
            }
            "-" in cleanExpression && !cleanExpression.startsWith("-") -> {
                val parts = cleanExpression.split("-")
                parts[0].toDouble() - parts[1].toDouble()
            }
            "*" in cleanExpression -> {
                val parts = cleanExpression.split("*")
                parts[0].toDouble() * parts[1].toDouble()
            }
            "/" in cleanExpression -> {
                val parts = cleanExpression.split("/")
                parts[0].toDouble() / parts[1].toDouble()
            }
            else -> cleanExpression.toDouble()
        }
    }
}


