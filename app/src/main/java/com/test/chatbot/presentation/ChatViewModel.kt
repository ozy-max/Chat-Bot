package com.test.chatbot.presentation

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.test.chatbot.data.PreferencesRepository
import com.test.chatbot.data.memory.MemoryRepository
import com.test.chatbot.data.memory.MemoryState
import com.test.chatbot.models.*
import com.test.chatbot.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class ChatViewModel(
    private val context: Context,
    private val repository: ChatRepository = ChatRepository(),
    private val preferencesRepository: PreferencesRepository? = null,
    private val memoryRepository: MemoryRepository? = null
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    // История для Claude
    private val claudeHistory = mutableListOf<ClaudeMessage>()
    // История для YandexGPT
    private val yandexHistory = mutableListOf<YandexGptMessage>()
    
    // Хранение summary для компрессии
    private var currentSummary: String? = null
    // Счетчик сообщений с момента последней компрессии
    private var messagesSinceCompression = 0
    // Токены без компрессии (для статистики)
    private var totalOriginalTokens = 0
    // Загруженный summary предыдущего диалога
    private var previousDialogSummary: String? = null
    
    // MCP клиент и инструменты
    private var mcpClient: com.test.chatbot.mcp.McpClient? = null
    private var mcpTools = listOf<com.test.chatbot.mcp.McpTool>()
    
    init {
        loadSavedSettings()
        loadSavedSummary()
        processPendingMessagesFromKill()
        connectToMcpServer() // Автоматическое подключение к MCP при запуске
    }
    
    /**
     * Подключение к MCP серверу
     */
    private fun connectToMcpServer() {
        viewModelScope.launch {
            try {
                val serverUrl = _uiState.value.mcpServerUrl
                if (serverUrl.isBlank()) {
                    Log.e("ChatViewModel", "MCP server URL not configured")
                    return@launch
                }
                
                Log.e("ChatViewModel", "Connecting to MCP server: $serverUrl")
                mcpClient = com.test.chatbot.mcp.McpClient.createHttpClient(serverUrl)
                
                mcpClient?.initialize()?.onSuccess { result ->
                    Log.e("ChatViewModel", "MCP connected successfully: ${result.serverInfo?.name}")
                    
                    // Получаем список инструментов
                    mcpClient?.listTools()?.onSuccess { tools ->
                        mcpTools = tools
                        Log.e("ChatViewModel", "MCP tools loaded: ${tools.size}")
                    }
                }?.onFailure {
                    Log.e("ChatViewModel", "MCP connection failed: ${it.message}")
                    mcpClient = null
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "MCP connection error: ${e.message}")
                mcpClient = null
            }
        }
    }
    
    /**
     * Загрузка сохранённого summary предыдущего диалога
     */
    private fun loadSavedSummary() {
        viewModelScope.launch {
            memoryRepository?.let { repo ->
                val summary = repo.getSavedSummary()
                previousDialogSummary = summary
                
                // Загружаем состояние памяти (вкл/выкл)
                val memoryEnabled = preferencesRepository?.loadMemoryEnabled() ?: true
                
                _uiState.update { 
                    it.copy(
                        memoryState = it.memoryState.copy(
                            isEnabled = memoryEnabled,
                            hasSummary = summary != null,
                            summaryPreview = summary?.take(100)?.plus("...") ?: "",
                            fullSummaryText = summary ?: "" // Полный текст summary
                        )
                    ) 
                }
                
            }
        }
    }
    
    /**
     * Обработка pending сообщений после kill процесса
     * Если приложение было убито, создаём summary из сохранённых сообщений
     */
    private fun processPendingMessagesFromKill() {
        viewModelScope.launch {
            try {
                val pendingMessages = preferencesRepository?.loadPendingUserMessages() ?: return@launch
                if (pendingMessages.isEmpty()) return@launch
                
                // Проверяем включена ли память
                val memoryEnabled = preferencesRepository?.loadMemoryEnabled() ?: true
                if (!memoryEnabled) {
                    preferencesRepository?.clearPendingUserMessages()
                    return@launch
                }
                
                Log.d("ChatViewModel", "Found ${pendingMessages.size} pending messages after kill, creating summary...")
                
                // Создаём summary из pending сообщений
                val provider = _uiState.value.selectedProvider
                val summaryResult = when (provider) {
                    AiProvider.CLAUDE -> repository.summarizeClaudeHistory(
                        _uiState.value.apiKey,
                        pendingMessages.map { ClaudeMessage(role = "user", content = it) }
                    )
                    AiProvider.YANDEX_GPT -> repository.summarizeYandexHistory(
                        _uiState.value.yandexApiKey,
                        _uiState.value.yandexFolderId,
                        pendingMessages.map { YandexGptMessage(role = "user", text = it) }
                    )
                }
                
                summaryResult.onSuccess { result ->
                    memoryRepository?.saveSummary(result.summary)
                    previousDialogSummary = result.summary
                    
                    _uiState.update { 
                        it.copy(
                            memoryState = it.memoryState.copy(
                                hasSummary = true,
                                summaryPreview = result.summary.take(100) + "...",
                                fullSummaryText = result.summary
                            )
                        ) 
                    }
                    
                    Log.d("ChatViewModel", "Summary created from pending messages")
                }.onFailure { error ->
                    Log.e("ChatViewModel", "Failed to create summary from pending: ${error.message}")
                }
                
                // Очищаем pending сообщения
                preferencesRepository?.clearPendingUserMessages()
                
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error processing pending messages: ${e.message}")
            }
        }
    }
    
    
    /**
     * Загрузка сохранённых настроек из DataStore
     */
    private fun loadSavedSettings() {
        preferencesRepository?.let { prefs ->
            viewModelScope.launch {
                try {
                    val settings = prefs.settingsFlow.first()
                    val provider = try {
                        AiProvider.valueOf(settings.selectedProvider)
                    } catch (e: Exception) {
                        AiProvider.CLAUDE
                    }
                    
                    _uiState.update { 
                        it.copy(
                            apiKey = settings.claudeApiKey,
                            yandexApiKey = settings.yandexApiKey,
                            yandexFolderId = settings.yandexFolderId,
                            todoistToken = settings.todoistToken,
                            temperature = settings.temperature,
                            maxTokens = settings.maxTokens,
                            selectedProvider = provider,
                            // Не показываем диалог API ключей если ключи уже сохранены
                            showApiKeyDialog = settings.claudeApiKey.isBlank() && settings.yandexApiKey.isBlank(),
                            isSettingsLoaded = true
                        )
                    }
                    
                    // Устанавливаем Todoist токен во встроенный сервер
                    if (settings.todoistToken.isNotBlank()) {
                        try {
                            com.test.chatbot.ChatBotApplication.mcpServer.setTodoistToken(settings.todoistToken)
                            Log.e("ChatViewModel", "✅ Todoist token loaded into embedded server")
                        } catch (e: Exception) {
                            Log.e("ChatViewModel", "❌ Failed to load Todoist token: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error loading settings: ${e.message}")
                    _uiState.update { it.copy(isSettingsLoaded = true) }
                }
            }
        } ?: run {
            _uiState.update { it.copy(isSettingsLoaded = true) }
        }
    }
    
    fun onUiEvent(event: ChatUiEvents) {
        when (event) {
            is ChatUiEvents.SendMessage -> sendMessage(event.message)
            is ChatUiEvents.UpdateApiKey -> updateApiKey(event.apiKey)
            is ChatUiEvents.UpdateYandexApiKey -> updateYandexApiKey(event.apiKey)
            is ChatUiEvents.UpdateYandexFolderId -> updateYandexFolderId(event.folderId)
            is ChatUiEvents.UpdateTodoistToken -> updateTodoistToken(event.token)
            is ChatUiEvents.UpdateTemperature -> updateTemperature(event.temperature)
            is ChatUiEvents.UpdateMaxTokens -> updateMaxTokens(event.maxTokens)
            is ChatUiEvents.UpdateProvider -> updateProvider(event.provider)
            is ChatUiEvents.ShowApiKeyDialog -> showApiKeyDialog()
            is ChatUiEvents.DismissApiKeyDialog -> dismissApiKeyDialog()
            is ChatUiEvents.ShowSettingsDialog -> showSettingsDialog()
            is ChatUiEvents.DismissSettingsDialog -> dismissSettingsDialog()
            is ChatUiEvents.DismissError -> dismissError()
            is ChatUiEvents.ClearChat -> clearChat()
            // Сравнение моделей
            is ChatUiEvents.CompareModels -> compareModels(event.query)
            is ChatUiEvents.ShowComparisonDialog -> showComparisonDialog()
            is ChatUiEvents.DismissComparisonDialog -> dismissComparisonDialog()
            is ChatUiEvents.ClearComparisonResult -> clearComparisonResult()
            // Компрессия диалога
            is ChatUiEvents.ToggleCompression -> toggleCompression(event.enabled)
            is ChatUiEvents.UpdateCompressionThreshold -> updateCompressionThreshold(event.threshold)
            is ChatUiEvents.ManualCompress -> manualCompress()
            is ChatUiEvents.ShowCompressionInfo -> showCompressionInfo()
            is ChatUiEvents.DismissCompressionInfo -> dismissCompressionInfo()
            
            // Долговременная память
            is ChatUiEvents.ToggleMemory -> toggleMemory(event.enabled)
            is ChatUiEvents.ClearAllMemories -> clearAllMemories()
            is ChatUiEvents.ShowMemoryDialog -> showMemoryDialog()
            is ChatUiEvents.DismissMemoryDialog -> dismissMemoryDialog()
            
            // AI Features Bottom Sheet
            is ChatUiEvents.ShowAiFeaturesSheet -> showAiFeaturesSheet()
            is ChatUiEvents.DismissAiFeaturesSheet -> dismissAiFeaturesSheet()
            
            // Lifecycle
            is ChatUiEvents.OnAppPause -> onAppPause()
        }
    }
    
    /**
     * Вызывается при уходе приложения в фон (onPause/onStop) или закрытии
     * Сохраняет summary если память включена
     */
    fun onAppPause() {
        // Сохраняем summary если память включена и есть достаточно сообщений
        if (_uiState.value.memoryState.isEnabled && _uiState.value.messages.size >= 2) {
            saveCurrentDialogSummary()
        }
    }
    
    /**
     * Сохранить pending сообщения пользователя для восстановления после kill
     */
    private fun savePendingUserMessages() {
        viewModelScope.launch {
            val userMessages = _uiState.value.messages
                .filter { it.isUser }
                .map { it.text }
            preferencesRepository?.savePendingUserMessages(userMessages)
        }
    }
    
    private fun sendMessage(userMessage: String) {
        if (userMessage.isBlank()) return
        
        // Проверяем все MCP команды
        if (userMessage.startsWith("/")) {
            handleMcpCommand(userMessage)
            return
        }
        
        // Добавляем сообщение пользователя в UI
        val userMsg = Message(text = userMessage, isUser = true)
        _uiState.update { it.copy(messages = it.messages + userMsg) }
        
        // Сохраняем pending сообщения (для восстановления после kill)
        if (_uiState.value.memoryState.isEnabled) {
            savePendingUserMessages()
        }
        
        // Отправляем запрос
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                // 🚀 ВСЕГДА используем RAG для всех вопросов
                handleRAGQueryAutomatic(userMessage)
                
                // Проверяем нужна ли автоматическая компрессия
                checkAndPerformAutoCompression()
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        error = "Ошибка: ${e.message}",
                        isLoading = false
                    ) 
                }
            }
        }
    }
    
    private suspend fun sendToClaude() {
        // Получаем контекст памяти
        val memoryContext = getMemoryContext()
        
        val result = repository.sendMessageToClaude(
            _uiState.value.apiKey,
            claudeHistory,
            _uiState.value.temperature,
            _uiState.value.maxTokens,
            memoryContext
        )
        
        result.onSuccess { response ->
            // Добавляем в историю
            claudeHistory.add(ClaudeMessage(role = "assistant", content = response.text))
            
            // Обновляем статистику токенов
            val currentStats = _uiState.value.tokenStats
            val newStats = currentStats.copy(
                lastInputTokens = response.inputTokens,
                lastOutputTokens = response.outputTokens,
                totalInputTokens = currentStats.totalInputTokens + response.inputTokens,
                totalOutputTokens = currentStats.totalOutputTokens + response.outputTokens,
                totalTokens = currentStats.totalTokens + response.inputTokens + response.outputTokens,
                requestCount = currentStats.requestCount + 1
            )
            
            // Проверяем stop_reason
            val warningMessage = when (response.stopReason) {
                "max_tokens" -> "\n\n⚠️ Ответ был обрезан из-за достижения лимита токенов"
                "end_turn" -> null
                else -> null
            }
            
            // Обновляем последнее сообщение пользователя с токенами
            val updatedMessages = _uiState.value.messages.toMutableList()
            val lastUserMessageIndex = updatedMessages.indexOfLast { it.isUser }
            if (lastUserMessageIndex >= 0) {
                updatedMessages[lastUserMessageIndex] = updatedMessages[lastUserMessageIndex].copy(
                    inputTokens = response.inputTokens
                )
            }
            
            // Показываем ответ с токенами
            val botMessage = Message(
                text = (response.text.ifEmpty { "Получен пустой ответ" }) + (warningMessage ?: ""),
                isUser = false,
                inputTokens = response.inputTokens,
                outputTokens = response.outputTokens,
                provider = AiProvider.CLAUDE
            )
            
            _uiState.update { 
                it.copy(
                    messages = updatedMessages + botMessage,
                    isLoading = false,
                    tokenStats = newStats
                ) 
            }
        }.onFailure { exception ->
            _uiState.update { 
                it.copy(
                    error = "Ошибка Claude: ${exception.message}",
                    isLoading = false
                ) 
            }
        }
    }
    
    private suspend fun sendToYandexGpt() {
        // Получаем контекст памяти
        val memoryContext = getMemoryContext()
        
        val result = repository.sendMessageToYandexGpt(
            _uiState.value.yandexApiKey,
            _uiState.value.yandexFolderId,
            yandexHistory,
            _uiState.value.temperature,
            _uiState.value.maxTokens,
            memoryContext
        )
        
        result.onSuccess { response ->
            // Добавляем в историю
            yandexHistory.add(YandexGptMessage(role = "assistant", text = response.text))
            
            // Обновляем статистику токенов
            val currentStats = _uiState.value.tokenStats
            val newStats = currentStats.copy(
                lastInputTokens = response.inputTokens,
                lastOutputTokens = response.outputTokens,
                totalInputTokens = currentStats.totalInputTokens + response.inputTokens,
                totalOutputTokens = currentStats.totalOutputTokens + response.outputTokens,
                totalTokens = currentStats.totalTokens + response.inputTokens + response.outputTokens,
                requestCount = currentStats.requestCount + 1
            )
            
            // Обновляем последнее сообщение пользователя с токенами
            val updatedMessages = _uiState.value.messages.toMutableList()
            val lastUserMessageIndex = updatedMessages.indexOfLast { it.isUser }
            if (lastUserMessageIndex >= 0) {
                updatedMessages[lastUserMessageIndex] = updatedMessages[lastUserMessageIndex].copy(
                    inputTokens = response.inputTokens
                )
            }
            
            // Проверяем статус ответа YandexGPT
            val warningMessage = when (response.stopReason) {
                "ALTERNATIVE_STATUS_TRUNCATED_FINAL" -> "\n\n⚠️ Ответ был обрезан из-за достижения лимита токенов"
                "ALTERNATIVE_STATUS_CONTENT_FILTER" -> "\n\n⚠️ Ответ был заблокирован фильтром контента"
                else -> null
            }
            
            // Показываем ответ с токенами
            val botMessage = Message(
                text = (response.text.ifEmpty { "Получен пустой ответ" }) + (warningMessage ?: ""),
                isUser = false,
                inputTokens = response.inputTokens,
                outputTokens = response.outputTokens,
                provider = AiProvider.YANDEX_GPT
            )
            _uiState.update { 
                it.copy(
                    messages = updatedMessages + botMessage,
                    isLoading = false,
                    tokenStats = newStats
                ) 
            }
        }.onFailure { exception ->
            _uiState.update { 
                it.copy(
                    error = "Ошибка YandexGPT: ${exception.message}",
                    isLoading = false
                ) 
            }
        }
    }
    
    private fun clearChat() {
        // Сохраняем summary текущего диалога перед очисткой (если включена память)
        if (_uiState.value.memoryState.isEnabled && _uiState.value.messages.isNotEmpty()) {
            saveCurrentDialogSummary()
        }
        
        claudeHistory.clear()
        yandexHistory.clear()
        
        // Сброс данных компрессии
        currentSummary = null
        messagesSinceCompression = 0
        totalOriginalTokens = 0
        previousDialogSummary = null
        
        _uiState.update { 
            it.copy(
                messages = emptyList(), 
                tokenStats = TokenStats(),
                compressionState = CompressionState(isEnabled = it.compressionSettings.enabled)
            ) 
        }
    }
    
    /**
     * Сохранение summary текущего диалога в долговременную память
     * Сохраняет ТОЛЬКО информацию от пользователя (не действия ассистента)
     */
    private fun saveCurrentDialogSummary() {
        viewModelScope.launch {
            try {
                val messages = _uiState.value.messages
                
                // Берём только сообщения пользователя
                val userMessages = messages.filter { it.isUser }
                if (userMessages.isEmpty()) return@launch
                
                // Получаем summary через API (передаём только сообщения пользователя)
                val provider = _uiState.value.selectedProvider
                val summaryResult = when (provider) {
                    AiProvider.CLAUDE -> repository.summarizeClaudeHistory(
                        _uiState.value.apiKey,
                        userMessages.map { ClaudeMessage(
                            role = "user",
                            content = it.text
                        )}
                    )
                    AiProvider.YANDEX_GPT -> repository.summarizeYandexHistory(
                        _uiState.value.yandexApiKey,
                        _uiState.value.yandexFolderId,
                        userMessages.map { YandexGptMessage(
                            role = "user",
                            text = it.text
                        )}
                    )
                }
                
                summaryResult.onSuccess { result ->
                    memoryRepository?.saveSummary(result.summary)
                    
                    // Очищаем pending messages т.к. summary создан
                    preferencesRepository?.clearPendingUserMessages()
                    
                    // Обновляем UI
                    _uiState.update { 
                        it.copy(
                            memoryState = it.memoryState.copy(
                                hasSummary = true,
                                summaryPreview = result.summary.take(100) + "...",
                                fullSummaryText = result.summary
                            )
                        ) 
                    }
                }.onFailure { error ->
                    Log.e("ChatViewModel", "Failed to save dialog summary: ${error.message}")
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error saving dialog summary: ${e.message}")
            }
        }
    }
    
    private fun updateTemperature(temperature: Double) {
        _uiState.update { it.copy(temperature = temperature) }
        // Сохраняем в DataStore
        viewModelScope.launch {
            preferencesRepository?.saveTemperature(temperature)
        }
    }
    
    private fun updateMaxTokens(maxTokens: Int) {
        _uiState.update { it.copy(maxTokens = maxTokens) }
        // Сохраняем в DataStore
        viewModelScope.launch {
            preferencesRepository?.saveMaxTokens(maxTokens)
        }
    }
    
    private fun updateApiKey(apiKey: String) {
        _uiState.update { it.copy(apiKey = apiKey) }
        // Сохраняем в DataStore
        viewModelScope.launch {
            preferencesRepository?.saveClaudeApiKey(apiKey)
        }
    }
    
    private fun updateYandexApiKey(apiKey: String) {
        _uiState.update { it.copy(yandexApiKey = apiKey) }
        // Сохраняем в DataStore
        viewModelScope.launch {
            preferencesRepository?.saveYandexApiKey(apiKey)
        }
    }
    
    private fun updateYandexFolderId(folderId: String) {
        _uiState.update { it.copy(yandexFolderId = folderId) }
        // Сохраняем в DataStore
        viewModelScope.launch {
            preferencesRepository?.saveYandexFolderId(folderId)
        }
    }
    
    private fun updateTodoistToken(token: String) {
        _uiState.update { it.copy(todoistToken = token) }
        // Сохраняем в DataStore
        viewModelScope.launch {
            preferencesRepository?.saveTodoistToken(token)
            
            // Отправляем токен на встроенный MCP сервер
            if (token.isNotBlank()) {
                try {
                    // Используем встроенный сервер напрямую
                    com.test.chatbot.ChatBotApplication.mcpServer.setTodoistToken(token)
                    Log.e("ChatViewModel", "✅ Todoist token set in embedded server")
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "❌ Failed to set Todoist token: ${e.message}")
                }
            }
        }
    }
    
    private fun updateProvider(provider: AiProvider) {
        _uiState.update { it.copy(selectedProvider = provider) }
        // Сохраняем в DataStore
        viewModelScope.launch {
            preferencesRepository?.saveSelectedProvider(provider.name)
        }
    }
    
    private fun showApiKeyDialog() {
        _uiState.update { it.copy(showApiKeyDialog = true) }
    }
    
    private fun dismissApiKeyDialog() {
        _uiState.update { it.copy(showApiKeyDialog = false) }
    }
    
    private fun showSettingsDialog() {
        _uiState.update { it.copy(showSettingsDialog = true) }
    }
    
    private fun dismissSettingsDialog() {
        _uiState.update { it.copy(showSettingsDialog = false) }
    }
    
    private fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
    
    // ===== Сравнение моделей =====
    
    private fun compareModels(query: String) {
        if (query.isBlank()) return
        
        val claudeKey = _uiState.value.apiKey
        val yandexKey = _uiState.value.yandexApiKey
        val yandexFolder = _uiState.value.yandexFolderId
        
        if (claudeKey.isBlank()) {
            _uiState.update { it.copy(error = "Введите Claude API ключ для сравнения") }
            return
        }
        
        _uiState.update { it.copy(isComparing = true, comparisonResult = null) }
        
        viewModelScope.launch {
            try {
                val result = repository.compareModels(
                    query = query,
                    claudeApiKey = claudeKey,
                    yandexApiKey = yandexKey,
                    yandexFolderId = yandexFolder,
                    temperature = _uiState.value.temperature
                )
                
                _uiState.update { 
                    it.copy(
                        isComparing = false,
                        comparisonResult = result
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isComparing = false,
                        error = "Ошибка сравнения: ${e.message}"
                    ) 
                }
            }
        }
    }
    
    private fun showComparisonDialog() {
        _uiState.update { it.copy(showComparisonDialog = true) }
    }
    
    private fun dismissComparisonDialog() {
        _uiState.update { it.copy(showComparisonDialog = false) }
    }
    
    private fun clearComparisonResult() {
        _uiState.update { it.copy(comparisonResult = null) }
    }
    
    // ===== Компрессия диалога =====
    
    private fun toggleCompression(enabled: Boolean) {
        _uiState.update { 
            it.copy(
                compressionSettings = it.compressionSettings.copy(enabled = enabled),
                compressionState = it.compressionState.copy(isEnabled = enabled)
            ) 
        }
    }
    
    private fun updateCompressionThreshold(threshold: Int) {
        _uiState.update { 
            it.copy(compressionSettings = it.compressionSettings.copy(threshold = threshold)) 
        }
    }
    
    private fun showCompressionInfo() {
        _uiState.update { it.copy(showCompressionInfo = true) }
    }
    
    private fun dismissCompressionInfo() {
        _uiState.update { it.copy(showCompressionInfo = false) }
    }
    
    /**
     * Проверка и выполнение автоматической компрессии
     */
    private suspend fun checkAndPerformAutoCompression() {
        val settings = _uiState.value.compressionSettings
        
        if (!settings.enabled) return
        
        // Проверяем порог сообщений
        val historySize = when (_uiState.value.selectedProvider) {
            AiProvider.CLAUDE -> claudeHistory.size
            AiProvider.YANDEX_GPT -> yandexHistory.filter { it.role != "system" }.size
        }
        
        if (historySize >= settings.threshold) {
            performCompression()
        }
    }
    
    /**
     * Ручная компрессия
     */
    private fun manualCompress() {
        viewModelScope.launch {
            performCompression()
        }
    }
    
    /**
     * Выполнение компрессии диалога
     */
    private suspend fun performCompression() {
        val settings = _uiState.value.compressionSettings
        val provider = _uiState.value.selectedProvider
        
        _uiState.update { it.copy(isCompressing = true) }
        
        try {
            when (provider) {
                AiProvider.CLAUDE -> compressClaudeHistory(settings)
                AiProvider.YANDEX_GPT -> compressYandexHistory(settings)
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Compression error: ${e.message}")
            _uiState.update { 
                it.copy(
                    isCompressing = false,
                    error = "Ошибка компрессии: ${e.message}"
                ) 
            }
        }
    }
    
    /**
     * Компрессия истории Claude
     */
    private suspend fun compressClaudeHistory(settings: CompressionSettings) {
        if (claudeHistory.size < settings.keepRecentMessages + 2) {
            _uiState.update { it.copy(isCompressing = false) }
            return
        }
        
        // Определяем сообщения для суммаризации (все кроме последних N)
        val messagesToSummarize = claudeHistory.dropLast(settings.keepRecentMessages)
        val recentMessages = claudeHistory.takeLast(settings.keepRecentMessages)
        
        val result = repository.summarizeClaudeHistory(
            _uiState.value.apiKey,
            messagesToSummarize
        )
        
        result.onSuccess { compressionResult ->
            // Сохраняем summary
            currentSummary = compressionResult.summary
            
            // Сохраняем summary в долговременную память (если включена)
            saveSummaryToMemory(compressionResult.summary)
            
            // Очищаем историю и добавляем summary как первое сообщение + последние сообщения
            claudeHistory.clear()
            claudeHistory.add(ClaudeMessage(
                role = "user",
                content = "КОНТЕКСТ ПРЕДЫДУЩЕГО РАЗГОВОРА:\n${compressionResult.summary}\n\n---\nПродолжаем разговор с учётом контекста выше."
            ))
            claudeHistory.add(ClaudeMessage(
                role = "assistant",
                content = "Понял. Я учитываю контекст предыдущего разговора и готов продолжить."
            ))
            claudeHistory.addAll(recentMessages)
            
            // Обновляем статистику компрессии
            updateCompressionStats(compressionResult)
            
            // Сбрасываем счетчик
            messagesSinceCompression = 0
        }.onFailure { error ->
            _uiState.update { 
                it.copy(
                    isCompressing = false,
                    error = "Ошибка компрессии Claude: ${error.message}"
                ) 
            }
        }
    }
    
    /**
     * Компрессия истории YandexGPT
     */
    private suspend fun compressYandexHistory(settings: CompressionSettings) {
        val nonSystemMessages = yandexHistory.filter { it.role != "system" }
        if (nonSystemMessages.size < settings.keepRecentMessages + 2) {
            _uiState.update { it.copy(isCompressing = false) }
            return
        }
        
        // Сохраняем системное сообщение
        val systemMessage = yandexHistory.find { it.role == "system" }
        
        // Определяем сообщения для суммаризации
        val messagesToSummarize = nonSystemMessages.dropLast(settings.keepRecentMessages)
        val recentMessages = nonSystemMessages.takeLast(settings.keepRecentMessages)
        
        val result = repository.summarizeYandexHistory(
            _uiState.value.yandexApiKey,
            _uiState.value.yandexFolderId,
            messagesToSummarize
        )
        
        result.onSuccess { compressionResult ->
            // Сохраняем summary
            currentSummary = compressionResult.summary
            
            // Сохраняем summary в долговременную память (если включена)
            saveSummaryToMemory(compressionResult.summary)
            
            // Очищаем историю и строим новую
            yandexHistory.clear()
            
            // Добавляем системное сообщение с контекстом
            yandexHistory.add(YandexGptMessage(
                role = "system",
                text = (systemMessage?.text ?: "Ты — универсальный ИИ-ассистент.") + 
                    "\n\nКОНТЕКСТ ПРЕДЫДУЩЕГО РАЗГОВОРА:\n${compressionResult.summary}"
            ))
            
            // Добавляем последние сообщения
            yandexHistory.addAll(recentMessages)
            
            // Обновляем статистику компрессии
            updateCompressionStats(compressionResult)
            
            // Сбрасываем счетчик
            messagesSinceCompression = 0
        }.onFailure { error ->
            _uiState.update { 
                it.copy(
                    isCompressing = false,
                    error = "Ошибка компрессии YandexGPT: ${error.message}"
                ) 
            }
        }
    }
    
    /**
     * Сохранение summary в долговременную память (при компрессии)
     */
    private fun saveSummaryToMemory(summary: String) {
        if (!_uiState.value.memoryState.isEnabled) return
        
        viewModelScope.launch {
            memoryRepository?.saveSummary(summary)
            _uiState.update { 
                it.copy(
                    memoryState = it.memoryState.copy(
                        hasSummary = true,
                        summaryPreview = summary.take(100) + "...",
                        fullSummaryText = summary
                    )
                ) 
            }
        }
    }
    
    // ===== Долговременная память =====
    
    private fun toggleMemory(enabled: Boolean) {
        _uiState.update { 
            it.copy(memoryState = it.memoryState.copy(isEnabled = enabled)) 
        }
        
        // Сохраняем состояние памяти
        viewModelScope.launch {
            preferencesRepository?.saveMemoryEnabled(enabled)
        }
    }
    
    private fun clearAllMemories() {
        viewModelScope.launch {
            memoryRepository?.clearSummary()
            preferencesRepository?.clearPendingUserMessages()
            previousDialogSummary = null
            _uiState.update { 
                it.copy(
                    memoryState = it.memoryState.copy(
                        hasSummary = false,
                        summaryPreview = "",
                        fullSummaryText = ""
                    )
                ) 
            }
        }
    }
    
    private fun showMemoryDialog() {
        _uiState.update { it.copy(showMemoryDialog = true) }
    }
    
    private fun dismissMemoryDialog() {
        _uiState.update { it.copy(showMemoryDialog = false) }
    }
    
    private fun showAiFeaturesSheet() {
        _uiState.update { it.copy(showAiFeaturesSheet = true) }
    }
    
    private fun dismissAiFeaturesSheet() {
        _uiState.update { it.copy(showAiFeaturesSheet = false) }
    }
    
    /**
     * Получить контекст памяти для агента
     * Возвращает summary предыдущего диалога если он есть
     */
    private suspend fun getMemoryContext(): String {
        if (!_uiState.value.memoryState.isEnabled) return ""
        
        // Используем загруженный summary или получаем из репозитория
        val summary = previousDialogSummary ?: memoryRepository?.getSavedSummary()
        
        if (summary.isNullOrBlank()) return ""
        
        return buildString {
            appendLine("=== КОНТЕКСТ ПРЕДЫДУЩЕГО ДИАЛОГА ===")
            appendLine()
            appendLine(summary)
            appendLine()
            appendLine("=====================================")
            appendLine("Учитывай эту информацию о пользователе при ответах.")
        }
    }
    
    /**
     * Обновление статистики компрессии
     * 
     * Логика экономии:
     * - originalTokens: сколько токенов занимали сжатые сообщения
     * - compressedTokens: сколько токенов занимает summary
     * - savedPerRequest: экономия на КАЖДОМ следующем запросе
     */
    private fun updateCompressionStats(result: CompressionResult) {
        val currentState = _uiState.value.compressionState
        
        // Экономия на каждом следующем запросе
        val savedPerRequest = result.originalTokens - result.compressedTokens
        
        // Процент сжатия этой компрессии
        val compressionRatio = if (result.originalTokens > 0) {
            (result.compressedTokens.toFloat() / result.originalTokens * 100)
        } else 100f
        
        // Процент экономии
        val savingsPercent = 100f - compressionRatio
        
        _uiState.update { 
            it.copy(
                isCompressing = false,
                compressionState = CompressionState(
                    isEnabled = it.compressionSettings.enabled,
                    compressionCount = currentState.compressionCount + 1,
                    originalTokenCount = result.originalTokens, // Токенов ДО сжатия
                    compressedTokenCount = result.compressedTokens, // Токенов ПОСЛЕ сжатия
                    savedTokens = savedPerRequest, // Экономия на запрос
                    savingsPercent = savingsPercent,
                    hasSummary = true,
                    summaryPreview = result.summary.take(150) + if (result.summary.length > 150) "..." else "",
                    currentHistoryTokens = result.compressedTokens,
                    virtualHistoryTokens = currentState.virtualHistoryTokens + result.originalTokens,
                    totalSavedTokens = currentState.totalSavedTokens + savedPerRequest
                )
            )
        }
    }
    
    /**
     * Обработка MCP команд (/weather City)
     */
    private fun handleMcpCommand(command: String) {
        // Добавляем команду пользователя в UI
        val userMsg = Message(text = command, isUser = true)
        _uiState.update { it.copy(messages = it.messages + userMsg, isLoading = true) }
        
        viewModelScope.launch {
            try {
                // Подключаемся к MCP серверу
                if (mcpClient == null) {
                    val serverUrl = _uiState.value.mcpServerUrl
                    if (serverUrl.isBlank()) {
                        addBotMessage("❌ Не указан URL MCP сервера. Подключитесь через меню.")
                        _uiState.update { it.copy(isLoading = false) }
                        return@launch
                    }
                    
                    mcpClient = com.test.chatbot.mcp.McpClient.createHttpClient(serverUrl)
                    
                    // Инициализация
                    mcpClient?.initialize()?.onFailure {
                        addBotMessage("❌ Ошибка подключения к MCP: ${it.message}")
                        _uiState.update { it.copy(isLoading = false) }
                        return@launch
                    }
                }
                
                // Парсинг команды
                val parts = command.trim().split(" ", limit = 3)
                val mainCommand = parts[0].removePrefix("/")
                
                when (mainCommand) {
                    "weather" -> {
                        val city = parts.getOrNull(1)?.trim() ?: ""
                        if (city.isBlank()) {
                            addBotMessage("❌ Укажите название города: /weather Москва")
                            _uiState.update { it.copy(isLoading = false) }
                            return@launch
                        }
                        handleWeatherCommand(city)
                    }
                    
                    "task" -> {
                        val subCommand = parts.getOrNull(1)?.trim() ?: ""
                        val args = parts.getOrNull(2)?.trim() ?: ""
                        handleTaskCommand(subCommand, args)
                    }
                    
                    "summary" -> {
                        handleSummaryCommand()
                    }
                    
                    "sync" -> {
                        handleSyncCommand()
                    }
                    
                    "pipeline" -> {
                        val searchQuery = parts.drop(1).joinToString(" ").trim()
                        if (searchQuery.isBlank()) {
                            addBotMessage("❌ Укажите запрос для поиска: /pipeline найди статьи о квантовых компьютерах")
                            _uiState.update { it.copy(isLoading = false) }
                            return@launch
                        }
                        handlePipelineCommand(searchQuery)
                    }
                    
                    "files" -> {
                        handleFilesCommand()
                    }
                    
                    // ADB команды
                    "screenshot" -> {
                        handleScreenshotCommand()
                    }
                    
                    "logs" -> {
                        val lines = parts.getOrNull(1)?.toIntOrNull() ?: 100
                        handleLogsCommand(lines)
                    }
                    
                    "device" -> {
                        handleDeviceInfoCommand()
                    }
                    
                    "apps" -> {
                        val limit = parts.getOrNull(1)?.toIntOrNull() ?: 20
                        handleListAppsCommand(limit)
                    }
                    
                    "start" -> {
                        val packageName = parts.drop(1).joinToString(" ").trim()
                        if (packageName.isBlank()) {
                            addBotMessage("❌ Укажите имя пакета: /start com.example.app")
                            _uiState.update { it.copy(isLoading = false) }
                            return@launch
                        }
                        handleStartAppCommand(packageName)
                    }
                    
                    // System Monitor команды
                    "monitor", "system" -> {
                        handleSystemMonitorCommand()
                    }
                    
                    "battery" -> {
                        handleBatteryCommand()
                    }
                    
                    "memory" -> {
                        handleMemoryCommand()
                    }
                    
                    "cpu" -> {
                        handleCpuCommand()
                    }
                    
                    "network" -> {
                        handleNetworkCommand()
                    }
                    
                    "storage" -> {
                        handleStorageCommand()
                    }
                    
                    // File Manager команды
                    "fm", "filemanager" -> {
                        val subcommand = parts.getOrNull(1) ?: ""
                        handleFileManagerCommand(subcommand, parts.drop(2))
                    }
                    
                    // Script Automation команды
                    "scripts" -> {
                        handleScriptsListCommand()
                    }
                    
                    // Termux команды
                    "termux" -> {
                        val subcommand = parts.getOrNull(1) ?: "info"
                        if (subcommand == "info") {
                            handleTermuxInfoCommand()
                        } else {
                            val command = parts.drop(1).joinToString(" ").trim()
                            handleTermuxExecuteCommand(command)
                        }
                    }
                    
                    // ADB WiFi команды
                    "wifi", "remote" -> {
                        handleAdbWifiCommand()
                    }
                    
                    "ssh" -> {
                        handleSshInfoCommand()
                    }
                    
                    // RAG (Vector Search) команды
                    "index" -> {
                        val subcommand = parts.getOrNull(1) ?: ""
                        handleIndexCommand(subcommand, parts.drop(2))
                    }
                    
                    "search", "find" -> {
                        val query = parts.drop(1).joinToString(" ").trim()
                        if (query.isBlank()) {
                            addBotMessage("❌ Укажите запрос: /search <ваш запрос>")
                            _uiState.update { it.copy(isLoading = false) }
                            return@launch
                        }
                        handleSemanticSearchCommand(query)
                    }
                    
                    "docs" -> {
                        handleListDocsCommand()
                    }
                    
                    // Ollama команды
                    "ollama" -> {
                        val subcommand = parts.getOrNull(1) ?: "status"
                        handleOllamaCommand(subcommand, parts.drop(2))
                    }
                    
                    "ask", "rag" -> {
                        val question = parts.drop(1).joinToString(" ").trim()
                        if (question.isBlank()) {
                            addBotMessage("❌ Укажите вопрос: /ask <ваш вопрос>")
                            _uiState.update { it.copy(isLoading = false) }
                            return@launch
                        }
                        handleRAGQueryCommand(question)
                    }
                    
                    "compare" -> {
                        val question = parts.drop(1).joinToString(" ").trim()
                        if (question.isBlank()) {
                            addBotMessage("❌ Укажите вопрос: /compare <ваш вопрос>")
                            _uiState.update { it.copy(isLoading = false) }
                            return@launch
                        }
                        handleCompareRAGCommand(question)
                    }
                    
                    "filter" -> {
                        val question = parts.drop(1).joinToString(" ").trim()
                        if (question.isBlank()) {
                            addBotMessage("❌ Укажите вопрос: /filter <ваш вопрос>")
                            _uiState.update { it.copy(isLoading = false) }
                            return@launch
                        }
                        handleCompareFilteringCommand(question)
                    }
                    
                    "help" -> {
                        val topic = parts.drop(1).joinToString(" ").trim()
                        if (topic.isBlank()) {
                            addBotMessage(getHelpMessage())
                            _uiState.update { it.copy(isLoading = false) }
                        } else {
                            handleHelpTopicCommand(topic)
                        }
                    }
                    
                    // Project & Git Integration
                    "project" -> {
                        val subCommand = parts.getOrNull(1)?.trim() ?: "info"
                        handleProjectCommand(subCommand)
                    }
                    
                    "git" -> {
                        val subCommand = parts.getOrNull(1)?.trim() ?: "status"
                        val args = parts.drop(2).joinToString(" ").trim()
                        handleGitCommand(subCommand, args)
                    }
                    
                    // Support & CRM
                    "support" -> {
                        val subCommand = parts.getOrNull(1)?.trim() ?: ""
                        val args = parts.drop(2).joinToString(" ").trim()
                        handleSupportCommand(subCommand, args)
                    }
                    
                    // Team Assistant Commands - интегрированный командный ассистент
                    "tasks" -> {
                        val priority = parts.getOrNull(1)?.trim() ?: "all"
                        handleTasksListCommand(priority)
                    }
                    
                    "create_task", "add_task" -> {
                        val description = parts.drop(1).joinToString(" ").trim()
                        if (description.isBlank()) {
                            addBotMessage("❌ Укажите описание задачи: /create_task Реализовать авторизацию")
                            _uiState.update { it.copy(isLoading = false) }
                            return@launch
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
                        val taskId = parts.getOrNull(1)?.toIntOrNull()
                        if (taskId == null) {
                            addBotMessage("❌ Укажите ID задачи: /complete_task 5")
                            _uiState.update { it.copy(isLoading = false) }
                            return@launch
                        }
                        handleCompleteTaskCommand(taskId)
                    }
                    
                    else -> {
                        addBotMessage(getHelpMessage())
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
                
            } catch (e: Exception) {
                addBotMessage("❌ Ошибка: ${e.message}")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    private suspend fun handleWeatherCommand(city: String) {
        val result = mcpClient?.callTool("get_weather", mapOf("city" to city))
        
        result?.onSuccess { toolResult ->
            val weatherText = toolResult.content.firstOrNull()?.text ?: "Нет данных"
            
            val aiPrompt = "Пользователь спросил про погоду в городе $city. Вот данные от MCP инструмента:\n\n$weatherText\n\nОтветь пользователю о погоде обычными словами. ОБЯЗАТЕЛЬНО начни ответ с префикса '🔧 [MCP] ' чтобы показать что данные получены через MCP инструмент."
            
            sendToAi(aiPrompt)
        }?.onFailure {
            addBotMessage("❌ Ошибка вызова инструмента: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    private suspend fun handleTaskCommand(subCommand: String, args: String) {
        when (subCommand) {
            "add" -> {
                if (args.isBlank()) {
                    addBotMessage("❌ Укажите название задачи: /task add Купить молоко")
                    _uiState.update { it.copy(isLoading = false) }
                    return
                }
                
                val result = mcpClient?.callTool("add_task", mapOf("title" to args))
                result?.onSuccess { toolResult ->
                    val resultText = toolResult.content.firstOrNull()?.text ?: "Задача добавлена"
                    val aiPrompt = "Пользователь добавил задачу. Результат от MCP:\n\n$resultText\n\nПодтверди добавление задачи пользователю. ОБЯЗАТЕЛЬНО начни ответ с префикса '🔧 [MCP] '"
                    sendToAi(aiPrompt)
                }?.onFailure {
                    addBotMessage("❌ Ошибка: ${it.message}")
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
            
            "list" -> {
                // Автоматическая синхронизация перед получением списка
                mcpClient?.callTool("sync_todoist", emptyMap())
                
                val status = if (args.isNotBlank()) args else null
                val params = if (status != null) mapOf("status" to status) else emptyMap()
                
                val result = mcpClient?.callTool("list_tasks", params)
                result?.onSuccess { toolResult ->
                    val taskList = toolResult.content.firstOrNull()?.text ?: "Нет задач"
                    val aiPrompt = "Пользователь запросил список задач. Данные от MCP:\n\n$taskList\n\nПокажи список задач пользователю. ОБЯЗАТЕЛЬНО начни ответ с префикса '🔧 [MCP] '"
                    sendToAi(aiPrompt)
                }?.onFailure {
                    addBotMessage("❌ Ошибка: ${it.message}")
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
            
            "complete" -> {
                if (args.isBlank()) {
                    addBotMessage("❌ Укажите ID задачи: /task complete 1")
                    _uiState.update { it.copy(isLoading = false) }
                    return
                }
                
                val taskId = args.toIntOrNull()
                if (taskId == null) {
                    addBotMessage("❌ ID задачи должен быть числом")
                    _uiState.update { it.copy(isLoading = false) }
                    return
                }
                
                val result = mcpClient?.callTool("complete_task", mapOf("task_id" to taskId))
                result?.onSuccess { toolResult ->
                    val resultText = toolResult.content.firstOrNull()?.text ?: "Задача завершена"
                    val aiPrompt = "Пользователь завершил задачу #$taskId. Результат от MCP:\n\n$resultText\n\nПоздравь пользователя с выполнением задачи. ОБЯЗАТЕЛЬНО начни ответ с префикса '🔧 [MCP] '"
                    sendToAi(aiPrompt)
                }?.onFailure {
                    addBotMessage("❌ Ошибка: ${it.message}")
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
            
            else -> {
                addBotMessage("❌ Команды /task: add, list, complete\nПример: /task add Купить продукты")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    private suspend fun handleSummaryCommand() {
        // Автоматическая синхронизация перед получением summary
        mcpClient?.callTool("sync_todoist", emptyMap())
        
        val result = mcpClient?.callTool("get_summary", emptyMap())
        
        result?.onSuccess { toolResult ->
            val summaryText = toolResult.content.firstOrNull()?.text ?: "Нет данных"
            val aiPrompt = "Пользователь запросил сводку задач за сегодня. Данные от MCP:\n\n$summaryText\n\nПокажи сводку пользователю. ОБЯЗАТЕЛЬНО начни ответ с префикса '🔧 [MCP] '"
            sendToAi(aiPrompt)
        }?.onFailure {
            addBotMessage("❌ Ошибка: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    private suspend fun handleSyncCommand() {
        val result = mcpClient?.callTool("sync_todoist", emptyMap())
        
        result?.onSuccess { toolResult ->
            // Короткое сообщение об успешной синхронизации
            addBotMessage("🔧 [MCP] Синхронизация [Todoist] завершена успешно.")
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    private suspend fun handleFilesCommand() {
        val result = mcpClient?.callTool("list_files", emptyMap())
        
        result?.onSuccess { toolResult ->
            val filesText = toolResult.content.firstOrNull()?.text ?: "Нет файлов"
            addBotMessage("📁 Сохранённые файлы:\n\n$filesText")
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка получения списка файлов: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    private suspend fun handlePipelineCommand(searchQuery: String) {
        val result = mcpClient?.callTool("run_pipeline", mapOf(
            "search_query" to searchQuery,
            "summary_prompt" to "Создай краткую выжимку из найденных статей",
            "api_key" to _uiState.value.apiKey  // Передаём API ключ для AI суммаризации
        ))
        
        result?.onSuccess { toolResult ->
            val pipelineText = toolResult.content.firstOrNull()?.text ?: "Пайплайн завершён"
            val lines = pipelineText.lines()
            
            Log.e("ChatViewModel", "Pipeline result:\n$pipelineText")
            
            // Парсим JSON результат для получения searchResults
            val pipelineResult = try {
                com.google.gson.Gson().fromJson(pipelineText, com.test.chatbot.mcp.server.PipelineResult::class.java)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to parse pipeline result as JSON: ${e.message}")
                Log.e("ChatViewModel", "Pipeline text was: $pipelineText")
                null
            }
            
            if (pipelineResult != null) {
                Log.i("ChatViewModel", "Pipeline result parsed successfully")
                Log.i("ChatViewModel", "Search results count: ${pipelineResult.searchResults?.size ?: 0}")
                pipelineResult.searchResults?.forEach {
                    Log.i("ChatViewModel", "  - ${it.title}: ${it.url}")
                }
            }
            
            val finalMessage = buildString {
                // Показываем источники из searchResults
                val searchResults = pipelineResult?.searchResults
                if (searchResults != null && searchResults.isNotEmpty()) {
                    append("📚 Источники:\n")
                    searchResults.forEachIndexed { index, result ->
                        // URL уже декодирован в PipelineAgent
                        val fullUrl = if (!result.url.startsWith("http://") && !result.url.startsWith("https://")) {
                            "https://${result.url}"
                        } else {
                            result.url
                        }
                        
                        append("${index + 1}. ${result.title}\n")
                        // MessageTextWithLinks автоматически извлечёт домен и сделает его кликабельным
                        append("$fullUrl\n\n")
                    }
                }
                
                // Показываем суммаризацию
                val summaryText = pipelineResult?.summaryText
                if (summaryText != null && summaryText.isNotBlank()) {
                    append("📝 Выжимка:\n$summaryText\n\n")
                }
                
                // Показываем путь к файлу
                val filePath = pipelineResult?.finalResult
                if (filePath != null) {
                    Log.e("ChatViewModel", "File saved at: $filePath")
                    append("📁 [FILE:$filePath]Результат[/FILE] сохранён локально\n\n")
                }
                
                // Извлекаем информацию о задаче в Todoist из steps
                val todoistStep = pipelineResult?.steps?.find { it.name == "create_todoist_task" }
                if (todoistStep != null) {
                    if (todoistStep.status == "completed") {
                        append("✅ Задача создана в Todoist")
                    } else if (todoistStep.status == "failed") {
                        append("⚠️ Задача в Todoist не создана")
                    }
                }
            }
            
            if (finalMessage.isNotBlank()) {
                Log.e("ChatViewModel", "Final message to display:\n$finalMessage")
                addBotMessage(finalMessage.trim())
            } else {
                Log.e("ChatViewModel", "Final message is blank!")
                Log.e("ChatViewModel", "Pipeline result: $pipelineResult")
            }
            
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка пайплайна: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    private suspend fun sendToAi(prompt: String) {
        when (_uiState.value.selectedProvider) {
            AiProvider.CLAUDE -> {
                claudeHistory.add(ClaudeMessage(role = "user", content = prompt))
                sendToClaude()
            }
            AiProvider.YANDEX_GPT -> {
                if (yandexHistory.isEmpty()) {
                    yandexHistory.add(YandexGptMessage(
                        role = "system",
                        text = "Ты — универсальный ИИ-ассистент. Отвечай на русском языке."
                    ))
                }
                yandexHistory.add(YandexGptMessage(role = "user", text = prompt))
                sendToYandexGpt()
            }
        }
    }
    
    // ==================== System Monitor Commands ====================
    
    private suspend fun handleSystemMonitorCommand() {
        val result = mcpClient?.callTool("system_info", emptyMap())
        
        result?.onSuccess { toolResult ->
            val systemInfo = toolResult.content.firstOrNull()?.text ?: "Информация недоступна"
            addBotMessage(systemInfo)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка получения системной информации: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    private suspend fun handleBatteryCommand() {
        val result = mcpClient?.callTool("battery_info", emptyMap())
        
        result?.onSuccess { toolResult ->
            val batteryInfo = toolResult.content.firstOrNull()?.text ?: "Информация недоступна"
            addBotMessage(batteryInfo)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка получения информации о батарее: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    private suspend fun handleMemoryCommand() {
        val result = mcpClient?.callTool("memory_info", emptyMap())
        
        result?.onSuccess { toolResult ->
            val memoryInfo = toolResult.content.firstOrNull()?.text ?: "Информация недоступна"
            addBotMessage(memoryInfo)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка получения информации о памяти: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    private suspend fun handleCpuCommand() {
        val result = mcpClient?.callTool("cpu_info", emptyMap())
        
        result?.onSuccess { toolResult ->
            val cpuInfo = toolResult.content.firstOrNull()?.text ?: "Информация недоступна"
            addBotMessage(cpuInfo)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка получения информации о процессоре: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    private suspend fun handleNetworkCommand() {
        val result = mcpClient?.callTool("network_info", emptyMap())
        
        result?.onSuccess { toolResult ->
            val networkInfo = toolResult.content.firstOrNull()?.text ?: "Информация недоступна"
            addBotMessage(networkInfo)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка получения сетевой информации: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    private suspend fun handleStorageCommand() {
        val result = mcpClient?.callTool("storage_info", emptyMap())
        
        result?.onSuccess { toolResult ->
            val storageInfo = toolResult.content.firstOrNull()?.text ?: "Информация недоступна"
            addBotMessage(storageInfo)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка получения информации о хранилище: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    // ==================== File Manager Commands ====================
    
    private suspend fun handleFileManagerCommand(subcommand: String, args: List<String>) {
        when (subcommand) {
            "list", "ls" -> {
                val path = args.joinToString(" ").trim()
                val result = mcpClient?.callTool("fm_list", if (path.isBlank()) emptyMap() else mapOf("path" to path))
                result?.onSuccess { toolResult ->
                    addBotMessage(toolResult.content.firstOrNull()?.text ?: "Пусто")
                    _uiState.update { it.copy(isLoading = false) }
                }?.onFailure {
                    addBotMessage("❌ Ошибка: ${it.message}")
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
            "read", "cat" -> {
                val path = args.joinToString(" ").trim()
                if (path.isBlank()) {
                    addBotMessage("❌ Укажите путь: /fm read <путь>")
                    _uiState.update { it.copy(isLoading = false) }
                    return
                }
                val result = mcpClient?.callTool("fm_read", mapOf("path" to path))
                result?.onSuccess { toolResult ->
                    addBotMessage(toolResult.content.firstOrNull()?.text ?: "Файл пустой")
                    _uiState.update { it.copy(isLoading = false) }
                }?.onFailure {
                    addBotMessage("❌ Ошибка: ${it.message}")
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
            "search", "find" -> {
                val pattern = args.joinToString(" ").trim()
                if (pattern.isBlank()) {
                    addBotMessage("❌ Укажите шаблон: /fm search <шаблон>")
                    _uiState.update { it.copy(isLoading = false) }
                    return
                }
                val result = mcpClient?.callTool("fm_search", mapOf("pattern" to pattern))
                result?.onSuccess { toolResult ->
                    addBotMessage(toolResult.content.firstOrNull()?.text ?: "Ничего не найдено")
                    _uiState.update { it.copy(isLoading = false) }
                }?.onFailure {
                    addBotMessage("❌ Ошибка: ${it.message}")
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
            else -> {
                addBotMessage("❌ Неизвестная подкоманда.\n\n" +
                    "Доступные команды:\n" +
                    "/fm list [путь] - список файлов\n" +
                    "/fm read <путь> - прочитать файл\n" +
                    "/fm search <шаблон> - найти файлы")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    // ==================== Script Automation Commands ====================
    
    private suspend fun handleScriptsListCommand() {
        val result = mcpClient?.callTool("script_list", emptyMap())
        
        result?.onSuccess { toolResult ->
            val scriptsList = toolResult.content.firstOrNull()?.text ?: "Нет скриптов"
            addBotMessage(scriptsList)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка получения списка скриптов: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    // ==================== Termux Commands ====================
    
    private suspend fun handleTermuxInfoCommand() {
        val result = mcpClient?.callTool("termux_info", emptyMap())
        
        result?.onSuccess { toolResult ->
            val termuxInfo = toolResult.content.firstOrNull()?.text ?: "Информация недоступна"
            addBotMessage(termuxInfo)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка получения информации о Termux: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    private suspend fun handleTermuxExecuteCommand(command: String) {
        if (command.isBlank()) {
            addBotMessage("❌ Укажите команду: /termux <команда>")
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        
        val result = mcpClient?.callTool("termux_command", mapOf("command" to command))
        
        result?.onSuccess { toolResult ->
            val output = toolResult.content.firstOrNull()?.text ?: "Команда отправлена"
            addBotMessage(output)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка выполнения команды: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    // ==================== ADB WiFi Commands ====================
    
    private suspend fun handleAdbWifiCommand() {
        val result = mcpClient?.callTool("adb_wifi_info", emptyMap())
        
        result?.onSuccess { toolResult ->
            val wifiInfo = toolResult.content.firstOrNull()?.text ?: "Информация недоступна"
            addBotMessage(wifiInfo)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка получения информации: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    private suspend fun handleSshInfoCommand() {
        val result = mcpClient?.callTool("ssh_info", emptyMap())
        
        result?.onSuccess { toolResult ->
            val sshInfo = toolResult.content.firstOrNull()?.text ?: "Информация недоступна"
            addBotMessage(sshInfo)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка получения информации: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    // ==================== Help ====================
    
    private fun getHelpMessage(): String {
        return """
            📚 ДОСТУПНЫЕ КОМАНДЫ
            
            📱 ОСНОВНЫЕ:
            /weather [город] - погода
            /task [add|list|complete] - задачи
            /summary - сводка задач
            /sync - синхронизация с Todoist
            
            🔍 ПОИСК И ПАЙПЛАЙНЫ:
            /pipeline [запрос] - автоматический поиск и анализ
            /files - список сохранённых файлов
            
            🛠️ ADB КОМАНДЫ:
            /screenshot - скриншот экрана
            /logs [N] - логи приложения
            /device - информация об устройстве
            /apps [N] - список приложений
            /start [пакет] - запустить приложение
            
            📊 МОНИТОРИНГ СИСТЕМЫ:
            /monitor, /system - полная информация о системе
            /battery - состояние батареи
            /memory - использование памяти
            /cpu - информация о процессоре
            /network - сетевое подключение
            /storage - хранилище
            
            📁 ФАЙЛОВЫЙ МЕНЕДЖЕР:
            /fm list [путь] - список файлов
            /fm read <путь> - прочитать файл
            /fm search <шаблон> - найти файлы
            
            🤖 АВТОМАТИЗАЦИЯ:
            /scripts - список скриптов
            
            💻 TERMUX:
            /termux - информация о Termux
            /termux <команда> - выполнить команду
            
            📡 УДАЛЁННОЕ УПРАВЛЕНИЕ:
            /wifi, /remote - ADB over WiFi
            /ssh - SSH через Termux
            
            🧠 ВЕКТОРНЫЙ ПОИСК (RAG):
            /index demo - проиндексировать демо-документы
            /index stats - статистика индекса
            /index list - список документов
            /index file <путь> - индексировать файл
            /index clear - очистить индекс
            /index reset - полный сброс БД
            /search <запрос> - семантический поиск
            /docs - список проиндексированных документов
            
            🦙 OLLAMA (AI):
            /ollama status - проверить Ollama
            /ollama config <url> - настроить URL
            
            💬 ГИБРИДНЫЙ РЕЖИМ (AUTO):
            ✨ Умный анализ каждого сообщения:
            
            1️⃣ ИСТОРИЯ ЧАТА
               - Личные вопросы ("меня зовут", "помнишь")
               - Контекстные вопросы ("а как", "подробнее")
            
            2️⃣ ДОКУМЕНТЫ (RAG)
               - Технические вопросы ("что такое Docker")
               - Автоматический поиск в базе знаний
               - Ответы с источниками
            
            3️⃣ API (YandexGPT/Claude)
               - Общие разговоры
               - Творческие задачи
               - Сложные вопросы
            
            📋 RAG КОМАНДЫ:
            /ask <вопрос> - явный RAG запрос
            /rag <вопрос> - альтернатива /ask
            
            🔬 АНАЛИТИКА:
            /compare <вопрос> - сравнить RAG vs No-RAG
            /filter <вопрос> - сравнить методы фильтрации
            
            📁 ПРОЕКТ:
            /project info - информация о проекте
            /project index - проиндексировать документацию
            /git status - статус Git репозитория
            /git search <запрос> - поиск в проекте
            
            🛟 ПОДДЕРЖКА ПОЛЬЗОВАТЕЛЕЙ:
            /support - справка по командам поддержки
            /support ask <вопрос> - задать вопрос (RAG + CRM)
            /support ticket <проблема> - создать тикет
            /support tickets - мои тикеты
            /support status <ID> - детали тикета
            /support search <запрос> - поиск тикетов
            /support stats - статистика поддержки
            /support user - моя информация
            
            🤝 КОМАНДНЫЙ АССИСТЕНТ (RAG + MCP + TODOIST):
            ✨ Интегрированное управление проектом:
            
            /tasks [all|high|medium|low] - показать задачи
            /create_task <описание> - создать новую задачу
            /complete_task <ID> - отметить задачу выполненной
            /project_status - полный статус проекта
            /recommend - рекомендации по приоритетам
            
            💡 Примеры:
            • "/tasks high" - задачи высокого приоритета
            • "/create_task СРОЧНО: Исправить баг авторизации"
            • "/project_status" - Git + Задачи + RAG анализ
            • "/recommend" - что делать в первую очередь
            
            /help - показать эту справку
        """.trimIndent()
    }
    
    // ==================== ADB Commands ====================
    
    private suspend fun handleScreenshotCommand() {
        val result = mcpClient?.callTool("screenshot", emptyMap())
        
        result?.onSuccess { toolResult ->
            val screenshotText = toolResult.content.firstOrNull()?.text ?: "Скриншот создан"
            addBotMessage(screenshotText)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка создания скриншота: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    private suspend fun handleLogsCommand(lines: Int) {
        val result = mcpClient?.callTool("get_logs", mapOf("lines" to lines))
        
        result?.onSuccess { toolResult ->
            val logsText = toolResult.content.firstOrNull()?.text ?: "Логи не найдены"
            addBotMessage(logsText)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка получения логов: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    private suspend fun handleDeviceInfoCommand() {
        val result = mcpClient?.callTool("device_info", emptyMap())
        
        result?.onSuccess { toolResult ->
            val deviceInfo = toolResult.content.firstOrNull()?.text ?: "Информация недоступна"
            addBotMessage(deviceInfo)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка получения информации: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    private suspend fun handleListAppsCommand(limit: Int) {
        val result = mcpClient?.callTool("list_apps", mapOf("limit" to limit))
        
        result?.onSuccess { toolResult ->
            val appsText = toolResult.content.firstOrNull()?.text ?: "Приложения не найдены"
            addBotMessage(appsText)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка получения списка приложений: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    private suspend fun handleStartAppCommand(packageName: String) {
        val result = mcpClient?.callTool("start_app", mapOf("package_name" to packageName))
        
        result?.onSuccess { toolResult ->
            val startText = toolResult.content.firstOrNull()?.text ?: "Приложение запущено"
            addBotMessage(startText)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка запуска приложения: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    // ==================== RAG (Vector Search) Commands ====================
    
    private suspend fun handleIndexCommand(subcommand: String, args: List<String>) {
        when (subcommand) {
            "stats" -> {
                val result = mcpClient?.callTool("index_stats", emptyMap())
                result?.onSuccess { toolResult ->
                    addBotMessage(toolResult.content.firstOrNull()?.text ?: "Статистика недоступна")
                    _uiState.update { it.copy(isLoading = false) }
                }?.onFailure {
                    addBotMessage("❌ Ошибка: ${it.message}")
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
            "list" -> {
                val result = mcpClient?.callTool("list_indexed_docs", emptyMap())
                result?.onSuccess { toolResult ->
                    addBotMessage(toolResult.content.firstOrNull()?.text ?: "Нет документов")
                    _uiState.update { it.copy(isLoading = false) }
                }?.onFailure {
                    addBotMessage("❌ Ошибка: ${it.message}")
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
            "file" -> {
                val filePath = args.joinToString(" ").trim()
                if (filePath.isBlank()) {
                    addBotMessage("❌ Укажите путь к файлу: /index file <путь>")
                    _uiState.update { it.copy(isLoading = false) }
                    return
                }
                
                val result = mcpClient?.callTool("index_file", mapOf("file_path" to filePath))
                result?.onSuccess { toolResult ->
                    addBotMessage(toolResult.content.firstOrNull()?.text ?: "Файл проиндексирован")
                    _uiState.update { it.copy(isLoading = false) }
                }?.onFailure {
                    addBotMessage("❌ Ошибка: ${it.message}")
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
            "clear" -> {
                val result = mcpClient?.callTool("clear_index", emptyMap())
                result?.onSuccess { toolResult ->
                    addBotMessage(toolResult.content.firstOrNull()?.text ?: "Индекс очищен")
                    _uiState.update { it.copy(isLoading = false) }
                }?.onFailure {
                    addBotMessage("❌ Ошибка: ${it.message}")
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
            "reset" -> {
                val result = mcpClient?.callTool("reset_database", emptyMap())
                result?.onSuccess { toolResult ->
                    addBotMessage(toolResult.content.firstOrNull()?.text ?: "База данных сброшена")
                    _uiState.update { it.copy(isLoading = false) }
                }?.onFailure {
                    addBotMessage("❌ Ошибка: ${it.message}")
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
            else -> {
                addBotMessage("❌ Неизвестная подкоманда.\n\n" +
                    "Доступные команды:\n" +
                    "/index demo - проиндексировать демо-документы\n" +
                    "/index stats - статистика индекса\n" +
                    "/index list - список документов\n" +
                    "/index file <путь> - индексировать файл\n" +
                    "/index clear - очистить индекс\n" +
                    "/index reset - полный сброс БД (если поиск не работает)")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    private suspend fun handleSemanticSearchCommand(query: String) {
        val result = mcpClient?.callTool("semantic_search", mapOf("query" to query, "top_k" to 5))
        
        result?.onSuccess { toolResult ->
            val searchText = toolResult.content.firstOrNull()?.text ?: "Ничего не найдено"
            addBotMessage(searchText)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка поиска: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    private suspend fun handleListDocsCommand() {
        val result = mcpClient?.callTool("list_indexed_docs", emptyMap())
        
        result?.onSuccess { toolResult ->
            val docsText = toolResult.content.firstOrNull()?.text ?: "Нет документов"
            addBotMessage(docsText)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка получения списка: ${it.message}")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    // ==================== Ollama Commands ====================
    
    private suspend fun handleOllamaCommand(subcommand: String, args: List<String>) {
        when (subcommand) {
            "status" -> {
                val result = mcpClient?.callTool("ollama_status", emptyMap())
                result?.onSuccess { toolResult ->
                    addBotMessage(toolResult.content.firstOrNull()?.text ?: "Статус недоступен")
                    _uiState.update { it.copy(isLoading = false) }
                }?.onFailure {
                    addBotMessage("❌ Ошибка: ${it.message}")
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
            "config", "configure" -> {
                val url = args.joinToString(" ").trim()
                if (url.isBlank()) {
                    addBotMessage("❌ Укажите URL: /ollama config http://192.168.1.100:11434")
                    _uiState.update { it.copy(isLoading = false) }
                    return
                }
                
                val result = mcpClient?.callTool("ollama_configure", mapOf("url" to url))
                result?.onSuccess { toolResult ->
                    addBotMessage(toolResult.content.firstOrNull()?.text ?: "Настроено")
                    _uiState.update { it.copy(isLoading = false) }
                }?.onFailure {
                    addBotMessage("❌ Ошибка: ${it.message}")
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
            else -> {
                addBotMessage("❌ Неизвестная подкоманда.\n\n" +
                    "Доступные команды:\n" +
                    "/ollama status - проверить статус\n" +
                    "/ollama config <url> - настроить URL")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    private suspend fun handleRAGQueryCommand(question: String) {
        addBotMessage("💬 Ищу информацию в базе знаний...\n⏱️ Подготовка ответа с источниками...")
        
        val result = mcpClient?.callTool("rag_query", mapOf("question" to question, "top_k" to 15))
        
        result?.onSuccess { toolResult ->
            val ragAnswer = toolResult.content.firstOrNull()?.text ?: "Нет ответа"
            addBotMessage(ragAnswer)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка RAG: ${it.message}\n\nПроверьте что Ollama доступна (/ollama status)")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    /**
     * Умный гибридный режим с анализом
     */
    private suspend fun handleRAGQueryAutomatic(question: String) {
        // 1. ПРОВЕРКА ИСТОРИИ: был ли уже такой вопрос?
        val similarInHistory = findSimilarInHistory(question)
        
        if (similarInHistory != null) {
            // Нашли похожий вопрос - отвечаем из истории
            addBotMessage(buildString {
                append("🧠 АНАЛИЗ ЗАПРОСА:\n")
                append("━━━━━━━━━━━━━━━━━━━━\n")
                append("📊 История чата: ✅ ДА\n")
                append("📚 Документы (RAG): ❌ НЕТ\n")
                append("🌐 API (LLM): ❌ НЕТ\n")
                append("━━━━━━━━━━━━━━━━━━━━\n\n")
                append("💾 Найден ответ в истории диалога!\n\n")
                append(similarInHistory)
            })
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        
        // 2. АНАЛИЗ: определяем тип запроса
        val analysis = analyzeMessageType(question)
        
        val analysisMessage = buildString {
            append("🧠 АНАЛИЗ ЗАПРОСА:\n")
            append("━━━━━━━━━━━━━━━━━━━━\n")
            append("📊 История чата: ${if (analysis.needsHistory) "✅ ДА" else "❌ НЕТ"}\n")
            append("📚 Документы (RAG): ${if (analysis.needsDocuments) "✅ ДА" else "❌ НЕТ"}\n")
            append("🌐 API (LLM): ${if (analysis.needsAPI) "✅ ДА" else "❌ НЕТ"}\n")
            append("━━━━━━━━━━━━━━━━━━━━\n\n")
            
            // Объяснение решения
            when {
                analysis.needsDocuments -> append("🔍 Технический вопрос → ищу в базе знаний")
                analysis.needsHistory && !analysis.needsDocuments -> append("💭 Личная информация → использую историю")
                analysis.needsAPI && !analysis.needsDocuments -> append("🤖 Общий разговор → использую AI")
                else -> append("⏱️ Готовлю ответ...")
            }
        }
        
        addBotMessage(analysisMessage)
        
        // 3. ИСТОРИЯ: собираем контекст диалога для справки
        val historyContext = getDialogHistoryContext()
        
        // 4. Выбор стратегии ответа
        when {
            // Вопрос "помнишь" / "как меня зовут" → ищем в истории
            analysis.needsHistory && (question.lowercase().contains("помнишь") || 
                                      question.lowercase().contains("как меня зовут") ||
                                      question.lowercase().contains("кто я")) -> {
                answerFromHistorySearch(question, historyContext)
            }
            // Технический вопрос → RAG
            analysis.needsDocuments -> {
                val documentContext = searchInDocuments(question)
                if (documentContext != null) {
                    answerFromRAG(question, historyContext, documentContext)
                } else {
                    answerFromAPI(question, historyContext)
                }
            }
            // Личная информация или общий разговор → API
            else -> {
                answerFromAPI(question, historyContext)
            }
        }
    }
    
    /**
     * Поиск похожего вопроса в истории (кэш ответов)
     */
    private fun findSimilarInHistory(question: String): String? {
        val questionLower = question.lowercase().trim()
        val messages = _uiState.value.messages
        
        // Ищем похожие вопросы (игнорируем служебные сообщения)
        for (i in messages.indices step 2) {
            if (i + 1 < messages.size) {
                val userMessage = messages[i]
                val botMessage = messages[i + 1]
                
                if (userMessage.isUser && !botMessage.isUser) {
                    val prevQuestion = userMessage.text.lowercase().trim()
                    
                    // Проверяем похожесть (простая проверка)
                    if (areSimilarQuestions(prevQuestion, questionLower)) {
                        // Убираем анализ и оставляем только ответ
                        return botMessage.text
                            .replace(Regex("🧠 АНАЛИЗ.*?━━━━━━━━━━━━━━━━━━━━\n\n.*?\n\n"), "")
                            .trim()
                    }
                }
            }
        }
        
        return null
    }
    
    /**
     * Проверка похожести вопросов
     */
    private fun areSimilarQuestions(q1: String, q2: String): Boolean {
        // Убираем знаки препинания
        val clean1 = q1.replace(Regex("[?!.,]"), "").trim()
        val clean2 = q2.replace(Regex("[?!.,]"), "").trim()
        
        // Точное совпадение
        if (clean1 == clean2) return true
        
        // Очень похожие (>=80% общих слов)
        val words1 = clean1.split(Regex("\\s+")).filter { it.length > 2 }.toSet()
        val words2 = clean2.split(Regex("\\s+")).filter { it.length > 2 }.toSet()
        
        if (words1.isEmpty() || words2.isEmpty()) return false
        
        val common = words1.intersect(words2).size
        val total = maxOf(words1.size, words2.size)
        
        return common.toFloat() / total >= 0.8f
    }
    
    /**
     * Поиск ответа в истории по ключевым словам
     */
    private suspend fun answerFromHistorySearch(question: String, history: String) {
        if (history.isEmpty()) {
            addBotMessage("❌ История диалога пуста.")
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        
        // Ищем ответ в истории через API с контекстом
        val prompt = "На основе истории диалога ответь на вопрос: $question$history"
        answerFromAPI(prompt, "")
    }
    
    /**
     * Анализ типа сообщения
     */
    private fun analyzeMessageType(message: String): MessageAnalysis {
        val messageLower = message.lowercase()
        
        // Ключевые слова для технических вопросов
        val techKeywords = listOf(
            "что такое", "как работает", "расскажи про", "объясни",
            "docker", "kotlin", "android", "нейрон", "квантов", "блокчейн",
            "rag", "машинное обучение", "api", "база данных", "типы", "применяется"
        )
        
        // Ключевые слова для личных вопросов/утверждений
        val personalKeywords = listOf(
            "меня зовут", "я живу", "мой любимый", "моя любимая", "я ел",
            "вчера", "сегодня", "завтра", "помнишь", "ты знаешь что я",
            "я смотрю", "я делаю", "мне нравится", "у меня", "мой", "моя"
        )
        
        // Ключевые слова для контекстных вопросов
        val contextKeywords = listOf(
            "а как", "а где", "также", "еще", "подробнее", "об этом"
        )
        
        val hasTechKeywords = techKeywords.any { messageLower.contains(it) }
        val hasPersonalKeywords = personalKeywords.any { messageLower.contains(it) }
        val hasContextKeywords = contextKeywords.any { messageLower.contains(it) }
        val isShortQuestion = message.length < 50 && message.contains("?")
        val hasQuestionWord = messageLower.startsWith("что") || messageLower.startsWith("как") || 
                              messageLower.startsWith("где") || messageLower.startsWith("когда")
        
        return MessageAnalysis(
            needsHistory = hasPersonalKeywords || hasContextKeywords || (isShortQuestion && !hasTechKeywords),
            needsDocuments = hasTechKeywords && !hasPersonalKeywords && hasQuestionWord,
            needsAPI = hasPersonalKeywords || (!hasTechKeywords && !hasContextKeywords) || !hasQuestionWord
        )
    }
    
    /**
     * Получить контекст истории диалога
     */
    private fun getDialogHistoryContext(): String {
        val recentMessages = _uiState.value.messages.takeLast(6).dropLast(1) // Последние 6, убираем текущее
        return if (recentMessages.isNotEmpty()) {
            "\n\nКонтекст из истории диалога:\n" + recentMessages.joinToString("\n") { msg ->
                val cleanText = msg.text
                    .replace(Regex("🧠 АНАЛИЗ.*?━━━━━━━━━━━━━━━━━━━━\n\n"), "") // Убираем анализ
                    .replace(Regex("📚 ИСТОЧНИКИ.*"), "") // Убираем источники
                    .take(200)
                if (msg.isUser) "Пользователь: $cleanText" else "Ассистент: $cleanText"
            }
        } else ""
    }
    
    /**
     * Поиск в документах
     */
    private suspend fun searchInDocuments(query: String): RAGContext? {
        return try {
            val result = mcpClient?.callTool("rag_query", mapOf(
                "question" to query,
                "top_k" to 15
            ))
            
            result?.getOrNull()?.content?.firstOrNull()?.text?.let { RAGContext(it) }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Ответ на основе истории
     */
    private suspend fun answerFromHistory(question: String, history: String) {
        // Просто передаем в API с историей - API сам разберется
        answerFromAPI(question, history)
    }
    
    /**
     * Ответ из RAG (документы + история)
     */
    private suspend fun answerFromRAG(question: String, history: String, ragContext: RAGContext) {
        addBotMessage(ragContext.answer)
        _uiState.update { it.copy(isLoading = false) }
    }
    
    /**
     * Ответ через API (с учетом истории)
     */
    private suspend fun answerFromAPI(question: String, history: String) {
        when (_uiState.value.selectedProvider) {
            AiProvider.CLAUDE -> {
                // Добавляем текущий вопрос (история уже в claudeHistory)
                claudeHistory.add(ClaudeMessage(role = "user", content = question))
                sendToClaude()
            }
            AiProvider.YANDEX_GPT -> {
                // Системное сообщение с инструкциями
                if (yandexHistory.isEmpty()) {
                    yandexHistory.add(YandexGptMessage(
                        role = "system",
                        text = "Ты — дружелюбный AI-ассистент. Отвечай на русском языке. " +
                               "Если пользователь делится личной информацией - запоминай её и используй в диалоге. " +
                               "Обращайся к пользователю по имени если он представился."
                    ))
                }
                
                // Добавляем вопрос с историей если нужно
                val messageText = if (history.isNotEmpty() && history.contains("Пользователь:")) {
                    "$history\n\nТекущий вопрос: $question"
                } else {
                    question
                }
                
                yandexHistory.add(YandexGptMessage(role = "user", text = messageText))
                sendToYandexGpt()
            }
        }
    }
    
    /**
     * Анализ сообщения
     */
    private data class MessageAnalysis(
        val needsHistory: Boolean,
        val needsDocuments: Boolean,
        val needsAPI: Boolean
    )
    
    /**
     * Контекст RAG
     */
    private data class RAGContext(val answer: String)
    
    private suspend fun handleCompareRAGCommand(question: String) {
        addBotMessage("🔬 Сравнение RAG vs No-RAG...\n\nЭто может занять 30-60 секунд.")
        
        val result = mcpClient?.callTool("compare_rag", mapOf(
            "question" to question,
            "top_k" to 10,
            "model" to "llama3"
        ))
        
        result?.onSuccess { toolResult ->
            val comparison = toolResult.content.firstOrNull()?.text ?: "Нет результата"
            addBotMessage(comparison)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка сравнения: ${it.message}\n\nПроверьте что Ollama доступна (/ollama status)")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    private suspend fun handleCompareFilteringCommand(question: String) {
        addBotMessage("🔬 Сравнение методов фильтрации...\n\n" +
            "Тестирую:\n" +
            "1️⃣ Без фильтра (15 документов, hybrid search)\n" +
            "2️⃣ С threshold фильтром (threshold=0.4, topK=12)\n" +
            "3️⃣ С LLM reranker (threshold=0.35, maxRerank=20, topK=15)\n\n" +
            "⏱️ Это может занять 60-90 секунд.")
        
        val result = mcpClient?.callTool("compare_filtering", mapOf(
            "question" to question,
            "model" to "llama3"
        ))
        
        result?.onSuccess { toolResult ->
            val comparison = toolResult.content.firstOrNull()?.text ?: "Нет результата"
            addBotMessage(comparison)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка сравнения фильтрации: ${it.message}\n\nПроверьте что Ollama доступна (/ollama status)")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    // ==================== Helper Methods ====================
    
    private fun addBotMessage(text: String) {
        val botMsg = Message(text = text, isUser = false)
        _uiState.update { it.copy(messages = it.messages + botMsg) }
    }
    
    // ==================== Project & Git Commands ====================
    
    private suspend fun handleHelpTopicCommand(topic: String) {
        val result = mcpClient?.callTool("project_help", mapOf("topic" to topic))
        
        result?.onSuccess { toolResult ->
            val help = toolResult.content.firstOrNull()?.text ?: "Нет информации"
            addBotMessage(help)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка: ${it.message}\n\n💡 Сначала проиндексируйте проект: /project index")
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    private suspend fun handleProjectCommand(subCommand: String) {
        when (subCommand) {
            "info" -> {
                val result = mcpClient?.callTool("project_info", emptyMap())
                result?.onSuccess { toolResult ->
                    val info = toolResult.content.firstOrNull()?.text ?: "Нет информации"
                    addBotMessage(info)
                    _uiState.update { it.copy(isLoading = false) }
                }?.onFailure {
                    addBotMessage("❌ Ошибка: ${it.message}")
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
            "index" -> {
                addBotMessage("📚 Индексация документации проекта...\n\nЭто может занять некоторое время.")
                val result = mcpClient?.callTool("project_index", emptyMap())
                result?.onSuccess { toolResult ->
                    val message = toolResult.content.firstOrNull()?.text ?: "Индексация завершена"
                    addBotMessage(message)
                    _uiState.update { it.copy(isLoading = false) }
                }?.onFailure {
                    addBotMessage("❌ Ошибка индексации: ${it.message}")
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
            "search" -> {
                addBotMessage("❌ Используйте: /project search <запрос>")
                _uiState.update { it.copy(isLoading = false) }
            }
            else -> {
                addBotMessage("📁 Команды /project:\n" +
                    "• /project info - информация о проекте\n" +
                    "• /project index - проиндексировать документацию\n" +
                    "• /project search <запрос> - поиск в документации")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    private suspend fun handleGitCommand(subCommand: String, args: String) {
        when (subCommand) {
            "status" -> {
                val result = mcpClient?.callTool("git_status", emptyMap())
                result?.onSuccess { toolResult ->
                    val status = toolResult.content.firstOrNull()?.text ?: "Нет данных"
                    addBotMessage(status)
                    _uiState.update { it.copy(isLoading = false) }
                }?.onFailure {
                    addBotMessage("❌ Ошибка: ${it.message}")
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
            "search" -> {
                if (args.isBlank()) {
                    addBotMessage("❌ Укажите поисковый запрос: /git search <запрос>")
                    _uiState.update { it.copy(isLoading = false) }
                    return
                }
                val result = mcpClient?.callTool("git_search", mapOf("query" to args))
                result?.onSuccess { toolResult ->
                    val results = toolResult.content.firstOrNull()?.text ?: "Ничего не найдено"
                    addBotMessage(results)
                    _uiState.update { it.copy(isLoading = false) }
                }?.onFailure {
                    addBotMessage("❌ Ошибка поиска: ${it.message}")
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
            else -> {
                addBotMessage("🌿 Команды /git:\n" +
                    "• /git status - статус репозитория\n" +
                    "• /git search <запрос> - поиск в файлах проекта")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    /**
     * Обработка команд поддержки
     */
    private suspend fun handleSupportCommand(subCommand: String, args: String) {
        when (subCommand) {
            "" -> {
                // /support без аргументов - показываем справку
                addBotMessage(
                    "🛟 **Система поддержки пользователей**\n\n" +
                    "**Команды:**\n" +
                    "• /support ask <вопрос> - задать вопрос (с RAG + CRM)\n" +
                    "• /support ticket <проблема> - создать тикет\n" +
                    "• /support tickets - мои тикеты\n" +
                    "• /support status <ID> - детали тикета\n" +
                    "• /support search <запрос> - поиск тикетов\n" +
                    "• /support stats - статистика поддержки\n" +
                    "• /support user - моя информация\n\n" +
                    "**Примеры:**\n" +
                    "• `/support ask Почему не работает авторизация?`\n" +
                    "• `/support ticket Не могу подключиться к Ollama серверу`\n" +
                    "• `/support status TICKET-001`"
                )
                _uiState.update { it.copy(isLoading = false) }
            }
            
            "ask" -> {
                // /support ask <вопрос> - ответить через RAG + CRM
                if (args.isBlank()) {
                    addBotMessage("❌ Укажите ваш вопрос: /support ask <вопрос>")
                    _uiState.update { it.copy(isLoading = false) }
                    return
                }
                
                val result = mcpClient?.callTool("support_answer", mapOf("question" to args))
                result?.onSuccess { toolResult ->
                    val answer = toolResult.content.firstOrNull()?.text ?: "Нет ответа"
                    addBotMessage(answer)
                    _uiState.update { it.copy(isLoading = false) }
                }?.onFailure {
                    addBotMessage("❌ Ошибка: ${it.message}")
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
            
            "ticket" -> {
                // /support ticket <описание> - создать новый тикет
                if (args.isBlank()) {
                    addBotMessage("❌ Опишите вашу проблему: /support ticket <описание>")
                    _uiState.update { it.copy(isLoading = false) }
                    return
                }
                
                // Определяем категорию и приоритет по ключевым словам
                val category = when {
                    args.contains("авториз", ignoreCase = true) || 
                    args.contains("вход", ignoreCase = true) || 
                    args.contains("ключ", ignoreCase = true) -> "authorization"
                    args.contains("rag", ignoreCase = true) || 
                    args.contains("документ", ignoreCase = true) || 
                    args.contains("поиск", ignoreCase = true) -> "rag"
                    args.contains("зависа", ignoreCase = true) || 
                    args.contains("медленн", ignoreCase = true) || 
                    args.contains("производит", ignoreCase = true) -> "performance"
                    args.contains("mcp", ignoreCase = true) || 
                    args.contains("git", ignoreCase = true) -> "mcp"
                    args.contains("функци", ignoreCase = true) || 
                    args.contains("добавить", ignoreCase = true) -> "feature_request"
                    else -> "general"
                }
                
                val priority = when {
                    args.contains("срочно", ignoreCase = true) || 
                    args.contains("важно", ignoreCase = true) || 
                    args.contains("критич", ignoreCase = true) -> "high"
                    args.contains("желательно", ignoreCase = true) || 
                    args.contains("предложение", ignoreCase = true) -> "low"
                    else -> "medium"
                }
                
                val params = mapOf(
                    "subject" to args.take(100),  // Первые 100 символов как тема
                    "description" to args,
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
            
            "tickets" -> {
                // /support tickets - показать все тикеты пользователя
                val result = mcpClient?.callTool("support_user_tickets", emptyMap())
                result?.onSuccess { toolResult ->
                    val tickets = toolResult.content.firstOrNull()?.text ?: "Нет тикетов"
                    addBotMessage(tickets)
                    _uiState.update { it.copy(isLoading = false) }
                }?.onFailure {
                    addBotMessage("❌ Ошибка: ${it.message}")
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
            
            "status" -> {
                // /support status <TICKET-ID> - показать детали тикета
                if (args.isBlank()) {
                    addBotMessage("❌ Укажите ID тикета: /support status TICKET-001")
                    _uiState.update { it.copy(isLoading = false) }
                    return
                }
                
                val result = mcpClient?.callTool("support_ticket_details", mapOf("ticket_id" to args))
                result?.onSuccess { toolResult ->
                    val details = toolResult.content.firstOrNull()?.text ?: "Тикет не найден"
                    addBotMessage(details)
                    _uiState.update { it.copy(isLoading = false) }
                }?.onFailure {
                    addBotMessage("❌ Ошибка: ${it.message}")
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
            
            "search" -> {
                // /support search <запрос> - поиск тикетов
                if (args.isBlank()) {
                    addBotMessage("❌ Укажите поисковый запрос: /support search <запрос>")
                    _uiState.update { it.copy(isLoading = false) }
                    return
                }
                
                val result = mcpClient?.callTool("support_search_tickets", mapOf("query" to args))
                result?.onSuccess { toolResult ->
                    val results = toolResult.content.firstOrNull()?.text ?: "Ничего не найдено"
                    addBotMessage(results)
                    _uiState.update { it.copy(isLoading = false) }
                }?.onFailure {
                    addBotMessage("❌ Ошибка поиска: ${it.message}")
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
            
            "stats" -> {
                // /support stats - статистика поддержки
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
            
            "user" -> {
                // /support user - информация о текущем пользователе
                val result = mcpClient?.callTool("support_user_info", emptyMap())
                result?.onSuccess { toolResult ->
                    val userInfo = toolResult.content.firstOrNull()?.text ?: "Нет данных"
                    addBotMessage(userInfo)
                    _uiState.update { it.copy(isLoading = false) }
                }?.onFailure {
                    addBotMessage("❌ Ошибка: ${it.message}")
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
            
            else -> {
                // Неизвестная подкоманда - показываем справку
                addBotMessage(
                    "❌ Неизвестная команда: /support $subCommand\n\n" +
                    "Используйте /support для списка команд"
                )
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    // ============================================
    // TEAM ASSISTANT - ИНТЕГРИРОВАННЫЙ КОМАНДНЫЙ АССИСТЕНТ
    // ============================================
    
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
        
        // Получаем список задач из MCP
        val status = if (priority == "all") null else "pending"
        val result = mcpClient?.callTool("list_tasks", mapOf("status" to (status ?: "")))
        
        result?.onSuccess { toolResult ->
            val tasksText = toolResult.content.firstOrNull()?.text ?: "Нет задач"
            
            // Парсим задачи и фильтруем по приоритету
            val filteredText = if (priority != "all" && priority in listOf("high", "medium", "low")) {
                filterTasksByPriority(tasksText, priority)
            } else {
                tasksText
            }
            
            addBotMessage(filteredText)
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
    private suspend fun handleCompleteTaskCommand(taskId: Int) {
        if (mcpClient == null) {
            addBotMessage("❌ MCP сервер недоступен")
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        
        val result = mcpClient?.callTool("complete_task", mapOf("task_id" to taskId))
        
        result?.onSuccess { toolResult ->
            val message = toolResult.content.firstOrNull()?.text ?: "Задача выполнена"
            addBotMessage(message)
            _uiState.update { it.copy(isLoading = false) }
        }?.onFailure {
            addBotMessage("❌ Ошибка: ${it.message}")
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
            when {
                line.contains("🔴 HIGH") -> {
                    val taskNum = tasks.size + 1
                    tasks.add(Pair(1, "   $taskNum. ${line.trim()} ⚠️"))
                }
                line.contains("🟡 MEDIUM") -> {
                    val taskNum = tasks.size + 1
                    tasks.add(Pair(2, "   $taskNum. ${line.trim()}"))
                }
                line.contains("🟢 LOW") -> {
                    val taskNum = tasks.size + 1
                    tasks.add(Pair(3, "   $taskNum. ${line.trim()} ⏸️"))
                }
            }
        }
        
        // Сортируем по приоритету
        val sorted = tasks.sortedBy { it.first }.map { it.second }
        
        return if (sorted.isNotEmpty()) {
            sorted.joinToString("\n")
        } else {
            "   Нет задач для приоритизации"
        }
    }
}
