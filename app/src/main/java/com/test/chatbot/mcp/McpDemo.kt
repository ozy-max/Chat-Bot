package com.test.chatbot.mcp

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Демонстрация работы с MCP клиентом
 * Показывает как подключиться к MCP серверу и получить список инструментов
 */
object McpDemo {
    
    private const val TAG = "McpDemo"
    
    /**
     * Подключиться к MCP серверу и получить список инструментов
     * 
     * @param serverUrl URL MCP сервера (например "http://localhost:3000/mcp")
     * @param onResult Callback с результатом
     */
    fun connectAndListTools(
        serverUrl: String,
        onResult: (McpConnectionResult) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = McpClient.createHttpClient(serverUrl)
            
            try {
                // 1. Инициализация соединения
                Log.d(TAG, "Connecting to MCP server: $serverUrl")
                
                val initResult = client.initialize()
                
                initResult.onFailure { error ->
                    Log.e(TAG, "Failed to initialize: ${error.message}")
                    onResult(McpConnectionResult.Error("Ошибка инициализации: ${error.message}"))
                    return@launch
                }
                
                val serverInfo = initResult.getOrNull()
                Log.d(TAG, "Connected to: ${serverInfo?.serverInfo?.name}")
                
                // 2. Получение списка инструментов
                val toolsResult = client.listTools()
                
                toolsResult.onSuccess { tools ->
                    Log.d(TAG, "Found ${tools.size} tools:")
                    tools.forEach { tool ->
                        Log.d(TAG, "  - ${tool.name}: ${tool.description}")
                    }
                    
                    onResult(McpConnectionResult.Success(
                        serverName = serverInfo?.serverInfo?.name ?: "Unknown",
                        serverVersion = serverInfo?.serverInfo?.version,
                        tools = tools
                    ))
                }.onFailure { error ->
                    Log.e(TAG, "Failed to list tools: ${error.message}")
                    onResult(McpConnectionResult.Error("Ошибка получения инструментов: ${error.message}"))
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Connection error: ${e.message}")
                onResult(McpConnectionResult.Error("Ошибка соединения: ${e.message}"))
            } finally {
                client.close()
            }
        }
    }
    
    /**
     * Форматирование списка инструментов для отображения
     */
    fun formatToolsList(tools: List<McpTool>): String {
        if (tools.isEmpty()) return "Нет доступных инструментов"
        
        return buildString {
            appendLine("📦 Доступные MCP инструменты (${tools.size}):")
            appendLine()
            
            tools.forEachIndexed { index, tool ->
                appendLine("${index + 1}. 🔧 ${tool.name}")
                tool.description?.let { appendLine("   📝 $it") }
                
                tool.inputSchema?.properties?.let { props ->
                    if (props.isNotEmpty()) {
                        appendLine("   📥 Параметры:")
                        props.forEach { (name, schema) ->
                            val required = tool.inputSchema?.required?.contains(name) == true
                            val reqMark = if (required) "*" else ""
                            appendLine("      - $name$reqMark (${schema.type}): ${schema.description ?: ""}")
                        }
                    }
                }
                appendLine()
            }
        }
    }
}

/**
 * Результат подключения к MCP серверу
 */
sealed class McpConnectionResult {
    data class Success(
        val serverName: String,
        val serverVersion: String?,
        val tools: List<McpTool>
    ) : McpConnectionResult()
    
    data class Error(val message: String) : McpConnectionResult()
}

