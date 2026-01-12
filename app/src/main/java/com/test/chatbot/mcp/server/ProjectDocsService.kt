package com.test.chatbot.mcp.server

import android.content.Context
import android.util.Log
import com.test.chatbot.rag.DocumentIndexService
import com.test.chatbot.rag.OllamaRAGService
import com.test.chatbot.model.RagConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Сервис для работы с документацией проекта через RAG
 */
class ProjectDocsService(
    private val context: Context,
    private val documentIndexService: DocumentIndexService,
    private val ollamaRAGService: OllamaRAGService
) {
    private val TAG = "ProjectDocsService"
    
    // Путь к проекту
    private val projectPath = "/Users/igorurev/FlutterProjects/ChatBot"
    
    /**
     * Индексировать документацию проекта
     */
    suspend fun indexProjectDocs(): String = withContext(Dispatchers.IO) {
        try {
            var indexed = 0
            val errors = mutableListOf<String>()
            
            // Список файлов для индексации
            val docsToIndex = listOf(
                "README.md",
                "app/src/main/java/com/test/chatbot/MainActivity.kt",
                "app/src/main/java/com/test/chatbot/presentation/ChatViewModel.kt",
                "app/src/main/java/com/test/chatbot/mcp/server/McpServer.kt",
                "app/build.gradle.kts",
                "gradle/libs.versions.toml"
            )
            
            for (filePath in docsToIndex) {
                try {
                    val file = File(projectPath, filePath)
                    if (file.exists() && file.isFile) {
                        val content = file.readText()
                        val docName = "project_${filePath.replace("/", "_")}"
                        
                        // Индексируем документ
                        documentIndexService.indexDocument(
                            name = docName,
                            content = content,
                            type = when {
                                filePath.endsWith(".md") -> "markdown"
                                filePath.endsWith(".kt") -> "kotlin"
                                filePath.endsWith(".toml") -> "toml"
                                filePath.endsWith(".kts") -> "gradle"
                                else -> "text"
                            }
                        )
                        indexed++
                        Log.d(TAG, "✅ Indexed: $filePath")
                    } else {
                        errors.add("Not found: $filePath")
                        Log.w(TAG, "⚠️ File not found: $filePath")
                    }
                } catch (e: Exception) {
                    errors.add("Error indexing $filePath: ${e.message}")
                    Log.e(TAG, "❌ Error indexing $filePath", e)
                }
            }
            
            buildString {
                append("✅ Индексация завершена!\n\n")
                append("📚 Проиндексировано файлов: $indexed\n")
                if (errors.isNotEmpty()) {
                    append("\n⚠️ Ошибки (${errors.size}):\n")
                    errors.take(5).forEach { append("  • $it\n") }
                }
                append("\n💡 Теперь используйте /help для вопросов о проекте!")
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
                buildString {
                    append("📊 Статистика документации проекта\n")
                    append("━━━━━━━━━━━━━━━━━━━━\n\n")
                    append("📚 Документов: ${stats.documentCount}\n")
                    append("📄 Чанков: ${stats.chunkCount}\n")
                    append("🔢 Эмбеддингов: ${stats.embeddingCount}\n")
                }
            } else {
                "❌ Ошибка: ${result.exceptionOrNull()?.message}"
            }
        } catch (e: Exception) {
            "❌ Ошибка: ${e.message}"
        }
    }
}
