package com.test.chatbot.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.test.chatbot.models.*
import com.test.chatbot.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository = ChatRepository()
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    // История для Claude
    private val claudeHistory = mutableListOf<ClaudeMessage>()
    // История для YandexGPT
    private val yandexHistory = mutableListOf<YandexGptMessage>()
    
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
        Log.d("###########","updateTemperature: $temperature")
        _uiState.update { it.copy(temperature = temperature) }
    }
    
    private fun updateApiKey(apiKey: String) {
        _uiState.update { it.copy(apiKey = apiKey) }
    }
    
    private fun updateYandexApiKey(apiKey: String) {
        _uiState.update { it.copy(yandexApiKey = apiKey) }
    }
    
    private fun updateYandexFolderId(folderId: String) {
        _uiState.update { it.copy(yandexFolderId = folderId) }
    }
    
    private fun updateProvider(provider: AiProvider) {
        _uiState.update { it.copy(selectedProvider = provider) }
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
}
