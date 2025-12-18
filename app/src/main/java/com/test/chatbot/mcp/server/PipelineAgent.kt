package com.test.chatbot.mcp.server

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay

data class PipelineStep(
    val name: String,
    val description: String,
    val status: String,
    val result: String? = null,
    val error: String? = null
)

data class PipelineResult(
    val success: Boolean,
    val steps: List<PipelineStep>,
    val finalResult: String?,
    val summaryText: String? = null,
    val searchResults: List<SearchResult>? = null,
    val error: String? = null
)

class PipelineAgent(
    context: Context,
    private val todoistService: TodoistService,
    private val onStepComplete: ((PipelineStep) -> Unit)? = null
) {
    
    private val webSearchService = WebSearchService()
    private val fileStorageService = FileStorageService(context)
    
    companion object {
        private const val TAG = "PipelineAgent"
    }
    
    suspend fun runSearchSummarizeSavePipeline(
        searchQuery: String,
        summaryPrompt: String = "Создай краткую выжимку из найденных статей",
        filename: String? = null
    ): PipelineResult {
        val steps = mutableListOf<PipelineStep>()
        
        try {
            Log.i(TAG, "🚀 Запуск пайплайна: $searchQuery")
            
            steps.add(PipelineStep(
                name = "search_web",
                description = "Поиск статей в интернете",
                status = "running"
            ))
            onStepComplete?.invoke(steps.last())
            delay(500)
            
            val rawSearchResults = webSearchService.search(searchQuery, maxResults = 3)
            
            // Декодируем URL из DuckDuckGo редиректов
            val searchResults = rawSearchResults.map { result ->
                val cleanUrl = try {
                    if (result.url.contains("uddg=")) {
                        val encoded = result.url.substringAfter("uddg=").substringBefore("&")
                        java.net.URLDecoder.decode(encoded, "UTF-8")
                    } else {
                        result.url
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Не удалось декодировать URL: ${result.url}")
                    result.url
                }
                result.copy(url = cleanUrl)
            }
            
            if (searchResults.isEmpty()) {
                val failedStep = steps.last().copy(
                    status = "failed",
                    error = "Не удалось найти результаты"
                )
                steps[steps.lastIndex] = failedStep
                onStepComplete?.invoke(failedStep)
                
                return PipelineResult(
                    success = false,
                    steps = steps,
                    finalResult = null,
                    error = "Не удалось найти результаты"
                )
            }
            
            val searchResultText = webSearchService.formatResults(searchResults)
            
            val successStep = steps.last().copy(
                status = "completed",
                result = "Найдено ${searchResults.size} статей"
            )
            steps[steps.lastIndex] = successStep
            onStepComplete?.invoke(successStep)
            delay(500)
            
            steps.add(PipelineStep(
                name = "summarize_text",
                description = "Создание суммаризации",
                status = "running"
            ))
            onStepComplete?.invoke(steps.last())
            delay(500)
            
            val summaryText = createSummary(searchResults, summaryPrompt)
            
            val summaryStep = steps.last().copy(
                status = "completed",
                result = "Суммаризация создана (${summaryText.length} символов)"
            )
            steps[steps.lastIndex] = summaryStep
            onStepComplete?.invoke(summaryStep)
            delay(500)
            
            steps.add(PipelineStep(
                name = "save_to_file",
                description = "Сохранение результатов",
                status = "running"
            ))
            onStepComplete?.invoke(steps.last())
            delay(500)
            
            val fullContent = buildString {
                append("=" .repeat(50))
                append("\n")
                append("РЕЗУЛЬТАТЫ ПОИСКА И АНАЛИЗА")
                append("\n")
                append("=" .repeat(50))
                append("\n\n")
                append("📝 Запрос: $searchQuery\n")
                append("📅 Дата: ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}\n\n")
                
                append("=" .repeat(50))
                append("\n")
                append("1️⃣ НАЙДЕННЫЕ СТАТЬИ")
                append("\n")
                append("=" .repeat(50))
                append("\n\n")
                append(searchResultText)
                append("\n\n")
                
                append("=" .repeat(50))
                append("\n")
                append("2️⃣ СУММАРИЗАЦИЯ")
                append("\n")
                append("=" .repeat(50))
                append("\n\n")
                append(summaryText)
                append("\n\n")
                
                append("=" .repeat(50))
                append("\n")
                append("КОНЕЦ ОТЧЁТА")
                append("\n")
                append("=" .repeat(50))
            }
            
            Log.i(TAG, "Контент подготовлен, размер: ${fullContent.length} символов")
            Log.i(TAG, "Начинаем сохранение файла...")
            
            val saveResult = fileStorageService.saveToFile(fullContent, filename)
            
            Log.i(TAG, "Результат сохранения: ${if (saveResult.isSuccess) "УСПЕХ" else "ОШИБКА"}")
            if (saveResult.isSuccess) {
                Log.i(TAG, "Файл сохранён по пути: ${saveResult.getOrNull()}")
            } else {
                Log.e(TAG, "Ошибка сохранения: ${saveResult.exceptionOrNull()?.message}")
            }
            
            if (saveResult.isFailure) {
                val failedStep = steps.last().copy(
                    status = "failed",
                    error = saveResult.exceptionOrNull()?.message
                )
                steps[steps.lastIndex] = failedStep
                onStepComplete?.invoke(failedStep)
                
                return PipelineResult(
                    success = false,
                    steps = steps,
                    finalResult = null,
                    error = "Не удалось сохранить файл"
                )
            }
            
            val filePath = saveResult.getOrNull()
            
            // Проверяем что файл действительно существует
            val file = java.io.File(filePath ?: "")
            Log.i(TAG, "Проверка файла: ${file.absolutePath}")
            Log.i(TAG, "Файл существует: ${file.exists()}")
            Log.i(TAG, "Размер файла: ${file.length()} байт")
            
            val saveStep = steps.last().copy(
                status = "completed",
                result = "Файл сохранён: $filePath"
            )
            steps[steps.lastIndex] = saveStep
            onStepComplete?.invoke(saveStep)
            delay(500)
            
            // 4. Создание задачи в Todoist
            steps.add(PipelineStep(
                name = "create_todoist_task",
                description = "Создание задачи в Todoist",
                status = "running"
            ))
            onStepComplete?.invoke(steps.last())
            delay(500)
            
            val timestamp = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date())
            
            // Формируем название задачи с ключевыми словами из запроса
            val cleanQuery = searchQuery
                .replace(Regex("найди|найти|поищи|поиск|покажи|статьи?|информацию|данные", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+о\\s+|\\s+про\\s+|\\s+об\\s+", RegexOption.IGNORE_CASE), " ")
                .trim()
                .replaceFirstChar { it.lowercase() }
            
            val taskTitle = if (cleanQuery.isNotBlank()) {
                "Выжимка из статей о $cleanQuery $timestamp"
            } else {
                "Выжимка $timestamp"
            }
            
            // Формируем описание с выжимкой и ссылками
            val taskDescription = buildString {
                // Ограничиваем длину суммаризации (Todoist имеет лимит на описание)
                val maxSummaryLength = 1000
                if (summaryText.length > maxSummaryLength) {
                    append(summaryText.take(maxSummaryLength))
                    append("...\n\n[Полный текст в PDF файле]")
                } else {
                    append(summaryText)
                }
                
                append("\n\n")
                append("Источники:\n")
                searchResults.forEachIndexed { index, result ->
                    append("${index + 1}. ${result.title}\n")
                    append("${result.url}\n")
                }
            }
            
            Log.i(TAG, "Название задачи: $taskTitle")
            Log.i(TAG, "Длина описания: ${taskDescription.length} символов")
            
            val todoistResult = todoistService.createTask(taskTitle, taskDescription)
            
            if (todoistResult.isFailure) {
                val failedStep = steps.last().copy(
                    status = "failed",
                    error = todoistResult.exceptionOrNull()?.message
                )
                steps[steps.lastIndex] = failedStep
                onStepComplete?.invoke(failedStep)
                
                Log.w(TAG, "⚠️ Задача в Todoist не создана: ${todoistResult.exceptionOrNull()?.message}")
                // Не возвращаем ошибку, так как основная работа выполнена
            } else {
                val taskId = todoistResult.getOrNull()
                val todoistStep = steps.last().copy(
                    status = "completed",
                    result = "Задача создана в Todoist (ID: $taskId)"
                )
                steps[steps.lastIndex] = todoistStep
                onStepComplete?.invoke(todoistStep)
                
                Log.i(TAG, "✅ Задача в Todoist создана: $taskId")
            }
            
            Log.i(TAG, "✅ Пайплайн завершён успешно")
            
            return PipelineResult(
                success = true,
                steps = steps,
                finalResult = filePath,
                summaryText = summaryText,
                searchResults = searchResults
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка пайплайна: ${e.message}", e)
            
            if (steps.isNotEmpty() && steps.last().status == "running") {
                val failedStep = steps.last().copy(
                    status = "failed",
                    error = e.message
                )
                steps[steps.lastIndex] = failedStep
                onStepComplete?.invoke(failedStep)
            }
            
            return PipelineResult(
                success = false,
                steps = steps,
                finalResult = null,
                error = e.message
            )
        }
    }
    
    private fun createSummary(results: List<SearchResult>, prompt: String): String {
        return buildString {
            append("$prompt\n\n")
            
            append("📊 Анализ ${results.size} статей:\n\n")
            
            results.forEach { result ->
                append("${result.snippet}\n\n")
            }
            
            append("🔗 Источники:\n")
            results.forEachIndexed { index, result ->
                append("${index + 1}. ${result.url}\n")
            }
        }
    }
    
    fun getStorageDirectory(): String {
        return fileStorageService.getStorageDir()
    }
}

