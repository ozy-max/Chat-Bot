package com.test.chatbot.mcp

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * MCP (Model Context Protocol) клиент
 * Поддерживает подключение к MCP серверам через:
 * - HTTP (SSE - Server-Sent Events)
 * - Stdio (для локальных процессов)
 */
class McpClient(
    private val transport: McpTransport
) {
    private val gson = Gson()
    private val requestId = AtomicInteger(0)
    
    private var serverCapabilities: ServerCapabilities? = null
    private var isInitialized = false
    
    companion object {
        private const val TAG = "McpClient"
        
        /**
         * Создать HTTP клиент для подключения к MCP серверу
         */
        fun createHttpClient(serverUrl: String): McpClient {
            return McpClient(HttpTransport(serverUrl))
        }
        
        /**
         * Создать Stdio клиент для локального MCP сервера
         */
        fun createStdioClient(command: String, args: List<String> = emptyList()): McpClient {
            return McpClient(StdioTransport(command, args))
        }
    }
    
    /**
     * Инициализация соединения с MCP сервером
     */
    suspend fun initialize(): Result<InitializeResult> = withContext(Dispatchers.IO) {
        try {
            val params = InitializeParams()
            val response = sendRequest<InitializeResult>("initialize", params)
            
            response.onSuccess { result ->
                serverCapabilities = result.capabilities
                isInitialized = true
                
                // Отправляем уведомление о готовности
                sendNotification("notifications/initialized")
                
                Log.d(TAG, "MCP initialized: ${result.serverInfo?.name ?: "Unknown server"}")
            }
            
            response
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MCP: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Получить список доступных инструментов
     */
    suspend fun listTools(): Result<List<McpTool>> = withContext(Dispatchers.IO) {
        try {
            if (!isInitialized) {
                return@withContext Result.failure(Exception("MCP not initialized"))
            }
            
            val response = sendRequest<ListToolsResult>("tools/list", null)
            response.map { it.tools }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list tools: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Вызвать инструмент
     */
    suspend fun callTool(name: String, arguments: Map<String, Any>? = null): Result<CallToolResult> = 
        withContext(Dispatchers.IO) {
            try {
                if (!isInitialized) {
                    return@withContext Result.failure(Exception("MCP not initialized"))
                }
                
                val params = CallToolParams(name, arguments)
                sendRequest<CallToolResult>("tools/call", params)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to call tool $name: ${e.message}")
                Result.failure(e)
            }
        }
    
    /**
     * Получить список ресурсов
     */
    suspend fun listResources(): Result<List<McpResource>> = withContext(Dispatchers.IO) {
        try {
            if (!isInitialized) {
                return@withContext Result.failure(Exception("MCP not initialized"))
            }
            
            val response = sendRequest<ListResourcesResult>("resources/list", null)
            response.map { it.resources }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list resources: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Получить список промптов
     */
    suspend fun listPrompts(): Result<List<McpPrompt>> = withContext(Dispatchers.IO) {
        try {
            if (!isInitialized) {
                return@withContext Result.failure(Exception("MCP not initialized"))
            }
            
            val response = sendRequest<ListPromptsResult>("prompts/list", null)
            response.map { it.prompts }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list prompts: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Закрыть соединение
     */
    fun close() {
        transport.close()
        isInitialized = false
    }
    
    /**
     * Проверить возможности сервера
     */
    fun hasToolsSupport(): Boolean = serverCapabilities?.tools != null
    fun hasResourcesSupport(): Boolean = serverCapabilities?.resources != null
    fun hasPromptsSupport(): Boolean = serverCapabilities?.prompts != null
    
    // ===== Внутренние методы =====
    
    private suspend inline fun <reified T> sendRequest(method: String, params: Any?): Result<T> {
        val id = requestId.incrementAndGet()
        val request = JsonRpcRequest(
            id = id,
            method = method,
            params = params
        )
        
        val requestJson = gson.toJson(request)
        Log.d(TAG, "-> $requestJson")
        
        val responseJson = transport.send(requestJson)
        Log.d(TAG, "<- $responseJson")
        
        val type = TypeToken.getParameterized(JsonRpcResponse::class.java, T::class.java).type
        val response: JsonRpcResponse<T> = gson.fromJson(responseJson, type)
        
        return if (response.error != null) {
            Result.failure(Exception("MCP Error ${response.error.code}: ${response.error.message}"))
        } else if (response.result != null) {
            Result.success(response.result)
        } else {
            Result.failure(Exception("Empty response"))
        }
    }
    
    private fun sendNotification(method: String, params: Any? = null) {
        val notification = mapOf(
            "jsonrpc" to "2.0",
            "method" to method,
            "params" to params
        )
        val json = gson.toJson(notification)
        transport.sendAsync(json)
    }
}

/**
 * Интерфейс транспорта для MCP
 */
interface McpTransport {
    suspend fun send(message: String): String
    fun sendAsync(message: String)
    fun close()
}

/**
 * HTTP транспорт (для SSE серверов)
 */
class HttpTransport(private val serverUrl: String) : McpTransport {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    override suspend fun send(message: String): String = withContext(Dispatchers.IO) {
        val requestBody = message.toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url(serverUrl)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .build()
        
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: ${response.message}")
        }
        
        response.body?.string() ?: throw Exception("Empty response body")
    }
    
    override fun sendAsync(message: String) {
        val requestBody = message.toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url(serverUrl)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                Log.e("HttpTransport", "Async send failed: ${e.message}")
            }
            
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }
    
    override fun close() {
        // HTTP клиент не требует явного закрытия
    }
}

/**
 * Stdio транспорт (для локальных MCP серверов)
 */
class StdioTransport(
    private val command: String,
    private val args: List<String>
) : McpTransport {
    
    private var process: Process? = null
    private var writer: OutputStreamWriter? = null
    private var reader: BufferedReader? = null
    
    private fun ensureStarted() {
        if (process == null) {
            val processBuilder = ProcessBuilder(listOf(command) + args)
            processBuilder.redirectErrorStream(false)
            
            process = processBuilder.start()
            writer = OutputStreamWriter(process!!.outputStream)
            reader = BufferedReader(InputStreamReader(process!!.inputStream))
        }
    }
    
    override suspend fun send(message: String): String = withContext(Dispatchers.IO) {
        ensureStarted()
        
        writer?.write(message + "\n")
        writer?.flush()
        
        reader?.readLine() ?: throw Exception("No response from process")
    }
    
    override fun sendAsync(message: String) {
        try {
            ensureStarted()
            writer?.write(message + "\n")
            writer?.flush()
        } catch (e: Exception) {
            Log.e("StdioTransport", "Async send failed: ${e.message}")
        }
    }
    
    override fun close() {
        writer?.close()
        reader?.close()
        process?.destroy()
        process = null
    }
}

