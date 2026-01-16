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

/**
 * ViewModel для командного ассистента (RAG + MCP + Todoist)
 * Управляет задачами, проектом и дает рекомендации
 */
class TeamAssistantChatViewModel(
    private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(TeamAssistantChatUiState())
    val uiState: StateFlow<TeamAssistantChatUiState> = _uiState.asStateFlow()
    
    // MCP клиент - используем встроенный Kotlin сервер напрямую
    private var mcpClient: com.test.chatbot.mcp.McpClient? = null
    
    init {
        connectToEmbeddedMcpServer()
        addBotMessage(getWelcomeMessage())
    }
    
    /**
     * Подключение к встроенному Kotlin MCP серверу
     */
    private fun connectToEmbeddedMcpServer() {
        viewModelScope.launch {
            try {
                Log.d("TeamAssistantVM", "Connecting to embedded Kotlin MCP server...")
                
                // Используем встроенный HTTP сервер (порт 3000)
                val mcpServerUrl = "http://localhost:3000/mcp"
                mcpClient = com.test.chatbot.mcp.McpClient.createHttpClient(mcpServerUrl)
                
                mcpClient?.initialize()?.onSuccess { result ->
                    Log.d("TeamAssistantVM", "✅ MCP connected: ${result.serverInfo?.name}")
                    Log.d("TeamAssistantVM", "✅ Todoist token already configured in embedded server")
                }?.onFailure {
                    Log.e("TeamAssistantVM", "❌ MCP connection failed: ${it.message}")
                    mcpClient = null
                }
            } catch (e: Exception) {
                Log.e("TeamAssistantVM", "❌ MCP connection error: ${e.message}")
                mcpClient = null
            }
        }
    }
    
    /**
     * Отправить сообщение/команду
     */
    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank()) return
        
        // Добавляем сообщение пользователя
        val userMsg = Message(text = userMessage, isUser = true)
        _uiState.update { it.copy(messages = it.messages + userMsg, isLoading = true) }
        
        viewModelScope.launch {
            try {
                // Обрабатываем команды
                if (userMessage.startsWith("/")) {
                    handleCommand(userMessage)
                } else {
                    // Обычный вопрос - используем RAG через project_help
                    handleQuestion(userMessage)
                }
            } catch (e: Exception) {
                addBotMessage("❌ Ошибка: ${e.message}")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    /**
     * Обработка команд
     */
    private suspend fun handleCommand(message: String) {
        val parts = message.trim().split("\\s+".toRegex())
        val command = parts[0].substring(1).lowercase()
        
        when (command) {
            "tasks" -> {
                val priority = parts.getOrNull(1)?.trim() ?: "all"
                handleTasksListCommand(priority)
            }
            
            "create_task", "add_task" -> {
                val description = parts.drop(1).joinToString(" ").trim()
                if (description.isBlank()) {
                    addBotMessage("❌ Укажите описание задачи: /create_task Реализовать авторизацию")
                    _uiState.update { it.copy(isLoading = false) }
                    return
                }
                handleCreateTaskCommand(description)
            }
            
            "project_status", "status" -> {
                handleProjectStatusCommand()
            }
            
            "recommend", "recommendations" -> {
                handleRecommendationsCommand()
            }
            
            "complete_task", "done" -> {
                val taskId = parts.getOrNull(1)?.trim()
                if (taskId.isNullOrBlank()) {
                    addBotMessage("❌ Укажите ID задачи из Todoist: /complete_task 8234567890")
                    _uiState.update { it.copy(isLoading = false) }
                    return
                }
                handleCompleteTaskCommand(taskId)
            }
            
            "sync" -> {
                handleSyncCommand()
            }
            
            "scan", "scan_project" -> {
                handleScanCommand()
            }
            
            "project" -> {
                val subcommand = parts.getOrNull(1)?.lowercase()
                when (subcommand) {
                    "index" -> handleProjectIndexCommand()
                    "status", "info" -> handleProjectStatusCommand()
                    else -> {
                        addBotMessage("❓ Неизвестная подкоманда /project\n\n" +
                                "Доступные команды:\n" +
                                "/project index - индексация проекта из GitHub\n" +
                                "/project status - статус проекта")
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
            }
            
            "help" -> {
                addBotMessage(getHelpMessage())
                _uiState.update { it.copy(isLoading = false) }
            }
            
            else -> {
                addBotMessage("❓ Неизвестная команда. Используйте /help для списка команд.")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    /**
     * Обработка обычного вопроса через RAG
     */
    private suspend fun handleQuestion(question: String) {
        if (mcpClient == null) {
            addBotMessage("❌ MCP сервер недоступен")
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        
        val result = mcpClient?.callTool("project_help", mapOf("topic" to question))
        
        result?.onSuccess { toolResult ->
            val answer = toolResult.content.firstOrNull()?.text ?: "Информация не найдена"
            addBotMessage(answer)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка поиска: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    /**
     * Показать список задач с фильтрацией по приоритету
     * Команда: /tasks [priority]
     */
    private suspend fun handleTasksListCommand(priority: String) {
        if (mcpClient == null) {
            addBotMessage("❌ MCP сервер недоступен")
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        
        val statusFilter = when (priority.lowercase()) {
            "all" -> null
            "high", "medium", "low" -> "pending"
            else -> "pending"
        }
        
        val result = mcpClient?.callTool("list_tasks", mapOf(
            "status" to (statusFilter ?: "pending")
        ))
        
        result?.onSuccess { toolResult ->
            val tasksText = toolResult.content.firstOrNull()?.text ?: "Нет задач"
            val filteredTasks = if (priority != "all") {
                filterTasksByPriority(tasksText, priority)
            } else {
                tasksText
            }
            addBotMessage(filteredTasks)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка получения задач: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    /**
     * Создать новую задачу
     * Команда: /create_task <описание>
     */
    private suspend fun handleCreateTaskCommand(description: String) {
        if (mcpClient == null) {
            addBotMessage("❌ MCP сервер недоступен")
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        
        // Определяем приоритет по ключевым словам
        val priority = when {
            description.contains("срочно", ignoreCase = true) || 
            description.contains("важно", ignoreCase = true) ||
            description.contains("критично", ignoreCase = true) -> "🔴 HIGH"
            description.contains("низкий", ignoreCase = true) ||
            description.contains("потом", ignoreCase = true) -> "🟢 LOW"
            else -> "🟡 MEDIUM"
        }
        
        // Добавляем приоритет к описанию
        val fullDescription = "[$priority] $description"
        
        val result = mcpClient?.callTool("add_task", mapOf(
            "title" to description.take(100),
            "description" to fullDescription
        ))
        
        result?.onSuccess { toolResult ->
            val message = toolResult.content.firstOrNull()?.text ?: "Задача создана"
            addBotMessage("$message\n\nИспользуйте /tasks для просмотра всех задач")
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка создания задачи: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    /**
     * Отметить задачу как выполненную
     * Команда: /complete_task <ID>
     */
    private suspend fun handleCompleteTaskCommand(taskId: String) {
        if (mcpClient == null) {
            addBotMessage("❌ MCP сервер недоступен")
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        
        val result = mcpClient?.callTool("complete_task", mapOf("task_id" to taskId))
        
        result?.onSuccess { toolResult ->
            val message = toolResult.content.firstOrNull()?.text ?: "Задача завершена"
            addBotMessage(message)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка завершения задачи: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    /**
     * Синхронизация с Todoist
     * Команда: /sync
     */
    private suspend fun handleSyncCommand() {
        if (mcpClient == null) {
            addBotMessage("❌ MCP сервер недоступен")
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        
        addBotMessage("🔄 Синхронизация с Todoist...")
        
        val result = mcpClient?.callTool("sync_todoist", emptyMap())
        
        result?.onSuccess { toolResult ->
            val message = toolResult.content.firstOrNull()?.text 
                ?: "✅ Синхронизация завершена"
            addBotMessage("$message\n\nИспользуйте /tasks для просмотра задач")
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка синхронизации: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    /**
     * Индексация проекта из GitHub
     * Команда: /project index
     */
    private suspend fun handleProjectIndexCommand() {
        if (mcpClient == null) {
            addBotMessage("❌ MCP сервер недоступен")
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        
        addBotMessage("📂 Начинаю индексацию проекта из GitHub...\n" +
                "⏳ Это может занять 1-2 минуты...")
        
        val result = mcpClient?.callTool("project_index", emptyMap())
        
        result?.onSuccess { toolResult ->
            val message = toolResult.content.firstOrNull()?.text 
                ?: "✅ Индексация завершена"
            addBotMessage(message)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка индексации: ${it.message}\n\n" +
                    "💡 Проверьте:\n" +
                    "1. URL репозитория в настройках\n" +
                    "2. Python MCP сервер запущен\n" +
                    "3. Доступ к интернету")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    /**
     * Сканировать проект и найти проблемы
     * Команда: /scan
     */
    private suspend fun handleScanCommand() {
        if (mcpClient == null) {
            addBotMessage("❌ MCP сервер недоступен")
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        
        addBotMessage("🔍 Сканирование проекта через RAG...\n\n⏳ Это может занять 30-60 секунд")
        
        val result = mcpClient?.callTool("scan_project", mapOf("scope" to "all"))
        
        result?.onSuccess { toolResult ->
            val message = toolResult.content.firstOrNull()?.text 
                ?: "✅ Сканирование завершено"
            addBotMessage(message)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка сканирования: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    /**
     * Показать статус проекта с анализом через RAG
     * Команда: /project_status
     */
    private suspend fun handleProjectStatusCommand() {
        if (mcpClient == null) {
            addBotMessage("❌ MCP сервер недоступен")
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        
        val response = StringBuilder()
        response.appendLine("📊 **СТАТУС ПРОЕКТА**")
        response.appendLine("━━━━━━━━━━━━━━━━━━━━\n")
        
        // 1. Git статус
        val gitResult = mcpClient?.callTool("git_status", emptyMap())
        gitResult?.onSuccess { toolResult ->
            val gitStatus = toolResult.content.firstOrNull()?.text ?: ""
            response.appendLine(gitStatus)
            response.appendLine()
        }
        
        // 2. Информация о проекте
        val projectResult = mcpClient?.callTool("project_info", emptyMap())
        projectResult?.onSuccess { toolResult ->
            val projectInfo = toolResult.content.firstOrNull()?.text ?: ""
            response.appendLine(projectInfo)
            response.appendLine()
        }
        
        // 3. Активные задачи
        val tasksResult = mcpClient?.callTool("list_tasks", mapOf("status" to "pending"))
        tasksResult?.onSuccess { toolResult ->
            val tasks = toolResult.content.firstOrNull()?.text ?: "Нет активных задач"
            response.appendLine("**📋 АКТИВНЫЕ ЗАДАЧИ:**")
            response.appendLine(tasks)
            response.appendLine()
        }
        
        // 4. RAG анализ проекта
        response.appendLine("**🤖 АНАЛИЗ ПРОЕКТА (RAG):**")
        val ragAnalysis = analyzeProjectWithRAG()
        response.appendLine(ragAnalysis)
        
        addBotMessage(response.toString())
        _uiState.update { it.copy(isLoading = false) }
    }
    
    /**
     * Дать рекомендации по приоритетам на основе RAG + Git + задач
     * Команда: /recommend
     */
    private suspend fun handleRecommendationsCommand() {
        if (mcpClient == null) {
            addBotMessage("❌ MCP сервер недоступен")
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        
        val response = StringBuilder()
        response.appendLine("💡 **РЕКОМЕНДАЦИИ ПО ПРИОРИТЕТАМ**")
        response.appendLine("━━━━━━━━━━━━━━━━━━━━\n")
        
        // 1. Получаем задачи
        val tasksResult = mcpClient?.callTool("list_tasks", mapOf("status" to "pending"))
        val tasksText = tasksResult?.getOrNull()?.content?.firstOrNull()?.text ?: ""
        
        // 2. Получаем git статус
        val gitResult = mcpClient?.callTool("git_status", emptyMap())
        val gitStatus = gitResult?.getOrNull()?.content?.firstOrNull()?.text ?: ""
        
        // 3. Анализируем через RAG
        val recommendations = generateRecommendations(tasksText, gitStatus)
        response.appendLine(recommendations)
        
        // 4. Приоритеты задач
        response.appendLine("\n**📌 ПРЕДЛАГАЕМЫЙ ПОРЯДОК:**")
        val prioritizedTasks = prioritizeTasks(tasksText)
        response.appendLine(prioritizedTasks)
        
        addBotMessage(response.toString())
        _uiState.update { it.copy(isLoading = false) }
    }
    
    /**
     * Фильтровать задачи по приоритету
     */
    private fun filterTasksByPriority(tasksText: String, priority: String): String {
        val priorityMarker = when (priority.lowercase()) {
            "high" -> "🔴 HIGH"
            "medium" -> "🟡 MEDIUM"
            "low" -> "🟢 LOW"
            else -> return tasksText
        }
        
        val lines = tasksText.split("\n")
        val filtered = lines.filter { line ->
            line.contains(priorityMarker, ignoreCase = true) ||
            line.startsWith("📋") || // Заголовок
            line.startsWith("━") || // Разделитель
            line.isBlank()
        }
        
        return if (filtered.size > 3) {
            filtered.joinToString("\n")
        } else {
            "📋 Нет задач с приоритетом $priority\n\nИспользуйте /tasks для просмотра всех задач"
        }
    }
    
    /**
     * Анализ проекта через RAG (использует project_help MCP tool)
     */
    private suspend fun analyzeProjectWithRAG(): String {
        return try {
            val result = mcpClient?.callTool("project_help", mapOf(
                "topic" to "Текущее состояние проекта: основные компоненты, что в разработке, технические долги"
            ))
            
            result?.getOrNull()?.content?.firstOrNull()?.text 
                ?: "✅ Проект в активной разработке\n" +
                   "📱 Основные компоненты: RAG, MCP, Support Chat, Team Assistant\n" +
                   "🚀 В разработке: Интеграция Todoist, улучшение приоритизации"
        } catch (e: Exception) {
            "Ошибка RAG анализа: ${e.message}"
        }
    }
    
    /**
     * Генерация рекомендаций на основе задач и git статуса
     */
    private suspend fun generateRecommendations(tasksText: String, gitStatus: String): String {
        return try {
            val contextInfo = """
                Текущие задачи:
                $tasksText
                
                Git статус:
                $gitStatus
            """.trimIndent()
            
            val result = mcpClient?.callTool("project_help", mapOf(
                "topic" to "Рекомендации по приоритетам задач на основе: $contextInfo"
            ))
            
            result?.getOrNull()?.content?.firstOrNull()?.text 
                ?: generateSmartRecommendations(tasksText, gitStatus)
        } catch (e: Exception) {
            generateSmartRecommendations(tasksText, gitStatus)
        }
    }
    
    /**
     * Умные рекомендации на основе анализа задач и git статуса
     */
    private fun generateSmartRecommendations(tasksText: String, gitStatus: String): String {
        val recommendations = StringBuilder()
        
        // Анализируем задачи
        val hasHighPriority = tasksText.contains("🔴 HIGH", ignoreCase = true)
        val hasMediumPriority = tasksText.contains("🟡 MEDIUM", ignoreCase = true)
        val hasChangedFiles = gitStatus.contains("M ", ignoreCase = true)
        
        recommendations.appendLine("**📊 АНАЛИЗ:**")
        
        if (hasHighPriority) {
            recommendations.appendLine("⚠️ Обнаружены задачи высокого приоритета - начните с них")
        }
        
        if (hasChangedFiles) {
            recommendations.appendLine("📝 Есть незафиксированные изменения - рекомендуется сделать commit")
        }
        
        recommendations.appendLine("\n**💡 РЕКОМЕНДАЦИИ:**")
        recommendations.appendLine("1. Завершите задачи высокого приоритета (🔴 HIGH)")
        recommendations.appendLine("2. Зафиксируйте текущие изменения в Git")
        recommendations.appendLine("3. Синхронизируйтесь с Todoist (/sync)")
        recommendations.appendLine("4. Переходите к задачам среднего приоритета")
        
        return recommendations.toString()
    }
    
    /**
     * Приоритизация задач
     */
    private fun prioritizeTasks(tasksText: String): String {
        val tasks = mutableListOf<Pair<Int, String>>()
        
        // Парсим задачи и определяем приоритет
        tasksText.split("\n").forEach { line ->
            if (line.contains("⏳")) {
                val priority = when {
                    line.contains("🔴 HIGH") -> 1
                    line.contains("🟡 MEDIUM") -> 2
                    line.contains("🟢 LOW") -> 3
                    else -> 4
                }
                tasks.add(priority to line)
            }
        }
        
        // Сортируем по приоритету
        val sorted = tasks.sortedBy { it.first }
        
        return if (sorted.isNotEmpty()) {
            sorted.mapIndexed { index, (priority, task) ->
                val emoji = when (priority) {
                    1 -> "⚠️"
                    3 -> "⏸️"
                    else -> ""
                }
                "   ${index + 1}. ${task.trim()} $emoji"
            }.joinToString("\n")
        } else {
            "Нет активных задач"
        }
    }
    
    /**
     * Добавить сообщение бота
     */
    fun addBotMessage(text: String) {
        val botMsg = Message(text = text, isUser = false)
        _uiState.update { it.copy(messages = it.messages + botMsg) }
    }
    
    /**
     * Приветственное сообщение
     */
    private fun getWelcomeMessage(): String {
        return """
            🤖 **КОМАНДНЫЙ АССИСТЕНТ**
            ━━━━━━━━━━━━━━━━━━━━
            
            Я помогу вам управлять проектом, задачами и дам рекомендации на основе RAG анализа.
            
            **📋 УПРАВЛЕНИЕ ЗАДАЧАМИ:**
            • `/tasks` - все задачи
            • `/tasks high` - только высокий приоритет
            • `/create_task <описание>` - создать задачу
            • `/complete_task <ID>` - завершить задачу
            • `/sync` - синхронизация с Todoist
            
            **📊 СТАТУС И АНАЛИЗ:**
            • `/project_status` - полный статус проекта
            • `/recommend` - рекомендации по приоритетам
            
            **💡 ПРИМЕРЫ:**
            • "Покажи задачи с высоким приоритетом"
            • "Создай задачу: СРОЧНО исправить баг"
            • "Что делать в первую очередь?"
            
            Чем могу помочь?
        """.trimIndent()
    }
    
    /**
     * Справка по командам
     */
    private fun getHelpMessage(): String {
        return """
            🤖 **СПРАВКА ПО КОМАНДАМ**
            ━━━━━━━━━━━━━━━━━━━━
            
            **📋 УПРАВЛЕНИЕ ЗАДАЧАМИ:**
            `/tasks [all|high|medium|low]` - показать задачи
            `/create_task <описание>` - создать новую задачу
            `/complete_task <Todoist ID>` - отметить задачу выполненной
            `/sync` - обновить список задач из Todoist
            
            **🔍 СКАНИРОВАНИЕ ПРОЕКТА:**
            `/project index` - индексировать проект из GitHub
            `/scan` - сканировать проект и найти проблемы (RAG)
            Находит: deprecated код, TODO/FIXME, code smells, 
            проблемы безопасности, производительности
            
            **📊 СТАТУС И АНАЛИЗ:**
            `/project_status` - полный статус проекта (Git + Задачи + RAG)
            `/recommend` - рекомендации по приоритетам
            
            **💡 ПРИОРИТЕТЫ (автоматические):**
            • Ключевые слова "СРОЧНО", "ВАЖНО" → 🔴 HIGH
            • Ключевые слова "НИЗКИЙ", "ПОТОМ" → 🟢 LOW
            • Остальное → 🟡 MEDIUM
            
            **🔗 TODOIST DIRECT:**
            • Все задачи хранятся только в Todoist
            • Нет локального дублирования
            • Быстрый кэш в памяти (1 минута)
            • Используйте Todoist ID для завершения
            
            **🤖 RAG:**
            • Анализ проекта и документации
            • Умные рекомендации
            
            Просто задайте вопрос, и я найду ответ!
        """.trimIndent()
    }
    
    /**
     * Очистить историю чата
     */
    fun clearChat() {
        _uiState.update { 
            TeamAssistantChatUiState(
                messages = listOf(Message(text = getWelcomeMessage(), isUser = false))
            ) 
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        mcpClient = null
    }
}

/**
 * UI состояние для командного ассистента
 */
data class TeamAssistantChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false
)
