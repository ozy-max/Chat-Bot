package com.test.chatbot.mcp.server

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.*
import java.io.IOException

// Import new services
import com.test.chatbot.mcp.server.SystemMonitorService
import com.test.chatbot.mcp.server.FileManagerService
import com.test.chatbot.mcp.server.ScriptAutomationService
import com.test.chatbot.mcp.server.TermuxService
import com.test.chatbot.mcp.server.AdbWifiService

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
    
    // Advanced services
    private lateinit var systemMonitorService: SystemMonitorService
    private lateinit var fileManagerService: FileManagerService
    private lateinit var scriptAutomationService: ScriptAutomationService
    private lateinit var termuxService: TermuxService
    private lateinit var adbWifiService: AdbWifiService
    
    // RAG (Retrieval-Augmented Generation)
    private lateinit var documentIndexService: com.test.chatbot.rag.DocumentIndexService
    private var ollamaClient: com.test.chatbot.rag.OllamaClient? = null
    private var ollamaRAGService: com.test.chatbot.rag.OllamaRAGService? = null
    
    // Ollama configuration
    private var ollamaUrl: String = ""
    private var ollamaEnabled: Boolean = false

    companion object {
        private const val TAG = "McpServer"
        const val DEFAULT_PORT = 3000
        private const val PREFS_NAME = "mcp_server_prefs"
        private const val PREF_OLLAMA_URL = "ollama_url"
        private const val DEFAULT_OLLAMA_URL = "http://10.0.2.2:11434" // Для эмулятора
    }

    /**
     * Загрузить сохранённый URL Ollama
     */
    private fun loadOllamaUrl() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ollamaUrl = prefs.getString(PREF_OLLAMA_URL, DEFAULT_OLLAMA_URL) ?: DEFAULT_OLLAMA_URL
        Log.i(TAG, "📋 Загружен Ollama URL: $ollamaUrl")
    }
    
    /**
     * Сохранить URL Ollama
     */
    private fun saveOllamaUrl(url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_OLLAMA_URL, url).apply()
        Log.i(TAG, "💾 Сохранён Ollama URL: $url")
    }
    
    /**
     * Инициализация сервера
     */
    fun initialize() {
        try {
            // Загружаем сохранённый URL Ollama
            loadOllamaUrl()
            
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
            
            // Advanced services
            systemMonitorService = SystemMonitorService(context)
            fileManagerService = FileManagerService(context)
            scriptAutomationService = ScriptAutomationService(context)
            termuxService = TermuxService(context)
            adbWifiService = AdbWifiService(context)
            
            // RAG system with Ollama support
            try {
                ollamaClient = com.test.chatbot.rag.OllamaClient(ollamaUrl)
                // Проверяем доступность в фоне
                scope.launch {
                ollamaEnabled = ollamaClient?.isAvailable() ?: false
                if (ollamaEnabled) {
                    Log.i(TAG, "✅ Ollama доступна на $ollamaUrl")
                    // Создаём DocumentIndexService перед RAG сервисом
                    documentIndexService = com.test.chatbot.rag.DocumentIndexService(context, ollamaClient)
                    val rerankerService = com.test.chatbot.service.RerankerService(ollamaClient!!)
                    ollamaRAGService = com.test.chatbot.rag.OllamaRAGService(documentIndexService, ollamaClient!!, rerankerService)
                } else {
                    Log.w(TAG, "⚠️ Ollama недоступна, используется локальный режим")
                    // Создаём DocumentIndexService с локальными эмбеддингами
                    documentIndexService = com.test.chatbot.rag.DocumentIndexService(context, ollamaClient)
                }
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Ошибка инициализации Ollama: ${e.message}")
                // Fallback на локальный режим
                documentIndexService = com.test.chatbot.rag.DocumentIndexService(context, null)
            }
            
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
                ),
                // System Monitor Tools
                mapOf(
                    "name" to "system_info",
                    "description" to "Получить полную информацию о системе (батарея, память, CPU, сеть, хранилище)",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf<String, Any>(),
                        "required" to emptyList<String>()
                    )
                ),
                mapOf(
                    "name" to "battery_info",
                    "description" to "Получить информацию о батарее",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf<String, Any>(),
                        "required" to emptyList<String>()
                    )
                ),
                mapOf(
                    "name" to "memory_info",
                    "description" to "Получить информацию о памяти",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf<String, Any>(),
                        "required" to emptyList<String>()
                    )
                ),
                mapOf(
                    "name" to "cpu_info",
                    "description" to "Получить информацию о процессоре",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf<String, Any>(),
                        "required" to emptyList<String>()
                    )
                ),
                mapOf(
                    "name" to "network_info",
                    "description" to "Получить информацию о сетевом подключении",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf<String, Any>(),
                        "required" to emptyList<String>()
                    )
                ),
                mapOf(
                    "name" to "storage_info",
                    "description" to "Получить информацию о хранилище",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf<String, Any>(),
                        "required" to emptyList<String>()
                    )
                ),
                // File Manager Tools
                mapOf(
                    "name" to "fm_list",
                    "description" to "Получить список файлов и директорий",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "path" to mapOf(
                                "type" to "string",
                                "description" to "Путь к директории (относительно app_files)"
                            )
                        ),
                        "required" to emptyList<String>()
                    )
                ),
                mapOf(
                    "name" to "fm_read",
                    "description" to "Прочитать содержимое файла",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "path" to mapOf(
                                "type" to "string",
                                "description" to "Путь к файлу"
                            )
                        ),
                        "required" to listOf("path")
                    )
                ),
                mapOf(
                    "name" to "fm_write",
                    "description" to "Записать содержимое в файл",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "path" to mapOf(
                                "type" to "string",
                                "description" to "Путь к файлу"
                            ),
                            "content" to mapOf(
                                "type" to "string",
                                "description" to "Содержимое файла"
                            )
                        ),
                        "required" to listOf("path", "content")
                    )
                ),
                mapOf(
                    "name" to "fm_delete",
                    "description" to "Удалить файл или директорию",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "path" to mapOf(
                                "type" to "string",
                                "description" to "Путь к файлу или директории"
                            )
                        ),
                        "required" to listOf("path")
                    )
                ),
                mapOf(
                    "name" to "fm_search",
                    "description" to "Найти файлы по имени",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "pattern" to mapOf(
                                "type" to "string",
                                "description" to "Шаблон для поиска"
                            ),
                            "search_path" to mapOf(
                                "type" to "string",
                                "description" to "Путь для поиска (опционально)"
                            )
                        ),
                        "required" to listOf("pattern")
                    )
                ),
                // Script Automation Tools
                mapOf(
                    "name" to "script_list",
                    "description" to "Получить список сохранённых скриптов",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf<String, Any>(),
                        "required" to emptyList<String>()
                    )
                ),
                mapOf(
                    "name" to "script_info",
                    "description" to "Получить информацию о скрипте",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "script_id" to mapOf(
                                "type" to "string",
                                "description" to "ID скрипта"
                            )
                        ),
                        "required" to listOf("script_id")
                    )
                ),
                mapOf(
                    "name" to "script_execute",
                    "description" to "Выполнить скрипт",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "script_id" to mapOf(
                                "type" to "string",
                                "description" to "ID скрипта для выполнения"
                            )
                        ),
                        "required" to listOf("script_id")
                    )
                ),
                // Termux Tools
                mapOf(
                    "name" to "termux_info",
                    "description" to "Получить информацию о Termux",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf<String, Any>(),
                        "required" to emptyList<String>()
                    )
                ),
                mapOf(
                    "name" to "termux_command",
                    "description" to "Выполнить команду в Termux",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "command" to mapOf(
                                "type" to "string",
                                "description" to "Команда для выполнения"
                            )
                        ),
                        "required" to listOf("command")
                    )
                ),
                // ADB WiFi Tools
                mapOf(
                    "name" to "adb_wifi_info",
                    "description" to "Получить информацию о ADB over WiFi",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf<String, Any>(),
                        "required" to emptyList<String>()
                    )
                ),
                mapOf(
                    "name" to "ssh_info",
                    "description" to "Получить информацию о SSH доступе через Termux",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf<String, Any>(),
                        "required" to emptyList<String>()
                    )
                ),
                // RAG (Vector Search) Tools
                mapOf(
                    "name" to "index_text",
                    "description" to "Проиндексировать текст для векторного поиска",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "name" to mapOf(
                                "type" to "string",
                                "description" to "Название документа"
                            ),
                            "content" to mapOf(
                                "type" to "string",
                                "description" to "Содержимое документа"
                            ),
                            "type" to mapOf(
                                "type" to "string",
                                "description" to "Тип документа (text, markdown, code)"
                            )
                        ),
                        "required" to listOf("name", "content")
                    )
                ),
                mapOf(
                    "name" to "index_file",
                    "description" to "Проиндексировать файл для векторного поиска",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "file_path" to mapOf(
                                "type" to "string",
                                "description" to "Путь к файлу (относительно app_files)"
                            )
                        ),
                        "required" to listOf("file_path")
                    )
                ),
                mapOf(
                    "name" to "semantic_search",
                    "description" to "Семантический поиск по проиндексированным документам",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "query" to mapOf(
                                "type" to "string",
                                "description" to "Поисковый запрос"
                            ),
                            "top_k" to mapOf(
                                "type" to "number",
                                "description" to "Количество результатов (по умолчанию 5)"
                            )
                        ),
                        "required" to listOf("query")
                    )
                ),
                mapOf(
                    "name" to "list_indexed_docs",
                    "description" to "Получить список проиндексированных документов",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf<String, Any>(),
                        "required" to emptyList<String>()
                    )
                ),
                mapOf(
                    "name" to "index_stats",
                    "description" to "Получить статистику векторного индекса",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf<String, Any>(),
                        "required" to emptyList<String>()
                    )
                ),
                mapOf(
                    "name" to "clear_index",
                    "description" to "Очистить векторный индекс",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf<String, Any>(),
                        "required" to emptyList<String>()
                    )
                ),
                // Ollama Tools
                mapOf(
                    "name" to "ollama_status",
                    "description" to "Проверить статус Ollama сервера и список моделей",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf<String, Any>(),
                        "required" to emptyList<String>()
                    )
                ),
                mapOf(
                    "name" to "ollama_configure",
                    "description" to "Настроить URL Ollama сервера",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "url" to mapOf(
                                "type" to "string",
                                "description" to "URL Ollama сервера (например: http://192.168.1.100:11434)"
                            )
                        ),
                        "required" to listOf("url")
                    )
                ),
                mapOf(
                    "name" to "rag_query",
                    "description" to "Задать вопрос с использованием RAG (поиск + генерация ответа через Ollama)",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "question" to mapOf(
                                "type" to "string",
                                "description" to "Вопрос для RAG системы"
                            ),
                            "top_k" to mapOf(
                                "type" to "number",
                                "description" to "Количество релевантных документов (по умолчанию 10)"
                            )
                        ),
                        "required" to listOf("question")
                    )
                ),
                mapOf(
                    "name" to "compare_rag",
                    "description" to "Сравнить ответы с RAG и без RAG для анализа эффективности",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "question" to mapOf(
                                "type" to "string",
                                "description" to "Вопрос для сравнения"
                            ),
                            "top_k" to mapOf(
                                "type" to "number",
                                "description" to "Количество релевантных документов (по умолчанию 10)"
                            ),
                            "model" to mapOf(
                                "type" to "string",
                                "description" to "Модель Ollama (по умолчанию llama3)"
                            )
                        ),
                        "required" to listOf("question")
                    )
                ),
                mapOf(
                    "name" to "compare_filtering",
                    "description" to "Сравнить методы фильтрации: без фильтра, с threshold, с LLM reranker",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "question" to mapOf(
                                "type" to "string",
                                "description" to "Вопрос для сравнения"
                            ),
                            "model" to mapOf(
                                "type" to "string",
                                "description" to "Модель Ollama (по умолчанию llama3)"
                            )
                        ),
                        "required" to listOf("question")
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
            // System Monitor Tools
            "system_info" -> runBlocking { getSystemInfo() }
            "battery_info" -> runBlocking { getBatteryInfo() }
            "memory_info" -> runBlocking { getMemoryInfo() }
            "cpu_info" -> runBlocking { getCpuInfo() }
            "network_info" -> runBlocking { getNetworkInfo() }
            "storage_info" -> runBlocking { getStorageInfo() }
            // File Manager Tools
            "fm_list" -> runBlocking { fileManagerList(arguments) }
            "fm_read" -> runBlocking { fileManagerRead(arguments) }
            "fm_write" -> runBlocking { fileManagerWrite(arguments) }
            "fm_delete" -> runBlocking { fileManagerDelete(arguments) }
            "fm_search" -> runBlocking { fileManagerSearch(arguments) }
            // Script Automation Tools
            "script_list" -> runBlocking { scriptList() }
            "script_info" -> runBlocking { scriptInfo(arguments) }
            "script_execute" -> runBlocking { scriptExecute(arguments) }
            // Termux Tools
            "termux_info" -> runBlocking { termuxInfo() }
            "termux_command" -> runBlocking { termuxCommand(arguments) }
            // ADB WiFi Tools
            "adb_wifi_info" -> runBlocking { adbWifiInfo() }
            "ssh_info" -> runBlocking { sshInfo() }
            // RAG Tools
            "index_text" -> runBlocking { indexText(arguments) }
            "index_file" -> runBlocking { indexFile(arguments) }
            "semantic_search" -> runBlocking { semanticSearch(arguments) }
            "list_indexed_docs" -> runBlocking { listIndexedDocs() }
            "index_stats" -> runBlocking { indexStats() }
            "clear_index" -> runBlocking { clearIndex() }
            "reset_database" -> runBlocking { resetDatabase() }
            // Ollama Tools
            "ollama_status" -> runBlocking { ollamaStatus() }
            "ollama_configure" -> runBlocking { ollamaConfigure(arguments) }
            "rag_query" -> runBlocking { ragQuery(arguments) }
            "compare_rag" -> runBlocking { compareRAG(arguments) }
            "compare_filtering" -> runBlocking { compareFiltering(arguments) }
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
    
    // ==================== System Monitor Tools ====================
    
    private suspend fun getSystemInfo(): Map<String, Any> {
        return try {
            val result = systemMonitorService.getSystemInfo()
            createToolResponse(result)
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    private suspend fun getBatteryInfo(): Map<String, Any> {
        return try {
            val result = systemMonitorService.getBatteryInfo()
            createToolResponse(result)
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    private suspend fun getMemoryInfo(): Map<String, Any> {
        return try {
            val result = systemMonitorService.getMemoryInfo()
            createToolResponse(result)
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    private suspend fun getCpuInfo(): Map<String, Any> {
        return try {
            val result = systemMonitorService.getCpuInfo()
            createToolResponse(result)
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    private suspend fun getNetworkInfo(): Map<String, Any> {
        return try {
            val result = systemMonitorService.getNetworkInfo()
            createToolResponse(result)
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    private suspend fun getStorageInfo(): Map<String, Any> {
        return try {
            val result = systemMonitorService.getStorageInfo()
            createToolResponse(result)
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    // ==================== File Manager Tools ====================
    
    private suspend fun fileManagerList(arguments: JsonObject?): Map<String, Any> {
        return try {
            val path = arguments?.get("path")?.asString ?: ""
            val result = fileManagerService.listDirectory(path)
            createToolResponse(result)
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    private suspend fun fileManagerRead(arguments: JsonObject?): Map<String, Any> {
        return try {
            val path = arguments?.get("path")?.asString
            
            if (path.isNullOrBlank()) {
                return createErrorMessage("Необходимо указать путь к файлу")
            }
            
            val result = fileManagerService.readFile(path)
            createToolResponse(result)
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    private suspend fun fileManagerWrite(arguments: JsonObject?): Map<String, Any> {
        return try {
            val path = arguments?.get("path")?.asString
            val content = arguments?.get("content")?.asString
            
            if (path.isNullOrBlank() || content == null) {
                return createErrorMessage("Необходимо указать путь и содержимое файла")
            }
            
            val result = fileManagerService.writeFile(path, content)
            createToolResponse(result)
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    private suspend fun fileManagerDelete(arguments: JsonObject?): Map<String, Any> {
        return try {
            val path = arguments?.get("path")?.asString
            
            if (path.isNullOrBlank()) {
                return createErrorMessage("Необходимо указать путь к файлу")
            }
            
            val result = fileManagerService.deleteFile(path)
            createToolResponse(result)
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    private suspend fun fileManagerSearch(arguments: JsonObject?): Map<String, Any> {
        return try {
            val pattern = arguments?.get("pattern")?.asString
            val searchPath = arguments?.get("search_path")?.asString ?: ""
            
            if (pattern.isNullOrBlank()) {
                return createErrorMessage("Необходимо указать шаблон для поиска")
            }
            
            val result = fileManagerService.searchFiles(pattern, searchPath)
            createToolResponse(result)
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    // ==================== Script Automation Tools ====================
    
    private suspend fun scriptList(): Map<String, Any> {
        return try {
            val result = scriptAutomationService.listScripts()
            createToolResponse(result)
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    private suspend fun scriptInfo(arguments: JsonObject?): Map<String, Any> {
        return try {
            val scriptId = arguments?.get("script_id")?.asString
            
            if (scriptId.isNullOrBlank()) {
                return createErrorMessage("Необходимо указать ID скрипта")
            }
            
            val result = scriptAutomationService.getScriptInfo(scriptId)
            createToolResponse(result)
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    private suspend fun scriptExecute(arguments: JsonObject?): Map<String, Any> {
        return try {
            val scriptId = arguments?.get("script_id")?.asString
            
            if (scriptId.isNullOrBlank()) {
                return createErrorMessage("Необходимо указать ID скрипта")
            }
            
            // TODO: Передать MCP клиент для выполнения скриптов с MCP инструментами
            val result = scriptAutomationService.executeScript(scriptId, null)
            
            result.fold(
                onSuccess = { executionResult ->
                    val message = buildString {
                        append("📜 Выполнение скрипта: ${executionResult.scriptId}\n")
                        append("━━━━━━━━━━━━━━━━━━━━\n\n")
                        
                        executionResult.steps.forEachIndexed { index, step ->
                            val status = if (step.success) "✅" else "❌"
                            append("${index + 1}. $status ${step.command.action}\n")
                            if (step.output != null) {
                                append("   ${step.output}\n")
                            }
                            if (step.error != null) {
                                append("   ❌ ${step.error}\n")
                            }
                            append("\n")
                        }
                        
                        if (executionResult.success) {
                            append("✅ Скрипт выполнен успешно")
                        } else {
                            append("❌ Скрипт завершён с ошибками")
                        }
                    }
                    
                    mapOf(
                        "content" to listOf(
                            mapOf("type" to "text", "text" to message)
                        )
                    )
                },
                onFailure = { error ->
                    createErrorMessage("Ошибка выполнения скрипта: ${error.message}")
                }
            )
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    // ==================== Termux Tools ====================
    
    private suspend fun termuxInfo(): Map<String, Any> {
        return try {
            val result = termuxService.getTermuxInfo()
            createToolResponse(result)
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    private suspend fun termuxCommand(arguments: JsonObject?): Map<String, Any> {
        return try {
            val command = arguments?.get("command")?.asString
            
            if (command.isNullOrBlank()) {
                return createErrorMessage("Необходимо указать команду")
            }
            
            val result = termuxService.executeCommand(command)
            createToolResponse(result)
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    // ==================== ADB WiFi Tools ====================
    
    private suspend fun adbWifiInfo(): Map<String, Any> {
        return try {
            val result = adbWifiService.getAdbWifiInfo()
            createToolResponse(result)
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    private suspend fun sshInfo(): Map<String, Any> {
        return try {
            val result = adbWifiService.getSshInfo()
            createToolResponse(result)
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    // ==================== RAG (Vector Search) Tools ====================
    
    private suspend fun indexText(arguments: JsonObject?): Map<String, Any> {
        return try {
            val name = arguments?.get("name")?.asString
            val content = arguments?.get("content")?.asString
            val type = arguments?.get("type")?.asString ?: "text"
            
            if (name.isNullOrBlank() || content.isNullOrBlank()) {
                return createErrorMessage("Необходимо указать name и content")
            }
            
            val result = documentIndexService.indexDocument(name, content, type)
            
            if (result.isSuccess) {
                val indexResult = result.getOrNull()!!
                mapOf(
                    "content" to listOf(
                        mapOf("type" to "text", "text" to indexResult.toSummary())
                    )
                )
            } else {
                createErrorMessage("Ошибка индексации: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    private suspend fun indexFile(arguments: JsonObject?): Map<String, Any> {
        return try {
            val filePath = arguments?.get("file_path")?.asString
            
            if (filePath.isNullOrBlank()) {
                return createErrorMessage("Необходимо указать file_path")
            }
            
            // Полный путь к файлу в app files
            val fullPath = context.filesDir.absolutePath + "/" + filePath
            
            val result = documentIndexService.indexFile(fullPath)
            
            if (result.isSuccess) {
                val indexResult = result.getOrNull()!!
                mapOf(
                    "content" to listOf(
                        mapOf("type" to "text", "text" to indexResult.toSummary())
                    )
                )
            } else {
                createErrorMessage("Ошибка индексации файла: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    private suspend fun semanticSearch(arguments: JsonObject?): Map<String, Any> {
        return try {
            val query = arguments?.get("query")?.asString
            val topK = arguments?.get("top_k")?.asInt ?: 5
            
            if (query.isNullOrBlank()) {
                return createErrorMessage("Необходимо указать query")
            }
            
            val result = documentIndexService.search(query, topK)
            
            if (result.isSuccess) {
                val results = result.getOrNull()!!
                
                val text = buildString {
                    append("🔍 Результаты поиска: \"$query\"\n")
                    append("━━━━━━━━━━━━━━━━━━━━\n\n")
                    
                    if (results.isEmpty()) {
                        append("Ничего не найдено\n")
                    } else {
                        results.forEachIndexed { index, searchResult ->
                            append("${index + 1}. ${searchResult.docName} (${searchResult.docType})\n")
                            append("   Релевантность: ${(searchResult.similarity * 100).toInt()}%\n")
                            append("   Текст: ${searchResult.chunkText.take(150)}")
                            if (searchResult.chunkText.length > 150) append("...")
                            append("\n\n")
                        }
                        
                        append("Найдено: ${results.size} результатов")
                    }
                }
                
                mapOf(
                    "content" to listOf(
                        mapOf("type" to "text", "text" to text)
                    )
                )
            } else {
                createErrorMessage("Ошибка поиска: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    private suspend fun listIndexedDocs(): Map<String, Any> {
        return try {
            val result = documentIndexService.listDocuments()
            
            if (result.isSuccess) {
                val documents = result.getOrNull()!!
                
                val text = buildString {
                    append("📚 Проиндексированные документы (${documents.size})\n")
                    append("━━━━━━━━━━━━━━━━━━━━\n\n")
                    
                    if (documents.isEmpty()) {
                        append("Нет проиндексированных документов\n\n")
                        append("Используйте /index для индексации файлов")
                    } else {
                        documents.forEach { doc ->
                            append("📄 ${doc.name} (${doc.type})\n")
                            append("   ID: ${doc.id}\n")
                            append("   Размер: ${doc.content.length} символов\n")
                            append("   Создан: ${formatTimestamp(doc.createdAt)}\n\n")
                        }
                    }
                }
                
                mapOf(
                    "content" to listOf(
                        mapOf("type" to "text", "text" to text)
                    )
                )
            } else {
                createErrorMessage("Ошибка получения списка: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    private suspend fun indexStats(): Map<String, Any> {
        return try {
            val verificationResult = documentIndexService.verifyIndex()
            
            if (verificationResult.isSuccess) {
                val verification = verificationResult.getOrNull()!!
                mapOf(
                    "content" to listOf(
                        mapOf("type" to "text", "text" to verification.toSummary())
                    )
                )
            } else {
                createErrorMessage("Ошибка получения статистики: ${verificationResult.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    private suspend fun clearIndex(): Map<String, Any> {
        return try {
            val result = documentIndexService.clearIndex()
            
            if (result.isSuccess) {
                mapOf(
                    "content" to listOf(
                        mapOf("type" to "text", "text" to "✅ Векторный индекс очищен")
                    )
                )
            } else {
                createErrorMessage("Ошибка очистки индекса: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    private suspend fun resetDatabase(): Map<String, Any> {
        return try {
            val result = documentIndexService.resetDatabase(context)
            
            if (result.isSuccess) {
                mapOf(
                    "content" to listOf(
                        mapOf("type" to "text", "text" to "✅ База данных RAG полностью удалена и пересоздана\n\n" +
                                "Теперь выполните: /index demo")
                    )
                )
            } else {
                createErrorMessage("Ошибка сброса БД: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
    
    // ==================== Ollama Tools ====================
    
    private suspend fun ollamaStatus(): Map<String, Any> {
        return try {
            if (ollamaClient == null) {
                return createErrorMessage("Ollama клиент не инициализирован")
            }
            
            val available = ollamaClient!!.isAvailable()
            
            if (!available) {
                val text = buildString {
                    append("❌ Ollama недоступна\n\n")
                    append("URL: $ollamaUrl\n\n")
                    append("Убедитесь что:\n")
                    append("1. Ollama запущена на компьютере\n")
                    append("2. URL правильный\n")
                    append("3. Устройство подключено к той же сети\n\n")
                    append("Используйте /ollama config для настройки URL")
                }
                
                return mapOf(
                    "content" to listOf(
                        mapOf("type" to "text", "text" to text)
                    )
                )
            }
            
            // Получаем список моделей
            val modelsResult = ollamaClient!!.listModels()
            
            val text = buildString {
                append("✅ Ollama доступна\n")
                append("━━━━━━━━━━━━━━━━━━━━\n\n")
                append("URL: $ollamaUrl\n")
                append("Статус: Подключено\n\n")
                
                if (modelsResult.isSuccess) {
                    val models = modelsResult.getOrNull()!!
                    append("📦 Установленные модели (${models.size}):\n\n")
                    
                    models.forEach { model ->
                        append("• ${model.name}\n")
                        val sizeMB = model.size / (1024 * 1024)
                        append("  Размер: $sizeMB MB\n")
                        append("  Обновлена: ${model.modifiedAt}\n\n")
                    }
                } else {
                    append("⚠️ Не удалось получить список моделей\n")
                }
            }
            
            mapOf(
                "content" to listOf(
                    mapOf("type" to "text", "text" to text)
                )
            )
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    private suspend fun ollamaConfigure(arguments: JsonObject?): Map<String, Any> {
        return try {
            val url = arguments?.get("url")?.asString
            
            if (url.isNullOrBlank()) {
                return createErrorMessage("Необходимо указать URL")
            }
            
            // Обновляем URL
            ollamaUrl = url.trimEnd('/')
            ollamaClient?.setBaseUrl(ollamaUrl)
            
            // Сохраняем URL в настройках
            saveOllamaUrl(ollamaUrl)
            
            // Проверяем доступность
            val available = ollamaClient?.isAvailable() ?: false
            
            val text = if (available) {
                ollamaEnabled = true
                // Инициализируем RAG сервис (используем существующий documentIndexService)
                val rerankerService = com.test.chatbot.service.RerankerService(ollamaClient!!)
                ollamaRAGService = com.test.chatbot.rag.OllamaRAGService(documentIndexService, ollamaClient!!, rerankerService)
                Log.i(TAG, "✅ OllamaRAGService инициализирован")
                "✅ Ollama настроена успешно\n\nURL: $ollamaUrl\nСтатус: Подключено\n\n🧠 RAG активирован"
            } else {
                ollamaEnabled = false
                ollamaRAGService = null
                "⚠️ Ollama настроена, но недоступна\n\nURL: $ollamaUrl\n\nУбедитесь что Ollama запущена"
            }
            
            mapOf(
                "content" to listOf(
                    mapOf("type" to "text", "text" to text)
                )
            )
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    private suspend fun ragQuery(arguments: JsonObject?): Map<String, Any> {
        return try {
            val question = arguments?.get("question")?.asString
            val topK = arguments?.get("top_k")?.asInt ?: 10 // Увеличено с 3 до 10 для лучшего поиска
            
            if (question.isNullOrBlank()) {
                return createErrorMessage("Необходимо указать вопрос")
            }
            
            if (ollamaRAGService == null) {
                return createErrorMessage(
                    "RAG недоступен. Ollama не подключена.\n\n" +
                    "Используйте /ollama config для настройки"
                )
            }
            
            // Выполняем RAG запрос
            val result = ollamaRAGService!!.queryWithRAG(question, topK)
            
            if (result.isSuccess) {
                val ragResponse = result.getOrNull()!!
                mapOf(
                    "content" to listOf(
                        mapOf("type" to "text", "text" to ragResponse.toFormattedString())
                    )
                )
            } else {
                createErrorMessage("Ошибка RAG: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    private suspend fun compareRAG(arguments: JsonObject?): Map<String, Any> {
        return try {
            val question = arguments?.get("question")?.asString
            val topK = arguments?.get("top_k")?.asInt ?: 10
            val model = arguments?.get("model")?.asString ?: "llama3"
            
            if (question.isNullOrBlank()) {
                return createErrorMessage("Необходимо указать вопрос")
            }
            
            if (ollamaRAGService == null) {
                return createErrorMessage(
                    "RAG недоступен. Ollama не подключена.\n\n" +
                    "Используйте /ollama config для настройки"
                )
            }
            
            // Выполняем сравнение
            val result = ollamaRAGService!!.compareRAG(question, model, topK)
            
            if (result.isSuccess) {
                val comparison = result.getOrNull()!!
                mapOf(
                    "content" to listOf(
                        mapOf("type" to "text", "text" to comparison.toFormattedString())
                    )
                )
            } else {
                createErrorMessage("Ошибка сравнения: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    private suspend fun compareFiltering(arguments: JsonObject?): Map<String, Any> {
        return try {
            val question = arguments?.get("question")?.asString
            val model = arguments?.get("model")?.asString ?: "llama3"
            
            if (question.isNullOrBlank()) {
                return createErrorMessage("Необходимо указать вопрос")
            }
            
            if (ollamaRAGService == null) {
                return createErrorMessage(
                    "RAG недоступен. Ollama не подключена.\n\n" +
                    "Используйте /ollama config для настройки"
                )
            }
            
            // Выполняем сравнение фильтрации
            val result = ollamaRAGService!!.compareFiltering(question, model)
            
            if (result.isSuccess) {
                val comparison = result.getOrNull()!!
                mapOf(
                    "content" to listOf(
                        mapOf("type" to "text", "text" to comparison.toFormattedString())
                    )
                )
            } else {
                createErrorMessage("Ошибка сравнения фильтрации: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            createErrorResponse(e)
        }
    }
    
    // ==================== Helper Methods ====================
    
    private fun createToolResponse(result: Result<String>): Map<String, Any> {
        val text = if (result.isSuccess) {
            result.getOrNull() ?: "Операция выполнена"
        } else {
            "❌ Ошибка: ${result.exceptionOrNull()?.message}"
        }
        
        return mapOf(
            "content" to listOf(
                mapOf("type" to "text", "text" to text)
            )
        )
    }
    
    private fun createErrorResponse(e: Exception): Map<String, Any> {
        return mapOf(
            "content" to listOf(
                mapOf("type" to "text", "text" to "❌ Ошибка: ${e.message}")
            )
        )
    }
    
    private fun createErrorMessage(message: String): Map<String, Any> {
        return mapOf(
            "content" to listOf(
                mapOf("type" to "text", "text" to "❌ $message")
            )
        )
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

