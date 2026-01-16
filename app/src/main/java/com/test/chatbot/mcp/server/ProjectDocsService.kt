package com.test.chatbot.mcp.server

import android.content.Context
import android.util.Log
import com.test.chatbot.data.UserPreferences
import com.test.chatbot.rag.DocumentIndexService
import com.test.chatbot.rag.OllamaRAGService
import com.test.chatbot.model.RagConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Сервис для работы с документацией проекта через RAG (из GitHub)
 */
class ProjectDocsService(
    private val context: Context,
    private val documentIndexService: DocumentIndexService,
    private val ollamaRAGService: OllamaRAGService
) {
    private val TAG = "ProjectDocsService"
    private val userPreferences = UserPreferences(context)
    
    // Функция для вызова Python MCP (будет установлена из McpServer)
    var pythonMcpCall: (suspend (toolName: String, args: Map<String, Any>) -> Map<String, Any>)? = null
    
    /**
     * Индексировать документацию проекта (Kotlin файлы из GitHub)
     */
    suspend fun indexProjectDocs(): String = withContext(Dispatchers.IO) {
        try {
            var indexed = 0
            var skipped = 0
            val errors = mutableListOf<String>()
            
            val githubRepoUrl = userPreferences.githubRepoUrl
            val githubBranch = userPreferences.githubBranch
            
            Log.i(TAG, "════════════════════════════════")
            Log.i(TAG, "🔍 ИНДЕКСАЦИЯ ПРОЕКТА")
            Log.i(TAG, "════════════════════════════════")
            
            if (pythonMcpCall == null) {
                val errorMsg = buildString {
                    append("❌ Python MCP сервер недоступен!\n\n")
                    append("💡 Запустите Python MCP сервер:\n")
                    append("   cd mcp-server && python server.py")
                }
                Log.e(TAG, errorMsg)
                return@withContext errorMsg
            }
            
            // Сначала пробуем локальное сканирование
            Log.i(TAG, "📂 Попытка локального сканирования...")
            var listResponse: Map<String, Any>?
            var useLocal = false
            
            try {
                listResponse = pythonMcpCall!!("local_list_kotlin_files", mapOf(
                    "project_path" to "/Users/igorurev/FlutterProjects/ChatBot"
                ))
                useLocal = true
                Log.i(TAG, "✅ Используем локальное сканирование")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Локальное сканирование не удалось, пробуем GitHub...")
                Log.i(TAG, "📦 Репозиторий: $githubRepoUrl")
                Log.i(TAG, "🌿 Ветка: $githubBranch")
                
                // Fallback к GitHub API
                listResponse = pythonMcpCall!!("github_list_kotlin_files", mapOf(
                    "repo_url" to githubRepoUrl,
                    "branch" to githubBranch
                ))
                useLocal = false
            }
            
            // Получаем текст из ответа
            val listContent = listResponse["content"] as? List<*>
            val listText = (listContent?.firstOrNull() as? Map<*, *>)?.get("text") as? String ?: ""
            
            // Парсим список файлов из ответа
            val ktFiles = try {
                // Попробуем получить список файлов из поля "files"
                val filesFromResponse = listResponse["files"] as? List<*>
                if (filesFromResponse != null) {
                    filesFromResponse.mapNotNull { it as? String }
                } else {
                    // Fallback: парсим из текста
                    val lines = listText.lines().filter { it.trim().matches(Regex("""^\d+\.\s+.+\.kt$""")) }
                    lines.map { it.substringAfter(". ").trim() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка парсинга списка файлов", e)
                emptyList()
            }
            
            if (ktFiles.isEmpty()) {
                val errorMsg = "❌ Kotlin файлы не найдены в репозитории $githubRepoUrl"
                Log.w(TAG, errorMsg)
                return@withContext errorMsg
            }
            
            Log.i(TAG, "✅ Найдено ${ktFiles.size} Kotlin файлов")
            Log.i(TAG, "📥 Загрузка и индексация файлов...")
            
            // Шаг 2: Загружаем и индексируем каждый файл
            val projectPath = "/Users/igorurev/FlutterProjects/ChatBot"
            ktFiles.forEachIndexed { index, filePath ->
                try {
                    val fileResponse = if (useLocal) {
                        // Локальное чтение файла
                        val fullPath = "$projectPath/$filePath"
                        pythonMcpCall!!("local_get_file_content", mapOf(
                            "file_path" to fullPath
                        ))
                    } else {
                        // Чтение через GitHub API
                        pythonMcpCall!!("github_get_file_content", mapOf(
                            "repo_url" to githubRepoUrl,
                            "file_path" to filePath,
                            "branch" to githubBranch
                        ))
                    }
                    
                    // Получаем содержимое файла
                    val fileContent = fileResponse["file_content"] as? String
                    val actualContent = if (fileContent != null) {
                        fileContent
                    } else {
                        // Fallback: парсим из text ответа
                        val fileText = (fileResponse["content"] as? List<*>)
                            ?.firstOrNull()
                            ?.let { (it as? Map<*, *>)?.get("text") as? String } ?: ""
                        
                        // Извлекаем реальное содержимое (между разделителями)
                        val separator = "=".repeat(50)
                        if (fileText.contains(separator)) {
                            fileText.substringAfter(separator).substringBefore(separator).trim()
                        } else {
                            fileText
                        }
                    }
                    
                    // Пропускаем слишком большие файлы
                    if (actualContent.length > 100_000) {
                        skipped++
                        Log.d(TAG, "⏭️ Пропущен (слишком большой): $filePath")
                        return@forEachIndexed
                    }
                    
                    val docName = "project_${filePath.replace("/", "_").replace("\\", "_")}"
                    
                    documentIndexService.indexDocument(
                        name = docName,
                        content = actualContent,
                        type = "kotlin"
                    )
                    indexed++
                    
                    if (indexed % 10 == 0) {
                        Log.i(TAG, "✅ Проиндексировано: $indexed/${ktFiles.size}")
                    }
                } catch (e: Exception) {
                    errors.add("Error indexing $filePath: ${e.message}")
                    Log.e(TAG, "❌ Ошибка индексации $filePath", e)
                }
            }
            
            Log.i(TAG, "")
            Log.i(TAG, "════════════════════════════════")
            Log.i(TAG, "✅ ИНДЕКСАЦИЯ ЗАВЕРШЕНА")
            Log.i(TAG, "════════════════════════════════")
            Log.i(TAG, "📚 Kotlin файлов: $indexed")
            if (skipped > 0) {
                Log.i(TAG, "⏭️ Пропущено: $skipped")
            }
            if (errors.isNotEmpty()) {
                Log.w(TAG, "⚠️ Ошибок: ${errors.size}")
                errors.take(3).forEach { Log.w(TAG, "  • $it") }
            }
            Log.i(TAG, "════════════════════════════════")
            
            buildString {
                append("✅ Индексация завершена!\n\n")
                append("📚 Проиндексировано Kotlin файлов: $indexed\n")
                if (skipped > 0) {
                    append("⏭️ Пропущено (слишком большие): $skipped\n")
                }
                if (errors.isNotEmpty()) {
                    append("\n⚠️ Ошибки (${errors.size}):\n")
                    errors.take(3).forEach { append("  • $it\n") }
                }
                if (indexed > 0) {
                    append("\n💡 Готово к сканированию проекта!")
                } else {
                    append("\n❌ ОШИБКА: Не найдено .kt файлов!")
                    append("\n💡 Пересоберите приложение: ./gradlew clean assembleDebug")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during indexing", e)
            "❌ Ошибка индексации: ${e.message}"
        }
    }
    
    /**
     * Поиск по документации проекта
     */
    suspend fun searchProjectDocs(query: String): String = withContext(Dispatchers.IO) {
        try {
            val result = ollamaRAGService.queryWithRAGFiltered(
                question = query,
                config = RagConfig.precise()
            )
            
            if (result.isSuccess) {
                result.getOrNull()!!.toFormattedString()
            } else {
                "❌ Ошибка: ${result.exceptionOrNull()?.message}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error searching docs", e)
            "❌ Ошибка поиска: ${e.message}"
        }
    }
    
    /**
     * Получить помощь по команде/концепции
     */
    suspend fun getHelp(topic: String): String = withContext(Dispatchers.IO) {
        try {
            // Формируем специализированный запрос
            val query = when {
                topic.contains("git", ignoreCase = true) -> 
                    "Как работает Git интеграция в проекте?"
                topic.contains("rag", ignoreCase = true) || topic.contains("ollama", ignoreCase = true) -> 
                    "Как работает RAG система в проекте? Какие есть команды?"
                topic.contains("mcp", ignoreCase = true) -> 
                    "Что такое MCP сервер в проекте? Как он работает?"
                topic.contains("task", ignoreCase = true) || topic.contains("todoist", ignoreCase = true) -> 
                    "Как работает интеграция с Todoist в проекте?"
                topic.contains("compose", ignoreCase = true) || topic.contains("ui", ignoreCase = true) -> 
                    "Как устроен UI в проекте? Jetpack Compose?"
                topic.contains("viewmodel", ignoreCase = true) -> 
                    "Как работает ChatViewModel в проекте?"
                else -> topic
            }
            
            val result = ollamaRAGService.queryWithRAGFiltered(
                question = query,
                config = RagConfig.balanced()
            )
            
            if (result.isSuccess) {
                buildString {
                    append("🔍 Помощь по теме: **$topic**\n")
                    append("━━━━━━━━━━━━━━━━━━━━\n\n")
                    append(result.getOrNull()!!.toFormattedString())
                }
            } else {
                "❌ Ошибка: ${result.exceptionOrNull()?.message}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting help", e)
            "❌ Ошибка: ${e.message}\n\n💡 Попробуйте сначала проиндексировать проект командой: /project index"
        }
    }
    
    /**
     * Получить примеры кода
     */
    suspend fun getCodeExamples(topic: String): String = withContext(Dispatchers.IO) {
        try {
            val query = "Покажи примеры кода для: $topic"
            val result = ollamaRAGService.queryWithRAGFiltered(
                question = query,
                config = RagConfig.fast()
            )
            
            if (result.isSuccess) {
                result.getOrNull()!!.toFormattedString()
            } else {
                "❌ Ошибка: ${result.exceptionOrNull()?.message}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting examples", e)
            "❌ Ошибка: ${e.message}"
        }
    }
    
    /**
     * Получить статистику индексации
     */
    suspend fun getIndexStats(): String = withContext(Dispatchers.IO) {
        try {
            val result = documentIndexService.getStats()
            if (result.isSuccess) {
                val stats = result.getOrNull()!!
                
                // Подсчитываем Kotlin файлы среди проиндексированных документов
                val kotlinFilesCount = stats.documentCount // Предполагаем что большинство - Kotlin файлы
                
                buildString {
                    append("📊 Статистика индексации проекта\n")
                    append("━━━━━━━━━━━━━━━━━━━━\n\n")
                    append("📚 Документов: ${stats.documentCount}\n")
                    append("📄 Kotlin файлов: ~$kotlinFilesCount\n")
                    append("📝 Чанков: ${stats.chunkCount}\n")
                    append("🔢 Эмбеддингов: ${stats.embeddingCount}\n\n")
                    
                    if (stats.documentCount > 0) {
                        append("✅ Проект проиндексирован и готов к сканированию")
                    } else {
                        append("⚠️ Проект не проиндексирован")
                    }
                }
            } else {
                "❌ Ошибка: ${result.exceptionOrNull()?.message}"
            }
        } catch (e: Exception) {
            "❌ Ошибка: ${e.message}"
        }
    }
}
