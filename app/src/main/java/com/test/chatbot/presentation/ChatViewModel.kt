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
        
        // Отправляем запрос
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                when (_uiState.value.selectedProvider) {
                    AiProvider.CLAUDE -> sendToClaude()
                    AiProvider.YANDEX_GPT -> sendToYandexGpt()
                }
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
            _uiState.value.temperature
        )
        
        result.onSuccess { textResponse ->
            // Добавляем в историю
            claudeHistory.add(ClaudeMessage(role = "assistant", content = textResponse))
            
            // Показываем ответ
            val botMessage = Message(
                text = textResponse.ifEmpty { "Получен пустой ответ" },
                isUser = false
            )
            _uiState.update { 
                it.copy(
                    messages = it.messages + botMessage,
                    isLoading = false
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
            _uiState.value.temperature
        )
        
        result.onSuccess { textResponse ->
            // Добавляем в историю
            yandexHistory.add(YandexGptMessage(role = "assistant", text = textResponse))
            
            // Показываем ответ
            val botMessage = Message(
                text = textResponse.ifEmpty { "Получен пустой ответ" },
                isUser = false
            )
            _uiState.update { 
                it.copy(
                    messages = it.messages + botMessage,
                    isLoading = false
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
        _uiState.update { it.copy(messages = emptyList()) }
    }
    
    private fun updateTemperature(temperature: Double) {
        Log.d("ChatViewModel","updateTemperature: $temperature")
        _uiState.update { it.copy(temperature = temperature) }
        // Сохраняем в DataStore
        viewModelScope.launch {
            preferencesRepository?.saveTemperature(temperature)
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
}
