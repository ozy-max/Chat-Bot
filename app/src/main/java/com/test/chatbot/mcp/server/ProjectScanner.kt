package com.test.chatbot.mcp.server

import android.content.Context
import android.util.Log
import com.test.chatbot.rag.OllamaRAGService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Сканер проекта для выявления проблем и генерации задач
 * Использует RAG для умного анализа кода
 */
class ProjectScanner(
    private val context: Context,
    private val ragService: OllamaRAGService,
    private val projectDocsService: ProjectDocsService
) {
    companion object {
        private const val TAG = "ProjectScanner"
    }
    
    data class ProjectIssue(
        val title: String,
        val description: String,
        val priority: String, // "high", "medium", "low"
        val category: String, // "bug", "improvement", "refactor", "docs", "security"
        val file: String? = null,
        val line: Int? = null,
        val recommendation: String
    )
    
    /**
     * Сканировать проект и найти проблемы
     */
    suspend fun scanProject(scope: String = "all"): Result<List<ProjectIssue>> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "════════════════════════════════")
            Log.i(TAG, "🔍 СКАНИРОВАНИЕ ПРОЕКТА (RAG)")
            Log.i(TAG, "════════════════════════════════")
            
            // Проверяем, что документы проиндексированы
            val stats = projectDocsService.getIndexStats()
            Log.i(TAG, "📊 Проверка индексации:")
            Log.i(TAG, stats)
            
            // Проверяем что есть проиндексированные документы
            val hasDocuments = stats.contains("Документов:") && !stats.contains("Документов: 0")
            if (!hasDocuments) {
                Log.e(TAG, "❌ ОШИБКА: Проект не проиндексирован!")
                Log.e(TAG, "💡 Запустите индексацию командой /project index")
                return@withContext Result.failure(Exception("Проект не проиндексирован. Запустите /project index"))
            }
            
            Log.i(TAG, "✅ Проект проиндексирован, начинаем сканирование...")
            
            val issues = mutableListOf<ProjectIssue>()
            
            // RAG анализ с улучшенными запросами
            Log.i(TAG, "")
            Log.i(TAG, "🤖 ЗАПУСК RAG АНАЛИЗА...")
            val ragIssues = findIssuesWithRAG()
            Log.i(TAG, "  ✅ RAG нашёл: ${ragIssues.size} проблем")
            issues.addAll(ragIssues)
            
            if (issues.isEmpty()) {
                Log.w(TAG, "")
                Log.w(TAG, "⚠️ RAG не нашел проблем.")
                Log.w(TAG, "💡 Возможные причины:")
                Log.w(TAG, "   1. Проект действительно чистый")
                Log.w(TAG, "   2. Similarity threshold слишком высокий")
                Log.w(TAG, "   3. Ollama embeddings не понимают код")
            }
            
            // Дедупликация
            val finalIssues = deduplicateIssues(issues)
            
            // Сортируем по приоритету
            val sorted = finalIssues.sortedByDescending { 
                when (it.priority) {
                    "high" -> 3
                    "medium" -> 2
                    "low" -> 1
                    else -> 0
                }
            }
            
            Log.i(TAG, "✅ Сканирование завершено: найдено ${sorted.size} проблем")
            
            // Подробная статистика по категориям
            val byPriority = sorted.groupBy { it.priority }
            Log.i(TAG, "Статистика по приоритетам:")
            Log.i(TAG, "  🔴 HIGH: ${byPriority["high"]?.size ?: 0}")
            Log.i(TAG, "  🟡 MEDIUM: ${byPriority["medium"]?.size ?: 0}")
            Log.i(TAG, "  ⚪ LOW: ${byPriority["low"]?.size ?: 0}")
            
            val byCategory = sorted.groupBy { it.category }
            Log.i(TAG, "Статистика по категориям:")
            byCategory.forEach { (category, items) ->
                Log.i(TAG, "  $category: ${items.size}")
            }
            
            Result.success(sorted)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка сканирования: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Дедупликация - убираем полностью одинаковые задачи
     */
    private fun deduplicateIssues(issues: List<ProjectIssue>): List<ProjectIssue> {
        val seen = mutableSetOf<String>()
        return issues.filter { issue ->
            // Ключ для дедупликации: категория + файл + первые 50 символов описания
            val key = "${issue.category}|${issue.file}|${issue.description.take(50)}"
            seen.add(key) // add() возвращает true если элемента не было
        }
    }
    
    /**
     * Проверка, является ли файл демо-документацией
     */
    private fun isDemoFile(fileName: String): Boolean {
        return fileName.contains("android_development") ||
               fileName.contains("demo_") ||
               fileName.contains("example_") ||
               fileName.endsWith(".txt") ||
               fileName.endsWith(".md")
    }
    
    /**
     * RAG поиск проблем с улучшенными запросами
     */
    private suspend fun findIssuesWithRAG(): List<ProjectIssue> {
        Log.i(TAG, "════════════════════════════════")
        Log.i(TAG, "🤖 RAG АНАЛИЗ КОДА")
        Log.i(TAG, "════════════════════════════════")
        val issues = mutableListOf<ProjectIssue>()
        
        // ============================================
        // ЗАПРОС 1: Hardcoded UI тексты
        // ============================================
        val query1 = "Text( Button( TextField( stringResource getString"
        Log.i(TAG, "")
        Log.i(TAG, "📝 Запрос 1: Hardcoded строки")
        Log.i(TAG, "   Query: $query1")
        val result1 = ragService.queryWithRAG(query1, topK = 50)
        
        if (result1.isSuccess) {
            val sources = result1.getOrNull()?.sources ?: emptyList()
            Log.i(TAG, "   ✅ Получено источников: ${sources.size}")
            
            val filtered = sources
                .filter { it.docName.startsWith("project_") && it.docName.endsWith(".kt") }
                .filter { !isDemoFile(it.docName) }
                .filter { it.similarity >= 0.20f } // ПОНИЖЕН THRESHOLD до 20%
            
            Log.i(TAG, "   ✅ После фильтрации: ${filtered.size} (similarity >= 0.20, только .kt)")
            
            filtered.forEach { source ->
                // Ищем паттерн Text("...") без stringResource
                val lines = source.chunkText.lines()
                lines.forEachIndexed { index, line ->
                    if ((line.contains("Text(") || line.contains("Button(")) && 
                        line.contains("\"") && 
                        !line.contains("stringResource") &&
                        !line.contains("getString")) {
                        
                        try {
                            val quotedText = line.substringAfter("\"").substringBefore("\"")
                            if (quotedText.length > 2 && quotedText.any { it.isLetter() }) {
                                issues.add(ProjectIssue(
                                    title = "💬 Hardcoded UI текст",
                                    description = "Файл: ${source.docName}\nТекст: \"$quotedText\"\nКод: ${line.trim().take(80)}",
                                    priority = "medium",
                                    category = "hardcoded",
                                    file = source.docName,
                                    recommendation = "Вынести в strings.xml и использовать stringResource()"
                                ))
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "   ⚠️ Ошибка парсинга: ${e.message}")
                        }
                    }
                }
            }
            
            Log.i(TAG, "   ✅ Найдено hardcoded строк: ${issues.size}")
        } else {
            Log.e(TAG, "   ❌ RAG ошибка: ${result1.exceptionOrNull()?.message}")
        }
        
        // ============================================
        // ЗАПРОС 2: TODO/FIXME комментарии
        // ============================================
        val query2 = "TODO FIXME HACK comment комментарий"
        Log.i(TAG, "")
        Log.i(TAG, "📝 Запрос 2: TODO/FIXME")
        Log.i(TAG, "   Query: $query2")
        val result2 = ragService.queryWithRAG(query2, topK = 40)
        
        if (result2.isSuccess) {
            val sources = result2.getOrNull()?.sources ?: emptyList()
            Log.i(TAG, "   ✅ Получено источников: ${sources.size}")
            
            val filtered = sources
                .filter { it.docName.startsWith("project_") && it.docName.endsWith(".kt") }
                .filter { !isDemoFile(it.docName) }
                .filter { it.similarity >= 0.20f }
            
            Log.i(TAG, "   ✅ После фильтрации: ${filtered.size} (similarity >= 0.20)")
            
            filtered.forEach { source ->
                val lines = source.chunkText.lines()
                lines.forEachIndexed { index, line ->
                    if (line.contains("//") && (line.contains("TODO", ignoreCase = true) || 
                                                 line.contains("FIXME", ignoreCase = true) ||
                                                 line.contains("HACK", ignoreCase = true))) {
                        val priority = if (line.contains("FIXME", ignoreCase = true)) "high" else "medium"
                        val type = when {
                            line.contains("FIXME", ignoreCase = true) -> "FIXME"
                            line.contains("HACK", ignoreCase = true) -> "HACK"
                            else -> "TODO"
                        }
                        issues.add(ProjectIssue(
                            title = "📝 $type комментарий",
                            description = "Файл: ${source.docName}\nКомментарий: ${line.trim().take(100)}",
                            priority = priority,
                            category = "improvement",
                            file = source.docName,
                            recommendation = "Реализовать функциональность или удалить комментарий"
                        ))
                    }
                }
            }
            
            Log.i(TAG, "   ✅ Найдено TODO/FIXME: ${issues.count { it.category == "improvement" }}")
        } else {
            Log.e(TAG, "   ❌ RAG ошибка: ${result2.exceptionOrNull()?.message}")
        }
        
        // ============================================
        // ЗАПРОС 3: Deprecated API
        // ============================================
        val query3 = "@Deprecated Deprecated deprecated устаревший"
        Log.i(TAG, "")
        Log.i(TAG, "📝 Запрос 3: Deprecated API")
        Log.i(TAG, "   Query: $query3")
        val result3 = ragService.queryWithRAG(query3, topK = 30)
        
        if (result3.isSuccess) {
            val sources = result3.getOrNull()?.sources ?: emptyList()
            Log.i(TAG, "   ✅ Получено источников: ${sources.size}")
            
            val filtered = sources
                .filter { it.docName.startsWith("project_") && it.docName.endsWith(".kt") }
                .filter { !isDemoFile(it.docName) }
                .filter { it.similarity >= 0.20f }
            
            Log.i(TAG, "   ✅ После фильтрации: ${filtered.size} (similarity >= 0.20)")
            
            filtered.forEach { source ->
                if (source.chunkText.contains("@Deprecated") || 
                    source.chunkText.contains("deprecated", ignoreCase = true)) {
                    issues.add(ProjectIssue(
                        title = "♻️ Deprecated API",
                        description = "Файл: ${source.docName}\nКод: ${source.chunkText.take(120)}...",
                        priority = "medium",
                        category = "refactor",
                        file = source.docName,
                        recommendation = "Обновить на актуальные API согласно документации"
                    ))
                }
            }
            
            Log.i(TAG, "   ✅ Найдено deprecated: ${issues.count { it.category == "refactor" }}")
        } else {
            Log.e(TAG, "   ❌ RAG ошибка: ${result3.exceptionOrNull()?.message}")
        }
        
        // ============================================
        // ИТОГИ RAG АНАЛИЗА
        // ============================================
        Log.i(TAG, "")
        Log.i(TAG, "════════════════════════════════")
        Log.i(TAG, "✅ RAG АНАЛИЗ ЗАВЕРШЁН")
        Log.i(TAG, "════════════════════════════════")
        Log.i(TAG, "💬 Hardcoded строк: ${issues.count { it.category == "hardcoded" }}")
        Log.i(TAG, "📝 TODO/FIXME: ${issues.count { it.category == "improvement" }}")
        Log.i(TAG, "♻️ Deprecated: ${issues.count { it.category == "refactor" }}")
        Log.i(TAG, "📊 ВСЕГО проблем: ${issues.size}")
        Log.i(TAG, "════════════════════════════════")
        
        return issues
    }
    
    /**
     * ============================================================
     * СТАРЫЕ RAG ФУНКЦИИ (не используются, оставлены для справки)
     * ============================================================
     */
    
    /**
     * Найти deprecated код через RAG (СТАРАЯ ВЕРСИЯ - не используется)
     */
    @Deprecated("Используется findIssuesWithRAG с упрощенными запросами")
    private suspend fun findDeprecatedCode(): List<ProjectIssue> {
        val query = "Найди все @Deprecated аннотации и использования deprecated методов в Kotlin файлах проекта (.kt). Покажи конкретный код с deprecated API."
        
        Log.d(TAG, "  RAG query: $query")
        val result = ragService.queryWithRAG(query, topK = 20)
        
        if (result.isFailure) {
            Log.w(TAG, "  ❌ RAG failed: ${result.exceptionOrNull()?.message}")
            return emptyList()
        }
        
        val ragResponse = result.getOrNull() ?: return emptyList()
        Log.d(TAG, "  📊 RAG вернул ${ragResponse.sources.size} источников")
        
        val sources = ragResponse.sources
            .filter { it.similarity >= 0.60f } // Понизили для большего охвата
            .filter { !isDemoFile(it.docName) } // Фильтруем демо-файлы
        
        Log.d(TAG, "  ✓ После фильтрации: ${sources.size} источников (similarity >= 0.60)")
        
        return sources.mapNotNull { source ->
            // Проверяем упоминается ли deprecated
            if (source.chunkText.contains("deprecated", ignoreCase = true) ||
                source.chunkText.contains("@Deprecated", ignoreCase = false)) {
                
                ProjectIssue(
                    title = "Обновить deprecated код в ${source.docName}",
                    description = "Найдено использование deprecated API:\n${source.chunkText.take(200)}...",
                    priority = "medium",
                    category = "refactor",
                    file = source.docName,
                    recommendation = "Заменить на актуальную альтернативу согласно документации"
                )
            } else null
        }
    }
    
    /**
     * Найти TODO/FIXME комментарии
     */
    private suspend fun findTodoComments(): List<ProjectIssue> {
        val query = "Найди все // TODO, // FIXME, // HACK комментарии в Kotlin файлах (.kt). Покажи конкретный код с этими комментариями."
        
        val result = ragService.queryWithRAG(query, topK = 25)
        
        if (result.isFailure) return emptyList()
        
        val ragResponse = result.getOrNull() ?: return emptyList()
        val sources = ragResponse.sources
            .filter { it.similarity >= 0.55f } // Понизили для большего охвата
            .filter { !isDemoFile(it.docName) }
            .filter { it.docName.endsWith(".kt") } // Только Kotlin файлы
        
        Log.d(TAG, "TODO/FIXME: найдено ${sources.size} потенциальных источников (similarity >= 0.55)")
        
        return sources.mapNotNull { source ->
            val text = source.chunkText
            val hasTodo = text.contains("TODO", ignoreCase = false)
            val hasFixme = text.contains("FIXME", ignoreCase = false)
            val hasHack = text.contains("HACK", ignoreCase = false)
            
            if (hasTodo || hasFixme || hasHack) {
                val priority = when {
                    hasFixme || hasHack -> "high"
                    hasTodo -> "medium"
                    else -> "low"
                }
                
                val type = when {
                    hasFixme -> "FIXME"
                    hasHack -> "HACK"
                    else -> "TODO"
                }
                
                ProjectIssue(
                    title = "Реализовать $type в ${source.docName}",
                    description = "Комментарий требует внимания:\n${text.take(200)}",
                    priority = priority,
                    category = if (hasFixme) "bug" else "improvement",
                    file = source.docName,
                    recommendation = "Реализовать или удалить комментарий"
                )
            } else null
        }
    }
    
    /**
     * Найти отсутствующую документацию (ОТКЛЮЧЕНО - генерирует много низкоприоритетных задач)
     */
    private suspend fun findMissingDocumentation(): List<ProjectIssue> {
        // Отключено, т.к. документация - низкий приоритет и генерирует шум
        Log.d(TAG, "Missing docs: пропущено (low priority)")
        return emptyList()
    }
    
    /**
     * Найти code smells (большие функции, дублирование)
     */
    private suspend fun findCodeSmells(): List<ProjectIssue> {
        val query = "Найди очень длинные функции (более 100 строк), сложную вложенность, повторяющийся код в Kotlin файлах (.kt)"
        
        val result = ragService.queryWithRAG(query, topK = 20)
        
        if (result.isFailure) return emptyList()
        
        val ragResponse = result.getOrNull() ?: return emptyList()
        val sources = ragResponse.sources
            .filter { it.similarity >= 0.45f } // Понизили для большего охвата
            .filter { !isDemoFile(it.docName) }
            .filter { it.docName.endsWith(".kt") }
        
        Log.d(TAG, "Code smells: найдено ${sources.size} потенциальных источников (similarity >= 0.45)")
        
        // Простая эвристика: длинные блоки кода
        return sources.take(5).mapNotNull { source ->
            val lines = source.chunkText.lines().size
            
            if (lines > 50) {
                ProjectIssue(
                    title = "Рефакторинг большой функции в ${source.docName}",
                    description = "Функция содержит $lines строк. Рекомендуется разделить на меньшие части для улучшения читаемости.",
                    priority = "medium",
                    category = "refactor",
                    file = source.docName,
                    recommendation = "Разделить на меньшие функции, каждая решает одну задачу"
                )
            } else null
        }
    }
    
    /**
     * Найти проблемы безопасности
     */
    private suspend fun findSecurityIssues(): List<ProjectIssue> {
        val query = "Найди hardcoded пароли, токены, API ключи в строковых литералах Kotlin кода (.kt). Покажи val password = \"...\", val token = \"sk-...\""
        
        val result = ragService.queryWithRAG(query, topK = 20)
        
        if (result.isFailure) return emptyList()
        
        val ragResponse = result.getOrNull() ?: return emptyList()
        val sources = ragResponse.sources
            .filter { it.similarity >= 0.55f } // Понизили для большего охвата
            .filter { !isDemoFile(it.docName) }
            .filter { it.docName.endsWith(".kt") }
        
        Log.d(TAG, "Security: найдено ${sources.size} потенциальных источников (similarity >= 0.55)")
        
        return sources.mapNotNull { source ->
            val text = source.chunkText.lowercase()
            
            val hasPassword = text.contains("password") && text.contains("=") && text.contains("\"")
            val hasToken = (text.contains("token") || text.contains("api_key") || text.contains("apikey")) && 
                           text.contains("=") && text.contains("\"")
            val hasHardcodedSecret = text.contains("secret") && text.contains("=") && text.contains("\"")
            
            if (hasPassword || hasToken || hasHardcodedSecret) {
                val type = when {
                    hasPassword -> "пароль"
                    hasToken -> "токен/API ключ"
                    hasHardcodedSecret -> "secret"
                    else -> "чувствительные данные"
                }
                
                ProjectIssue(
                    title = "⚠️ Проверить безопасность ($type) в ${source.docName}",
                    description = "Возможно хранение чувствительных данных в коде:\n${source.chunkText.take(150)}",
                    priority = "high",
                    category = "security",
                    file = source.docName,
                    recommendation = "Использовать secure storage (DataStore/Keystore) для чувствительных данных"
                )
            } else null
        }
    }
    
    /**
     * Найти проблемы производительности
     */
    private suspend fun findPerformanceIssues(): List<ProjectIssue> {
        val query = "Найди .execute() вызовы без withContext, блокирующие операции на главном потоке, циклы без async в Kotlin файлах (.kt)"
        
        val result = ragService.queryWithRAG(query, topK = 15)
        
        if (result.isFailure) return emptyList()
        
        val ragResponse = result.getOrNull() ?: return emptyList()
        val sources = ragResponse.sources
            .filter { it.similarity >= 0.50f } // Понизили для большего охвата
            .filter { !isDemoFile(it.docName) }
            .filter { it.docName.endsWith(".kt") }
        
        Log.d(TAG, "Performance: найдено ${sources.size} потенциальных источников (similarity >= 0.50)")
        
        return sources.take(3).mapNotNull { source ->
            val text = source.chunkText
            
            // Поиск блокирующих операций
            val hasBlockingCall = text.contains(".execute()", ignoreCase = false) && 
                                 !text.contains("withContext(Dispatchers.IO)")
            val hasRunBlocking = text.contains("runBlocking", ignoreCase = false) &&
                                !text.contains("// OK to block") // Комментарий-исключение
            
            if (hasBlockingCall || hasRunBlocking) {
                val type = if (hasRunBlocking) "runBlocking" else ".execute()"
                
                ProjectIssue(
                    title = "Оптимизировать блокирующий вызов ($type) в ${source.docName}",
                    description = "Обнаружен потенциально блокирующий вызов на главном потоке:\n${text.take(150)}",
                    priority = "medium",
                    category = "improvement",
                    file = source.docName,
                    recommendation = "Переместить в корутину с Dispatchers.IO или использовать suspend функции"
                )
            } else null
        }
    }
    
    /**
     * Найти hardcoded строки (UI текст не в strings.xml)
     */
    private suspend fun findHardcodedStrings(): List<ProjectIssue> {
        val query = "Text Button TextField Toast строки кавычки Compose UI"
        
        Log.d(TAG, "  RAG query: $query")
        val result = ragService.queryWithRAG(query, topK = 30)
        
        if (result.isFailure) {
            Log.w(TAG, "  ❌ RAG failed: ${result.exceptionOrNull()?.message}")
            return emptyList()
        }
        
        val ragResponse = result.getOrNull() ?: return emptyList()
        Log.d(TAG, "  📊 RAG вернул ${ragResponse.sources.size} источников")
        
        val sources = ragResponse.sources
            .filter { it.similarity >= 0.40f } // Ещё ниже threshold
            .filter { !isDemoFile(it.docName) }
            .filter { it.docName.endsWith(".kt") }
        
        Log.d(TAG, "  ✓ После фильтрации: ${sources.size} источников (similarity >= 0.40)")
        
        // Выводим первые 3 источника для отладки
        sources.take(3).forEach { source ->
            Log.d(TAG, "  📄 ${source.docName} (similarity: ${source.similarity})")
            Log.d(TAG, "     ${source.chunkText.take(100)}...")
        }
        
        return sources.mapNotNull { source ->
            val text = source.chunkText
            
            // Ищем паттерны hardcoded строк в UI коде
            val hasTextComposable = text.contains("Text(", ignoreCase = false) && 
                                    text.contains("\"") &&
                                    !text.contains("stringResource(")
            
            val hasTextView = text.contains(".text = \"", ignoreCase = false) ||
                             text.contains(".setText(\"", ignoreCase = false)
            
            val hasToast = text.contains("Toast.makeText", ignoreCase = false) &&
                          text.contains("\"")
            
            // Проверяем что это действительно пользовательский текст (не технический)
            val hasUserText = text.contains("\"") && (
                text.lowercase().let { t ->
                    t.contains("добавить") || t.contains("удалить") || 
                    t.contains("сохранить") || t.contains("отменить") ||
                    t.contains("ошибка") || t.contains("успешно") ||
                    t.contains("подтвердите") || t.contains("введите") ||
                    t.contains("button") || t.contains("title") ||
                    t.contains("message") || t.contains("label") ||
                    // Английский
                    t.contains("click") || t.contains("save") || 
                    t.contains("cancel") || t.contains("delete") ||
                    t.contains("error") || t.contains("success")
                }
            )
            
            if ((hasTextComposable || hasTextView || hasToast) && hasUserText) {
                // Извлекаем примеры строк для показа
                val stringExample = text
                    .split("\"")
                    .filter { it.length > 5 && it.any { c -> c.isLetter() } }
                    .firstOrNull()
                    ?.take(50) ?: "текст"
                
                ProjectIssue(
                    title = "Переместить hardcoded текст в strings.xml (${source.docName})",
                    description = "Найден hardcoded UI текст:\n\"$stringExample\"\n\nРекомендуется вынести в strings.xml для локализации.",
                    priority = "medium",
                    category = "improvement",
                    file = source.docName,
                    recommendation = "Создать строковый ресурс в res/values/strings.xml и использовать stringResource() или getString()"
                )
            } else null
        }.take(10) // Ограничиваем количество, чтобы не было слишком много
    }
    
    // ================================================================
    // УДАЛЕНА ФУНКЦИЯ findIssuesWithSimpleSearch()
    // Текстовый поиск не соответствует заданию (нужен только RAG)
    // ================================================================
}
