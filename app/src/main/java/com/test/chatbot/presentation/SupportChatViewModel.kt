package com.test.chatbot.presentation

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.test.chatbot.models.Message
import com.test.chatbot.data.UserPreferences
import com.test.chatbot.data.DeviceInfoProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel для чата службы поддержки
 * Автоматически обрабатывает все сообщения через support service
 */
class SupportChatViewModel(
    private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SupportChatUiState())
    val uiState: StateFlow<SupportChatUiState> = _uiState.asStateFlow()
    
    // MCP клиент для вызова support tools
    private var mcpClient: com.test.chatbot.mcp.McpClient? = null
    
    // URL MCP сервера
    private val mcpServerUrl = "http://10.0.2.2:3000/mcp"
    
    // User preferences для получения данных пользователя
    private val userPreferences = UserPreferences(context)
    
    // Device info provider для получения информации об устройстве
    private val deviceInfoProvider = DeviceInfoProvider(context)
    
    init {
        connectToMcpServer()
        // Добавляем приветственное сообщение с персонализацией
        val userName = userPreferences.fullName
        val deviceModel = deviceInfoProvider.deviceModel
        
        addBotMessage(
            "👋 **Добро пожаловать в службу поддержки, $userName!**\n\n" +
            "📱 Ваше устройство: $deviceModel\n" +
            "🆔 ID пользователя: ${userPreferences.userId}\n\n" +
            "Я помогу вам решить любые вопросы по использованию приложения.\n\n" +
            "Просто задайте ваш вопрос, и я найду ответ в нашей базе знаний.\n\n" +
            "**Полезные команды:**\n" +
            "• Напишите `/tickets` - посмотреть мои тикеты\n" +
            "• Напишите `/ticket <описание>` - создать тикет\n" +
            "• Напишите `/status TICKET-001` - детали тикета\n" +
            "• Напишите `/user` - моя информация\n" +
            "• Напишите `/stats` - статистика поддержки\n\n" +
            "Чем я могу вам помочь?"
        )
    }
    
    /**
     * Подключение к MCP серверу
     */
    private fun connectToMcpServer() {
        viewModelScope.launch {
            try {
                Log.d("SupportChatViewModel", "Connecting to MCP server: $mcpServerUrl")
                mcpClient = com.test.chatbot.mcp.McpClient.createHttpClient(mcpServerUrl)
                
                mcpClient?.initialize()?.onSuccess { result ->
                    Log.d("SupportChatViewModel", "MCP connected: ${result.serverInfo?.name}")
                }?.onFailure {
                    Log.e("SupportChatViewModel", "MCP connection failed: ${it.message}")
                    mcpClient = null
                }
            } catch (e: Exception) {
                Log.e("SupportChatViewModel", "MCP connection error: ${e.message}")
                mcpClient = null
            }
        }
    }
    
    /**
     * Отправить сообщение в поддержку
     */
    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank()) return
        
        // Добавляем сообщение пользователя
        val userMsg = Message(text = userMessage, isUser = true)
        _uiState.update { it.copy(messages = it.messages + userMsg, isLoading = true) }
        
        viewModelScope.launch {
            try {
                // Проверяем специальные команды
                when {
                    userMessage.startsWith("/tickets") -> {
                        handleTicketsCommand()
                    }
                    userMessage.startsWith("/ticket ") -> {
                        val description = userMessage.removePrefix("/ticket ").trim()
                        handleCreateTicketCommand(description)
                    }
                    userMessage.startsWith("/status ") -> {
                        val ticketId = userMessage.removePrefix("/status ").trim()
                        handleTicketStatusCommand(ticketId)
                    }
                    userMessage.startsWith("/user") -> {
                        handleUserInfoCommand()
                    }
                    userMessage.startsWith("/stats") -> {
                        handleStatsCommand()
                    }
                    else -> {
                        // Обычный вопрос - отправляем в support service
                        handleSupportQuestion(userMessage)
                    }
                }
            } catch (e: Exception) {
                addBotMessage("❌ Ошибка: ${e.message}")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    /**
     * Обработка обычного вопроса через support service
     */
    private suspend fun handleSupportQuestion(question: String) {
        if (mcpClient == null) {
            addBotMessage("❌ Служба поддержки временно недоступна. Попробуйте позже.")
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        
        // Передаем динамические данные пользователя
        val arguments = mapOf(
            "question" to question,
            "user_id" to userPreferences.userId,
            "user_name" to userPreferences.fullName,
            "device_model" to deviceInfoProvider.deviceModel,
            "android_version" to deviceInfoProvider.androidVersion
        )
        
        val result = mcpClient?.callTool("support_answer", arguments)
        result?.onSuccess { toolResult ->
            val answer = toolResult.content.firstOrNull()?.text ?: "Извините, не удалось получить ответ."
            addBotMessage(answer)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка обращения к службе поддержки: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    /**
     * Показать тикеты пользователя
     */
    private suspend fun handleTicketsCommand() {
        if (mcpClient == null) {
            addBotMessage("❌ Служба поддержки временно недоступна.")
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        
        val result = mcpClient?.callTool("support_tickets", mapOf("user_id" to userPreferences.userId))
        result?.onSuccess { toolResult ->
            val tickets = toolResult.content.firstOrNull()?.text ?: "Нет тикетов"
            addBotMessage(tickets)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    /**
     * Создать новый тикет
     */
    private suspend fun handleCreateTicketCommand(description: String) {
        if (mcpClient == null) {
            addBotMessage("❌ Служба поддержки временно недоступна.")
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        
        // Определяем категорию и приоритет
        val category = when {
            description.contains("авториз", ignoreCase = true) || 
            description.contains("вход", ignoreCase = true) || 
            description.contains("ключ", ignoreCase = true) -> "authorization"
            description.contains("rag", ignoreCase = true) || 
            description.contains("документ", ignoreCase = true) || 
            description.contains("поиск", ignoreCase = true) -> "rag"
            description.contains("зависа", ignoreCase = true) || 
            description.contains("медленн", ignoreCase = true) || 
            description.contains("производит", ignoreCase = true) -> "performance"
            description.contains("mcp", ignoreCase = true) || 
            description.contains("git", ignoreCase = true) -> "mcp"
            else -> "general"
        }
        
        val priority = when {
            description.contains("срочно", ignoreCase = true) || 
            description.contains("важно", ignoreCase = true) || 
            description.contains("критич", ignoreCase = true) -> "high"
            description.contains("желательно", ignoreCase = true) || 
            description.contains("предложение", ignoreCase = true) -> "low"
            else -> "medium"
        }
        
        val params = mapOf(
            "user_id" to userPreferences.userId,
            "subject" to description.take(100),
            "description" to description,
            "category" to category,
            "priority" to priority
        )
        
        val result = mcpClient?.callTool("support_create_ticket", params)
        result?.onSuccess { toolResult ->
            val message = toolResult.content.firstOrNull()?.text ?: "Тикет создан"
            addBotMessage(message)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка создания тикета: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    /**
     * Показать статус тикета
     */
    private suspend fun handleTicketStatusCommand(ticketId: String) {
        if (mcpClient == null) {
            addBotMessage("❌ Служба поддержки временно недоступна.")
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        
        val result = mcpClient?.callTool("support_ticket_details", mapOf("ticket_id" to ticketId))
        result?.onSuccess { toolResult ->
            val details = toolResult.content.firstOrNull()?.text ?: "Тикет не найден"
            addBotMessage(details)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    /**
     * Показать информацию о пользователе
     */
    private suspend fun handleUserInfoCommand() {
        // Показываем локальные данные пользователя
        val userInfo = buildString {
            appendLine("👤 **Информация о пользователе**")
            appendLine("━━━━━━━━━━━━━━━━━━━━")
            appendLine()
            appendLine("**ID:** ${userPreferences.userId}")
            appendLine("**Имя:** ${userPreferences.fullName}")
            userPreferences.email?.let { appendLine("**Email:** $it") }
            appendLine()
            appendLine("**📱 Устройство:**")
            appendLine("Модель: ${deviceInfoProvider.deviceModel}")
            appendLine("Производитель: ${deviceInfoProvider.manufacturer}")
            appendLine("Android: ${deviceInfoProvider.androidVersion}")
            appendLine("SDK: ${deviceInfoProvider.sdkVersion}")
        }
        
        addBotMessage(userInfo)
        _uiState.update { it.copy(isLoading = false) }
    }
    
    /**
     * Показать статистику поддержки
     */
    private suspend fun handleStatsCommand() {
        if (mcpClient == null) {
            addBotMessage("❌ Служба поддержки временно недоступна.")
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        
        val result = mcpClient?.callTool("support_stats", emptyMap())
        result?.onSuccess { toolResult ->
            val stats = toolResult.content.firstOrNull()?.text ?: "Нет данных"
            addBotMessage(stats)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    /**
     * Добавить сообщение бота
     */
    private fun addBotMessage(text: String) {
        val botMessage = Message(text = text, isUser = false)
        _uiState.update { 
            it.copy(
                messages = it.messages + botMessage,
                isLoading = false
            ) 
        }
    }
    
    /**
     * Очистить чат
     */
    fun clearChat() {
        _uiState.update { 
            SupportChatUiState().copy(
                messages = listOf(
                    Message(
                        text = "👋 **Добро пожаловать в службу поддержки!**\n\n" +
                               "Чем я могу вам помочь?",
                        isUser = false
                    )
                )
            )
        }
    }
}

/**
 * UI состояние чата поддержки
 */
data class SupportChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
