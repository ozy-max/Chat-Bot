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
    private lateinit var webSearchService: WebSearchService
    private lateinit var fileStorageService: FileStorageService
    private lateinit var pipelineAgent: PipelineAgent
    private lateinit var adbService: AdbService

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
            
            webSearchService = WebSearchService()
            fileStorageService = FileStorageService(context)
            adbService = AdbService(context)
            val chatRepository = com.test.chatbot.repository.ChatRepository()
            pipelineAgent = PipelineAgent(context, todoistService, chatRepository)
            
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
                ),
                mapOf(
                    "name" to "search_web",
                    "description" to "Поиск статей в интернете по запросу",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "query" to mapOf(
                                "type" to "string",
                                "description" to "Поисковый запрос"
                            ),
                            "max_results" to mapOf(
                                "type" to "number",
                                "description" to "Максимальное количество результатов (по умолчанию 3)"
                            )
                        ),
                        "required" to listOf("query")
                    )
                ),
                mapOf(
                    "name" to "save_to_file",
                    "description" to "Сохранить текст в файл",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "content" to mapOf(
                                "type" to "string",
                                "description" to "Содержимое файла"
                            ),
                            "filename" to mapOf(
                                "type" to "string",
                                "description" to "Имя файла (опционально)"
                            )
                        ),
                        "required" to listOf("content")
                    )
                ),
                mapOf(
                    "name" to "run_pipeline",
                    "description" to "Запустить автоматический пайплайн: поиск → суммаризация → сохранение",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "search_query" to mapOf(
                                "type" to "string",
                                "description" to "Запрос для поиска статей"
                            ),
                            "summary_prompt" to mapOf(
                                "type" to "string",
                                "description" to "Промпт для суммаризации (опционально)"
                            ),
                            "filename" to mapOf(
                                "type" to "string",
                                "description" to "Имя файла для сохранения (опционально)"
                            )
                        ),
                        "required" to listOf("search_query")
                    )
                ),
                mapOf(
                    "name" to "list_files",
                    "description" to "Получить список сохранённых файлов",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf<String, Any>(),
                        "required" to emptyList<String>()
                    )
                ),
                // ADB Tools
                mapOf(
                    "name" to "screenshot",
                    "description" to "Сделать скриншот экрана устройства",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf<String, Any>(),
                        "required" to emptyList<String>()
                    )
                ),
                mapOf(
                    "name" to "get_logs",
                    "description" to "Получить логи приложения",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "package_name" to mapOf(
                                "type" to "string",
                                "description" to "Имя пакета приложения (опционально, по умолчанию текущее)"
                            ),
                            "lines" to mapOf(
                                "type" to "number",
                                "description" to "Количество строк логов (по умолчанию 100)"
                            )
                        ),
                        "required" to emptyList<String>()
                    )
                ),
                mapOf(
                    "name" to "device_info",
                    "description" to "Получить информацию об устройстве (модель, Android версия, память)",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf<String, Any>(),
                        "required" to emptyList<String>()
                    )
                ),
                mapOf(
                    "name" to "start_app",
                    "description" to "Запустить приложение по имени пакета",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "package_name" to mapOf(
                                "type" to "string",
                                "description" to "Имя пакета приложения для запуска"
                            )
                        ),
                        "required" to listOf("package_name")
                    )
                ),
                mapOf(
                    "name" to "shell_command",
                    "description" to "Выполнить shell команду на устройстве",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "command" to mapOf(
                                "type" to "string",
                                "description" to "Shell команда для выполнения"
                            )
                        ),
                        "required" to listOf("command")
                    )
                ),
                mapOf(
                    "name" to "list_apps",
                    "description" to "Получить список установленных приложений",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "limit" to mapOf(
                                "type" to "number",
                                "description" to "Максимальное количество приложений (по умолчанию 20)"
                            )
                        ),
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
        val arguments = params?.getAsJsonObject("arguments")
        
        return when (name) {
            "sync_todoist" -> runBlocking { syncTodoist() }
            "list_tasks" -> runBlocking { listTasks() }
            "get_summary" -> runBlocking { getSummary() }
            "search_web" -> runBlocking { searchWeb(arguments) }
            "save_to_file" -> runBlocking { saveToFile(arguments) }
            "run_pipeline" -> runBlocking { runPipeline(arguments) }
            "list_files" -> runBlocking { listFiles() }
            // ADB Tools
            "screenshot" -> runBlocking { takeScreenshot() }
            "get_logs" -> runBlocking { getAppLogs(arguments) }
            "device_info" -> runBlocking { getDeviceInfo() }
            "start_app" -> runBlocking { startApp(arguments) }
            "shell_command" -> runBlocking { executeShellCommand(arguments) }
            "list_apps" -> runBlocking { listInstalledApps(arguments) }
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
     * Поиск в интернете
     */
    private suspend fun searchWeb(arguments: JsonObject?): Map<String, Any> {
        return try {
            val query = arguments?.get("query")?.asString ?: ""
            val maxResults = arguments?.get("max_results")?.asInt ?: 3
            
            if (query.isBlank()) {
                return mapOf(
                    "content" to listOf(
                        mapOf("type" to "text", "text" to "❌ Необходимо указать поисковый запрос")
                    )
                )
            }
            
            val results = webSearchService.search(query, maxResults)
            val text = webSearchService.formatResults(results)
            
            mapOf(
                "content" to listOf(
                    mapOf("type" to "text", "text" to text)
                )
            )
        } catch (e: Exception) {
            mapOf(
                "content" to listOf(
                    mapOf("type" to "text", "text" to "❌ Ошибка поиска: ${e.message}")
                )
            )
        }
    }

    /**
     * Сохранение в файл
     */
    private suspend fun saveToFile(arguments: JsonObject?): Map<String, Any> {
        return try {
            val content = arguments?.get("content")?.asString ?: ""
            val filename = arguments?.get("filename")?.asString
            
            if (content.isBlank()) {
                return mapOf(
                    "content" to listOf(
                        mapOf("type" to "text", "text" to "❌ Содержимое файла не может быть пустым")
                    )
                )
            }
            
            val result = fileStorageService.saveToFile(content, filename)
            
            val text = if (result.isSuccess) {
                "✅ Файл успешно сохранён:\n${result.getOrNull()}"
            } else {
                "❌ Ошибка сохранения: ${result.exceptionOrNull()?.message}"
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
     * Запуск автоматического пайплайна
     */
    private suspend fun runPipeline(arguments: JsonObject?): Map<String, Any> {
        return try {
            val searchQuery = arguments?.get("search_query")?.asString ?: ""
            val summaryPrompt = arguments?.get("summary_prompt")?.asString 
                ?: "Создай краткую выжимку из найденных статей"
            val filename = arguments?.get("filename")?.asString
            val apiKey = arguments?.get("api_key")?.asString ?: ""
            
            if (searchQuery.isBlank()) {
                return mapOf(
                    "content" to listOf(
                        mapOf("type" to "text", "text" to "❌ Необходимо указать поисковый запрос")
                    )
                )
            }
            
            val result = pipelineAgent.runSearchSummarizeSavePipeline(
                searchQuery = searchQuery,
                summaryPrompt = summaryPrompt,
                filename = filename,
                apiKey = apiKey
            )
            
            // Возвращаем результат в JSON формате
            val gson = com.google.gson.Gson()
            val jsonResult = gson.toJson(result)
            
            mapOf(
                "content" to listOf(
                    mapOf("type" to "text", "text" to jsonResult)
                )
            )
        } catch (e: Exception) {
            mapOf(
                "content" to listOf(
                    mapOf("type" to "text", "text" to "❌ Ошибка пайплайна: ${e.message}")
                )
            )
        }
    }

    /**
     * Список сохранённых файлов
     */
    private suspend fun listFiles(): Map<String, Any> {
        return try {
            val result = fileStorageService.listFiles()
            
            val text = if (result.isSuccess) {
                val files = result.getOrNull() ?: emptyList()
                if (files.isEmpty()) {
                    "📁 Нет сохранённых файлов"
                } else {
                    buildString {
                        append("📁 Сохранённые файлы (${files.size}):\n\n")
                        files.forEachIndexed { index, filename ->
                            append("${index + 1}. $filename\n")
                        }
                        append("\n📂 Директория: ${fileStorageService.getStorageDir()}")
                    }
                }
            } else {
                "❌ Ошибка: ${result.exceptionOrNull()?.message}"
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

    // ==================== ADB Tools ====================
    
    /**
     * Сделать скриншот экрана
     */
    private suspend fun takeScreenshot(): Map<String, Any> {
        return try {
            val result = adbService.takeScreenshot()
            
            val text = if (result.isSuccess) {
                val path = result.getOrNull()
                "✅ Скриншот успешно сохранён:\n$path"
            } else {
                "❌ Ошибка создания скриншота: ${result.exceptionOrNull()?.message}\n\n" +
                "💡 Для создания скриншотов могут потребоваться дополнительные разрешения или root права."
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
     * Получить логи приложения
     */
    private suspend fun getAppLogs(arguments: JsonObject?): Map<String, Any> {
        return try {
            val packageName = arguments?.get("package_name")?.asString
            val lines = arguments?.get("lines")?.asInt ?: 100
            
            val result = if (packageName.isNullOrBlank()) {
                adbService.getAppLogs(lines = lines)
            } else {
                adbService.getAppLogs(packageName, lines)
            }
            
            val text = if (result.isSuccess) {
                val logs = result.getOrNull() ?: "Логов не найдено"
                "📋 Логи приложения:\n\n$logs"
            } else {
                "❌ Ошибка получения логов: ${result.exceptionOrNull()?.message}"
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
     * Получить информацию об устройстве
     */
    private suspend fun getDeviceInfo(): Map<String, Any> {
        return try {
            val result = adbService.getDeviceInfo()
            
            val text = if (result.isSuccess) {
                result.getOrNull() ?: "Информация недоступна"
            } else {
                "❌ Ошибка получения информации: ${result.exceptionOrNull()?.message}"
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
     * Запустить приложение
     */
    private suspend fun startApp(arguments: JsonObject?): Map<String, Any> {
        return try {
            val packageName = arguments?.get("package_name")?.asString
            
            if (packageName.isNullOrBlank()) {
                return mapOf(
                    "content" to listOf(
                        mapOf("type" to "text", "text" to "❌ Необходимо указать имя пакета")
                    )
                )
            }
            
            val result = adbService.startApp(packageName)
            
            val text = if (result.isSuccess) {
                "✅ ${result.getOrNull()}"
            } else {
                "❌ Ошибка запуска приложения: ${result.exceptionOrNull()?.message}"
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
     * Выполнить shell команду
     */
    private suspend fun executeShellCommand(arguments: JsonObject?): Map<String, Any> {
        return try {
            val command = arguments?.get("command")?.asString
            
            if (command.isNullOrBlank()) {
                return mapOf(
                    "content" to listOf(
                        mapOf("type" to "text", "text" to "❌ Необходимо указать команду")
                    )
                )
            }
            
            val result = adbService.executeShellCommand(command)
            
            val text = if (result.isSuccess) {
                val output = result.getOrNull()
                if (output.isNullOrBlank()) {
                    "✅ Команда выполнена успешно (вывод пустой)"
                } else {
                    "✅ Результат выполнения:\n\n$output"
                }
            } else {
                "❌ Ошибка выполнения команды: ${result.exceptionOrNull()?.message}"
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
     * Получить список установленных приложений
     */
    private suspend fun listInstalledApps(arguments: JsonObject?): Map<String, Any> {
        return try {
            val limit = arguments?.get("limit")?.asInt ?: 20
            
            val result = adbService.getInstalledApps(limit)
            
            val text = if (result.isSuccess) {
                result.getOrNull() ?: "Приложений не найдено"
            } else {
                "❌ Ошибка получения списка приложений: ${result.exceptionOrNull()?.message}"
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
    
    // ==================== Helper Methods ====================
    
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

