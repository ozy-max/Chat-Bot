package com.test.chatbot.mcp.server

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.test.chatbot.mcp.McpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class Script(
    val id: String,
    val name: String,
    val description: String,
    val commands: List<ScriptCommand>,
    val createdAt: Long = System.currentTimeMillis()
)

data class ScriptCommand(
    val type: String, // "mcp_tool", "shell", "delay"
    val action: String, // имя инструмента или команда
    val arguments: Map<String, Any> = emptyMap()
)

data class ScriptExecutionResult(
    val scriptId: String,
    val success: Boolean,
    val steps: List<StepResult>,
    val error: String? = null
)

data class StepResult(
    val command: ScriptCommand,
    val success: Boolean,
    val output: String?,
    val error: String? = null
)

class ScriptAutomationService(private val context: Context) {
    
    companion object {
        private const val TAG = "ScriptAutomation"
        private const val SCRIPTS_FILE = "automation_scripts.json"
    }
    
    private val scriptsFile: File by lazy {
        File(context.filesDir, SCRIPTS_FILE)
    }
    
    private val gson = Gson()
    
    /**
     * Создать новый скрипт
     */
    suspend fun createScript(
        name: String,
        description: String,
        commands: List<ScriptCommand>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val scripts = loadScripts().toMutableList()
            
            val scriptId = "script_${System.currentTimeMillis()}"
            val script = Script(
                id = scriptId,
                name = name,
                description = description,
                commands = commands
            )
            
            scripts.add(script)
            saveScripts(scripts)
            
            Log.i(TAG, "✅ Скрипт создан: $name ($scriptId)")
            Result.success("✅ Скрипт создан: $name\nID: $scriptId\nКоманд: ${commands.size}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка создания скрипта: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Получить список всех скриптов
     */
    suspend fun listScripts(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val scripts = loadScripts()
            
            if (scripts.isEmpty()) {
                Result.success("📜 Нет сохранённых скриптов\n\nИспользуйте команду для создания скрипта")
            } else {
                val info = buildString {
                    append("📜 СКРИПТЫ АВТОМАТИЗАЦИИ (${scripts.size})\n")
                    append("━━━━━━━━━━━━━━━━━━━━\n\n")
                    
                    scripts.forEach { script ->
                        append("🔧 ${script.name}\n")
                        append("   ID: ${script.id}\n")
                        append("   Описание: ${script.description}\n")
                        append("   Команд: ${script.commands.size}\n")
                        append("   Создан: ${formatDate(script.createdAt)}\n\n")
                    }
                }
                Result.success(info)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка получения списка: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Получить детальную информацию о скрипте
     */
    suspend fun getScriptInfo(scriptId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val script = findScript(scriptId)
                ?: return@withContext Result.failure(Exception("Скрипт не найден: $scriptId"))
            
            val info = buildString {
                append("🔧 ${script.name}\n")
                append("━━━━━━━━━━━━━━━━━━━━\n")
                append("ID: ${script.id}\n")
                append("Описание: ${script.description}\n")
                append("Создан: ${formatDate(script.createdAt)}\n")
                append("━━━━━━━━━━━━━━━━━━━━\n\n")
                append("КОМАНДЫ (${script.commands.size}):\n\n")
                
                script.commands.forEachIndexed { index, cmd ->
                    append("${index + 1}. ${cmd.type}: ${cmd.action}\n")
                    if (cmd.arguments.isNotEmpty()) {
                        append("   Аргументы: ${cmd.arguments}\n")
                    }
                }
            }
            
            Result.success(info)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка получения информации: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Выполнить скрипт
     */
    suspend fun executeScript(
        scriptId: String,
        mcpClient: McpClient? = null
    ): Result<ScriptExecutionResult> = withContext(Dispatchers.IO) {
        try {
            val script = findScript(scriptId)
                ?: return@withContext Result.failure(Exception("Скрипт не найден: $scriptId"))
            
            Log.i(TAG, "▶️ Выполнение скрипта: ${script.name}")
            
            val stepResults = mutableListOf<StepResult>()
            var allSuccess = true
            
            for ((index, command) in script.commands.withIndex()) {
                Log.i(TAG, "   Шаг ${index + 1}/${script.commands.size}: ${command.type} - ${command.action}")
                
                val stepResult = executeCommand(command, mcpClient)
                stepResults.add(stepResult)
                
                if (!stepResult.success) {
                    allSuccess = false
                    Log.w(TAG, "   ❌ Шаг провален: ${stepResult.error}")
                    break // Останавливаем выполнение при ошибке
                } else {
                    Log.i(TAG, "   ✅ Шаг выполнен")
                }
            }
            
            val result = ScriptExecutionResult(
                scriptId = scriptId,
                success = allSuccess,
                steps = stepResults
            )
            
            Log.i(TAG, if (allSuccess) "✅ Скрипт выполнен успешно" else "❌ Скрипт завершён с ошибками")
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка выполнения скрипта: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    private suspend fun executeCommand(
        command: ScriptCommand,
        mcpClient: McpClient?
    ): StepResult {
        return when (command.type) {
            "mcp_tool" -> {
                if (mcpClient == null) {
                    StepResult(
                        command = command,
                        success = false,
                        output = null,
                        error = "MCP клиент недоступен"
                    )
                } else {
                    try {
                        val result = mcpClient.callTool(command.action, command.arguments)
                        
                        result?.fold(
                            onSuccess = { toolResult ->
                                StepResult(
                                    command = command,
                                    success = true,
                                    output = toolResult.content.firstOrNull()?.text ?: "Выполнено",
                                    error = null
                                )
                            },
                            onFailure = { error ->
                                StepResult(
                                    command = command,
                                    success = false,
                                    output = null,
                                    error = error.message
                                )
                            }
                        ) ?: StepResult(
                            command = command,
                            success = false,
                            output = null,
                            error = "Нет ответа от инструмента"
                        )
                    } catch (e: Exception) {
                        StepResult(
                            command = command,
                            success = false,
                            output = null,
                            error = e.message
                        )
                    }
                }
            }
            
            "delay" -> {
                try {
                    val delayMs = (command.arguments["milliseconds"] as? Number)?.toLong() ?: 1000L
                    kotlinx.coroutines.delay(delayMs)
                    StepResult(
                        command = command,
                        success = true,
                        output = "Задержка ${delayMs}ms",
                        error = null
                    )
                } catch (e: Exception) {
                    StepResult(
                        command = command,
                        success = false,
                        output = null,
                        error = e.message
                    )
                }
            }
            
            else -> {
                StepResult(
                    command = command,
                    success = false,
                    output = null,
                    error = "Неизвестный тип команды: ${command.type}"
                )
            }
        }
    }
    
    /**
     * Удалить скрипт
     */
    suspend fun deleteScript(scriptId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val scripts = loadScripts().toMutableList()
            val script = scripts.find { it.id == scriptId }
                ?: return@withContext Result.failure(Exception("Скрипт не найден: $scriptId"))
            
            scripts.removeIf { it.id == scriptId }
            saveScripts(scripts)
            
            Log.i(TAG, "✅ Скрипт удалён: ${script.name}")
            Result.success("✅ Скрипт удалён: ${script.name}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка удаления скрипта: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    // Вспомогательные функции
    
    private fun loadScripts(): List<Script> {
        return try {
            if (scriptsFile.exists()) {
                val json = scriptsFile.readText()
                val type = object : TypeToken<List<Script>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки скриптов: ${e.message}", e)
            emptyList()
        }
    }
    
    private fun saveScripts(scripts: List<Script>) {
        try {
            val json = gson.toJson(scripts)
            scriptsFile.writeText(json)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка сохранения скриптов: ${e.message}", e)
            throw e
        }
    }
    
    private fun findScript(scriptId: String): Script? {
        return loadScripts().find { it.id == scriptId }
    }
    
    private fun formatDate(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}

