package com.test.chatbot.presentation

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.test.chatbot.models.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.test.chatbot.utils.MessageBridge

/**
 * ViewModel для экрана результатов сканирования проекта
 */
class ScanResultsViewModel(private val context: Context) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ScanResultsUiState())
    val uiState: StateFlow<ScanResultsUiState> = _uiState.asStateFlow()
    
    private var mcpClient: com.test.chatbot.mcp.McpClient? = null
    
    init {
        connectToMcpServer()
    }
    
    /**
     * Подключение к MCP серверу через HTTP (с увеличенным timeout для долгих операций)
     */
    private fun connectToMcpServer() {
        viewModelScope.launch {
            try {
                Log.d("ScanResultsVM", "Подключение к MCP серверу...")
                
                val mcpServerUrl = "http://localhost:3000/mcp"
                mcpClient = com.test.chatbot.mcp.McpClient.createHttpClient(mcpServerUrl)
                
                mcpClient?.initialize()?.onSuccess {
                    Log.d("ScanResultsVM", "✅ MCP сервер подключен (HTTP с timeout 120s)")
                }?.onFailure {
                    Log.e("ScanResultsVM", "❌ MCP подключение не удалось: ${it.message}")
                    mcpClient = null
                }
            } catch (e: Exception) {
                Log.e("ScanResultsVM", "❌ Ошибка подключения к MCP: ${e.message}")
                mcpClient = null
            }
        }
    }
    
    /**
     * Запустить сканирование проекта (с автоматической индексацией)
     */
    fun startScan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, scanningStatus = "Индексация проекта...", error = null) }
            
            if (mcpClient == null) {
                Log.e("ScanResultsVM", "❌ MCP клиент не инициализирован")
                _uiState.update { 
                    it.copy(
                        isScanning = false,
                        scanningStatus = "",
                        error = "MCP сервер недоступен. Попробуйте перезапустить приложение."
                    )
                }
                return@launch
            }
            
            try {
                // ШАГ 1: Индексация проекта (обязательна для качественного сканирования)
                Log.i("ScanResultsVM", "📚 Начало индексации проекта...")
                _uiState.update { it.copy(scanningStatus = "Шаг 1/2: Индексация проекта...") }
                
                // Небольшая задержка чтобы UI успел отобразиться
                kotlinx.coroutines.delay(500)
                
                val indexResult = mcpClient?.callTool("project_index", emptyMap())
                
                indexResult?.onSuccess { toolResult ->
                    val textContent = toolResult.content.firstOrNull()?.text ?: "Индексация выполнена"
                    Log.i("ScanResultsVM", "✅ Индексация: $textContent")
                    
                    // Проверяем, что файлы действительно проиндексированы
                    if (textContent.contains("Kotlin файлов: 0")) {
                        Log.e("ScanResultsVM", "❌ КРИТИЧЕСКАЯ ОШИБКА: Индексировано 0 файлов!")
                        _uiState.update { 
                            it.copy(
                                isScanning = false,
                                scanningStatus = "",
                                error = "❌ Не найдено .kt файлов для индексации!\n\n" +
                                       "💡 Возможные причины:\n" +
                                       "• Файлы не скопированы в assets\n" +
                                       "• Нужна пересборка: ./gradlew clean assembleDebug"
                            ) 
                        }
                        return@launch
                    }
                }?.onFailure { error ->
                    Log.e("ScanResultsVM", "❌ Ошибка индексации: ${error.message}")
                    _uiState.update { 
                        it.copy(
                            isScanning = false,
                            scanningStatus = "",
                            error = "❌ Ошибка индексации: ${error.message}"
                        ) 
                    }
                    return@launch
                }
                
                // Небольшая задержка перед переходом к следующему шагу
                kotlinx.coroutines.delay(500)
                
                // ШАГ 2: Сканирование проекта
                kotlinx.coroutines.delay(1000) // Пауза между шагами
                _uiState.update { it.copy(scanningStatus = "Шаг 2/2: Сканирование кода...") }
                Log.i("ScanResultsVM", "🔍 Начало сканирования проекта...")
                
                // Задержка чтобы UI отобразил второй статус
                kotlinx.coroutines.delay(500)
                
                val scanResult = mcpClient?.callTool("scan_project", mapOf("scope" to "all"))
                
                scanResult?.onSuccess { toolResult ->
                    Log.i("ScanResultsVM", "✅ Сканирование завершено успешно")
                    
                    // Парсим JSON с задачами
                    val tasksData = toolResult.content.firstOrNull()?.text ?: ""
                    Log.d("ScanResultsVM", "Длина ответа: ${tasksData.length} символов")
                    
                    val tasks = parseTasksFromResponse(tasksData)
                    Log.i("ScanResultsVM", "📊 Найдено задач: ${tasks.size}")
                    
                    _uiState.update { 
                        it.copy(
                            isScanning = false,
                            scanningStatus = "",
                            tasks = tasks,
                            error = null
                        )
                    }
                }?.onFailure { error ->
                    Log.e("ScanResultsVM", "❌ Ошибка сканирования: ${error.message}", error)
                    _uiState.update { 
                        it.copy(
                            isScanning = false,
                            scanningStatus = "",
                            error = "Ошибка сканирования: ${error.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("ScanResultsVM", "❌ Исключение при сканировании: ${e.message}", e)
                _uiState.update { 
                    it.copy(
                        isScanning = false,
                        scanningStatus = "",
                        error = "Ошибка: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Парсинг задач из ответа MCP
     */
    private fun parseTasksFromResponse(response: String): List<ScanTask> {
        val tasks = mutableListOf<ScanTask>()
        
        // Ищем JSON блок в ответе
        val jsonStart = response.indexOf("```json")
        val jsonEnd = response.indexOf("```", jsonStart + 7)
        
        if (jsonStart == -1 || jsonEnd == -1) {
            Log.w("ScanResultsVM", "JSON блок не найден в ответе")
            return emptyList()
        }
        
        val jsonText = response.substring(jsonStart + 7, jsonEnd).trim()
        
        try {
            val gson = com.google.gson.Gson()
            val jsonArray = gson.fromJson(jsonText, com.google.gson.JsonArray::class.java)
            
            jsonArray.forEach { element ->
                val obj = element.asJsonObject
                tasks.add(
                    ScanTask(
                        title = obj.get("title")?.asString ?: "",
                        description = obj.get("description")?.asString ?: "",
                        priority = obj.get("priority")?.asString ?: "medium",
                        category = obj.get("category")?.asString ?: "improvement",
                        file = obj.get("file")?.asString,
                        recommendation = obj.get("recommendation")?.asString ?: "",
                        isSelected = true // По умолчанию все выбраны
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("ScanResultsVM", "Ошибка парсинга JSON: ${e.message}")
        }
        
        return tasks
    }
    
    /**
     * Переключить выбор задачи
     */
    fun toggleTaskSelection(taskIndex: Int) {
        _uiState.update { state ->
            val updatedTasks = state.tasks.mapIndexed { index, task ->
                if (index == taskIndex) task.copy(isSelected = !task.isSelected)
                else task
            }
            state.copy(tasks = updatedTasks)
        }
    }
    
    /**
     * Изменить приоритет задачи
     */
    fun changeTaskPriority(taskIndex: Int, newPriority: String) {
        _uiState.update { state ->
            val updatedTasks = state.tasks.mapIndexed { index, task ->
                if (index == taskIndex) task.copy(priority = newPriority)
                else task
            }
            state.copy(tasks = updatedTasks)
        }
    }
    
    /**
     * Создать выбранные задачи в Todoist
     */
    fun createSelectedTasks(onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true, error = null) }
            
            val selectedTasks = _uiState.value.tasks.filter { it.isSelected }
            
            if (selectedTasks.isEmpty()) {
                _uiState.update { 
                    it.copy(
                        isCreating = false, 
                        error = "Выберите хотя бы одну задачу"
                    )
                }
                return@launch
            }
            
            if (mcpClient == null) {
                _uiState.update { 
                    it.copy(
                        isCreating = false, 
                        error = "MCP сервер недоступен"
                    )
                }
                return@launch
            }
            
            var createdCount = 0
            var errorCount = 0
            
            // Создаём задачи по одной
            selectedTasks.forEachIndexed { index, task ->
                try {
                    Log.i("ScanResultsVM", "Создание задачи ${index + 1}/${selectedTasks.size}: ${task.title}")
                    
                    val description = buildString {
                        append(task.description)
                        append("\n\n")
                        append("📁 Файл: ${task.file ?: "не указан"}\n")
                        append("💡 Рекомендация: ${task.recommendation}")
                    }
                    
                    // Определяем приоритет для Todoist (1-4)
                    val todoistPriority = when (task.priority) {
                        "high" -> 3      // High в Todoist
                        "medium" -> 2    // Medium в Todoist
                        "low" -> 1       // Normal в Todoist
                        else -> 2
                    }
                    
                    val result = mcpClient?.callTool(
                        "add_task",
                        mapOf(
                            "title" to task.title,
                            "description" to description,
                            "priority" to todoistPriority
                        )
                    )
                    
                    result?.onSuccess {
                        createdCount++
                        Log.i("ScanResultsVM", "✅ Задача ${index + 1} создана")
                    }?.onFailure {
                        errorCount++
                        Log.e("ScanResultsVM", "❌ Ошибка создания задачи ${index + 1}: ${it.message}")
                    }
                } catch (e: Exception) {
                    errorCount++
                    Log.e("ScanResultsVM", "❌ Исключение при создании задачи ${index + 1}: ${e.message}")
                }
            }
            
            Log.i("ScanResultsVM", "Создание завершено. Успешно: $createdCount, Ошибок: $errorCount")
            
            // Получаем общее количество задач из Todoist
            val totalTasksResult = mcpClient?.callTool("sync_todoist", emptyMap())
            var totalTasks = 0
            
            totalTasksResult?.onSuccess { toolResult ->
                val text = toolResult.content.firstOrNull()?.text ?: ""
                // Парсим количество из ответа "Всего задач в Todoist: X"
                val regex = """Всего задач в Todoist: (\d+)""".toRegex()
                val match = regex.find(text)
                totalTasks = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
            }
            
            _uiState.update { 
                it.copy(
                    isCreating = false,
                    createdCount = createdCount,
                    error = if (errorCount > 0) "Создано: $createdCount, Ошибок: $errorCount" else null
                )
            }
            
            // Отправляем сообщение в чат ассистента
            val message = buildString {
                append("✅ Задачи созданы в Todoist!\n\n")
                append("📊 Создано задач: $createdCount\n")
                if (errorCount > 0) {
                    append("⚠️ Ошибок: $errorCount\n")
                }
                append("\n📋 Всего задач в Todoist: $totalTasks\n\n")
                append("Используйте /tasks для просмотра")
            }
            MessageBridge.sendMessage(message)
            
            onComplete(totalTasks)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        mcpClient = null
    }
}

/**
 * UI состояние для экрана результатов сканирования
 */
data class ScanResultsUiState(
    val isScanning: Boolean = false,
    val scanningStatus: String = "", // "Индексация проекта..." или "Сканирование кода..."
    val isCreating: Boolean = false,
    val tasks: List<ScanTask> = emptyList(),
    val createdCount: Int = 0,
    val error: String? = null
)

/**
 * Задача из сканирования проекта
 */
data class ScanTask(
    val title: String,
    val description: String,
    val priority: String, // "high", "medium", "low"
    val category: String,
    val file: String?,
    val recommendation: String,
    val isSelected: Boolean = true
)
