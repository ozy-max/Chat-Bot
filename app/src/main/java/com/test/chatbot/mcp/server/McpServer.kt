package com.test.chatbot.mcp.server

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.*
import java.io.IOException

/**
 * Встроенный MCP (Model Context Protocol) сервер на Kotlin
 * Работает внутри Android приложения
 */
class McpServer(
    private val context: Context,
    private val port: Int = 3000
) : NanoHTTPD(port) {

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private var todoistToken: String = ""
    private var syncInterval: Int = 1 // минуты
    
    private lateinit var taskRepository: TaskRepository
    private lateinit var todoistService: TodoistService
    private lateinit var schedulerManager: SchedulerManager

    companion object {
        private const val TAG = "McpServer"
        const val DEFAULT_PORT = 3000
    }

    /**
     * Инициализация сервера
     */
    fun initialize() {
        try {
            taskRepository = TaskRepository(context)
            todoistService = TodoistService()
            schedulerManager = SchedulerManager(
                taskRepository = taskRepository,
                todoistService = todoistService,
                scope = scope
            )
            
            Log.i(TAG, "✅ MCP Server инициализирован на порту $port")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка инициализации: ${e.message}", e)
        }
    }

    /**
     * Запуск сервера
     */
    fun startServer() {
        try {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            Log.i(TAG, "🚀 MCP Server запущен на http://localhost:$port/mcp")
            
            // Запускаем планировщики
            schedulerManager.start(syncInterval)
        } catch (e: IOException) {
            Log.e(TAG, "❌ Не удалось запустить сервер: ${e.message}", e)
        }
    }

    /**
     * Остановка сервера
     */
    fun stopServer() {
        try {
            stop()
            schedulerManager.stop()
            scope.cancel()
            Log.i(TAG, "🛑 MCP Server остановлен")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка остановки сервера: ${e.message}", e)
        }
    }

    /**
     * Установить Todoist токен
     */
    fun setTodoistToken(token: String) {
        this.todoistToken = token
        todoistService.setToken(token)
        Log.i(TAG, "✅ Todoist токен обновлён")
    }
    
    /**
     * Установить callback для получения summary
     */
    fun setSummaryCallback(callback: (String) -> Unit) {
        schedulerManager.setOnSummaryGenerated(callback)
        Log.i(TAG, "📊 Summary callback установлен")
    }

    /**
     * Установить интервал синхронизации
     */
    fun setSyncInterval(minutes: Int) {
        if (minutes >= 1) {
            this.syncInterval = minutes
            schedulerManager.updateInterval(minutes)
            Log.i(TAG, "✅ Интервал синхронизации: $minutes минут")
        }
    }

    /**
     * Обработка HTTP запросов
     */
    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        Log.d(TAG, "📨 $method $uri")

        return when {
            uri == "/mcp" && method == Method.POST -> handleMcpRequest(session)
            uri == "/set_todoist_token" && method == Method.POST -> handleSetTodoistToken(session)
            uri == "/set_interval" && method == Method.POST -> handleSetInterval(session)
            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "application/json",
                """{"error": "Not found"}"""
            )
        }
    }

    /**
     * Обработка MCP запроса (JSON-RPC)
     */
    private fun handleMcpRequest(session: IHTTPSession): Response {
        val bodyMap = mutableMapOf<String, String>()
        try {
            session.parseBody(bodyMap)
            val postData = bodyMap["postData"] ?: return errorResponse("No data")
            
            val request = gson.fromJson(postData, JsonObject::class.java)
            val method = request.get("method")?.asString
            val id = request.get("id")?.asInt ?: 1

            val result = when (method) {
                "initialize" -> handleInitialize()
                "tools/list" -> handleToolsList()
                "tools/call" -> handleToolCall(request)
                else -> mapOf("error" to "Unknown method: $method")
            }

            val response = mapOf(
                "jsonrpc" to "2.0",
                "id" to id,
                "result" to result
            )

            return jsonResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка обработки MCP: ${e.message}", e)
            return errorResponse(e.message ?: "Internal error")
        }
    }

    /**
     * Обработка установки Todoist токена
     */
    private fun handleSetTodoistToken(session: IHTTPSession): Response {
        val bodyMap = mutableMapOf<String, String>()
        try {
            session.parseBody(bodyMap)
            val postData = bodyMap["postData"] ?: return errorResponse("No data")
            
            val json = gson.fromJson(postData, JsonObject::class.java)
            val token = json.get("token")?.asString ?: ""
            
            setTodoistToken(token)
            
            return jsonResponse(mapOf("status" to "success"))
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка установки токена: ${e.message}", e)
            return errorResponse(e.message ?: "Internal error")
        }
    }

    /**
     * Обработка установки интервала
     */
    private fun handleSetInterval(session: IHTTPSession): Response {
        val bodyMap = mutableMapOf<String, String>()
        try {
            session.parseBody(bodyMap)
            val postData = bodyMap["postData"] ?: return errorResponse("No data")
            
            val json = gson.fromJson(postData, JsonObject::class.java)
            val minutes = json.get("interval_minutes")?.asInt ?: 30
            
            setSyncInterval(minutes)
            
            return jsonResponse(mapOf(
                "status" to "success",
                "interval_minutes" to minutes
            ))
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка установки интервала: ${e.message}", e)
            return errorResponse(e.message ?: "Internal error")
        }
    }

    /**
     * Инициализация MCP
     */
    private fun handleInitialize(): Map<String, Any> {
        return mapOf(
            "protocolVersion" to "2024-11-05",
            "capabilities" to mapOf(
                "tools" to mapOf("listChanged" to false)
            ),
            "serverInfo" to mapOf(
                "name" to "MCP Kotlin Server",
                "version" to "1.0.0"
            )
        )
    }

    /**
     * Список инструментов
     */
    private fun handleToolsList(): Map<String, Any> {
        return mapOf(
            "tools" to listOf(
                mapOf(
                    "name" to "sync_todoist",
                    "description" to "Синхронизировать задачи с Todoist",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf<String, Any>(),
                        "required" to emptyList<String>()
                    )
                ),
                mapOf(
                    "name" to "list_tasks",
                    "description" to "Получить список задач",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf<String, Any>(),
                        "required" to emptyList<String>()
                    )
                ),
                mapOf(
                    "name" to "get_summary",
                    "description" to "Получить сводку задач за сегодня",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf<String, Any>(),
                        "required" to emptyList<String>()
                    )
                )
            )
        )
    }

    /**
     * Вызов инструмента
     */
    private fun handleToolCall(request: JsonObject): Map<String, Any> {
        val params = request.getAsJsonObject("params")
        val name = params?.get("name")?.asString ?: ""
        
        return when (name) {
            "sync_todoist" -> runBlocking { syncTodoist() }
            "list_tasks" -> runBlocking { listTasks() }
            "get_summary" -> runBlocking { getSummary() }
            else -> mapOf(
                "content" to listOf(
                    mapOf("type" to "text", "text" to "Unknown tool: $name")
                )
            )
        }
    }

    /**
     * Синхронизация с Todoist
     */
    private suspend fun syncTodoist(): Map<String, Any> {
        return try {
            val count = todoistService.syncTasks(taskRepository)
            mapOf(
                "content" to listOf(
                    mapOf(
                        "type" to "text",
                        "text" to "✅ Синхронизация завершена!\n\n📥 Синхронизировано задач с Todoist: $count"
                    )
                )
            )
        } catch (e: Exception) {
            mapOf(
                "content" to listOf(
                    mapOf("type" to "text", "text" to "❌ Ошибка: ${e.message}")
                )
            )
        }
    }

    /**
     * Список задач
     */
    private suspend fun listTasks(): Map<String, Any> {
        return try {
            val tasks = taskRepository.getAllTasks()
            val text = if (tasks.isEmpty()) {
                "📋 Нет задач"
            } else {
                buildString {
                    append("📋 Список задач (${tasks.size}):\n\n")
                    tasks.forEachIndexed { index, task ->
                        val status = if (task.completed) "✅" else "⏳"
                        append("$status #${task.id}: ${task.title}\n")
                        if (task.description.isNotBlank()) {
                            append("   ${task.description}\n")
                        }
                        if (index < tasks.size - 1) append("\n")
                    }
                }
            }
            mapOf(
                "content" to listOf(
                    mapOf("type" to "text", "text" to text)
                )
            )
        } catch (e: Exception) {
            mapOf(
                "content" to listOf(
                    mapOf("type" to "text", "text" to "❌ Ошибка: ${e.message}")
                )
            )
        }
    }

    /**
     * Сводка задач
     */
    private suspend fun getSummary(): Map<String, Any> {
        return try {
            val summary = taskRepository.getTodaySummary(todoistService)
            val text = buildString {
                append("📊 Сводка за сегодня\n\n")
                append("✅ Выполнено: ${summary.completedToday}\n")
                append("📝 Создано: ${summary.createdToday}\n")
                append("⏳ Осталось: ${summary.pendingCount}")
            }
            mapOf(
                "content" to listOf(
                    mapOf("type" to "text", "text" to text)
                )
            )
        } catch (e: Exception) {
            mapOf(
                "content" to listOf(
                    mapOf("type" to "text", "text" to "❌ Ошибка: ${e.message}")
                )
            )
        }
    }

    /**
     * JSON ответ
     */
    private fun jsonResponse(data: Any): Response {
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            gson.toJson(data)
        )
    }

    /**
     * Ответ с ошибкой
     */
    private fun errorResponse(message: String): Response {
        return newFixedLengthResponse(
            Response.Status.INTERNAL_ERROR,
            "application/json",
            """{"error": "$message"}"""
        )
    }
}

