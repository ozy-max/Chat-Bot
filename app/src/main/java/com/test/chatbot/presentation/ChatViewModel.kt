package com.test.chatbot.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.test.chatbot.data.PreferencesRepository
import com.test.chatbot.models.*
import com.test.chatbot.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository = ChatRepository(),
    private val preferencesRepository: PreferencesRepository? = null
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
    
    init {
        loadSavedSettings()
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
                            temperature = settings.temperature,
                            maxTokens = settings.maxTokens,
                            selectedProvider = provider,
                            // Не показываем диалог API ключей если ключи уже сохранены
                            showApiKeyDialog = settings.claudeApiKey.isBlank() && settings.yandexApiKey.isBlank(),
                            isSettingsLoaded = true
                        )
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
        }
    }
    
    private fun sendMessage(userMessage: String) {
        if (userMessage.isBlank()) return
        
        // Добавляем сообщение пользователя в UI
        val userMsg = Message(text = userMessage, isUser = true)
        _uiState.update { it.copy(messages = it.messages + userMsg) }
        
        // Добавляем в историю в зависимости от провайдера
        when (_uiState.value.selectedProvider) {
            AiProvider.CLAUDE -> {
                claudeHistory.add(ClaudeMessage(role = "user", content = userMessage))
            }
            AiProvider.YANDEX_GPT -> {
                // Добавляем системное сообщение если история пуста
                if (yandexHistory.isEmpty()) {
                    yandexHistory.add(YandexGptMessage(
                        role = "system",
                        text = "Ты — универсальный ИИ-ассистент. Отвечай на русском языке."
                    ))
                }
                yandexHistory.add(YandexGptMessage(role = "user", text = userMessage))
            }
        }
        
        // Увеличиваем счетчик сообщений
        messagesSinceCompression++
        totalOriginalTokens += repository.estimateTokens(userMessage)
        
        // Отправляем запрос
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                when (_uiState.value.selectedProvider) {
                    AiProvider.CLAUDE -> sendToClaude()
                    AiProvider.YANDEX_GPT -> sendToYandexGpt()
                }
                
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
        val result = repository.sendMessageToClaude(
            _uiState.value.apiKey,
            claudeHistory,
            _uiState.value.temperature,
            _uiState.value.maxTokens
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
        val result = repository.sendMessageToYandexGpt(
            _uiState.value.yandexApiKey,
            _uiState.value.yandexFolderId,
            yandexHistory,
            _uiState.value.temperature,
            _uiState.value.maxTokens
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
        claudeHistory.clear()
        yandexHistory.clear()
        
        // Сброс данных компрессии
        currentSummary = null
        messagesSinceCompression = 0
        totalOriginalTokens = 0
        
        _uiState.update { 
            it.copy(
                messages = emptyList(), 
                tokenStats = TokenStats(),
                compressionState = CompressionState(isEnabled = it.compressionSettings.enabled)
            ) 
        }
    }
    
    private fun updateTemperature(temperature: Double) {
        Log.d("ChatViewModel","updateTemperature: $temperature")
        _uiState.update { it.copy(temperature = temperature) }
        // Сохраняем в DataStore
        viewModelScope.launch {
            preferencesRepository?.saveTemperature(temperature)
        }
    }
    
    private fun updateMaxTokens(maxTokens: Int) {
        Log.d("ChatViewModel","updateMaxTokens: $maxTokens")
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
        Log.d("ChatViewModel", "Compression ${if (enabled) "enabled" else "disabled"}")
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
            
            Log.d("ChatViewModel", "Claude history compressed: ${compressionResult.originalTokens} → ${compressionResult.compressedTokens} tokens")
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
            
            Log.d("ChatViewModel", "Yandex history compressed: ${compressionResult.originalTokens} → ${compressionResult.compressedTokens} tokens")
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
        
        Log.d("ChatViewModel", """
            Compression stats:
            - Original: ${result.originalTokens} tokens (${result.originalMessages} messages)
            - Compressed: ${result.compressedTokens} tokens (summary)
            - Saved per request: $savedPerRequest tokens
            - Savings: ${savingsPercent.toInt()}%
        """.trimIndent())
    }
}
