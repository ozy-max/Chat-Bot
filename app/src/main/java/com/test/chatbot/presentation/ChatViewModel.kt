package com.test.chatbot.presentation

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
    
    private val conversationHistory = mutableListOf<ClaudeMessage>()
    
    fun onUiEvent(event: ChatUiEvents) {
        when (event) {
            is ChatUiEvents.SendMessage -> sendMessage(event.message)
            is ChatUiEvents.UpdateApiKey -> updateApiKey(event.apiKey)
            is ChatUiEvents.ShowApiKeyDialog -> showApiKeyDialog()
            is ChatUiEvents.DismissApiKeyDialog -> dismissApiKeyDialog()
            is ChatUiEvents.DismissError -> dismissError()
        }
    }
    
    private fun sendMessage(userMessage: String) {
        if (userMessage.isBlank()) return
        
        // Добавляем сообщение пользователя
        val userMsg = Message(text = userMessage, isUser = true)
        _uiState.update { it.copy(messages = it.messages + userMsg) }
        
        // Добавляем в историю разговора
        conversationHistory.add(ClaudeMessage(role = "user", content = userMessage))
        
        // Отправляем запрос к API
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                sendRequestToClaude()
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
    
    private suspend fun sendRequestToClaude() {
        val result = repository.sendMessage(_uiState.value.apiKey, conversationHistory)
        
        result.onSuccess { response ->
            handleClaudeResponse(response)
        }.onFailure { exception ->
            _uiState.update { 
                it.copy(
                    error = "Ошибка API: ${exception.message}",
                    isLoading = false
                ) 
            }
        }
    }
    
    private fun handleClaudeResponse(response: ClaudeResponse) {
        // Получаем текстовый ответ
        val textResponse = response.content
            .filter { it.type == "text" }
            .joinToString("") { it.text ?: "" }
        
        // Добавляем ответ ассистента в историю
        conversationHistory.add(ClaudeMessage(
            role = "assistant",
            content = textResponse
        ))
        
        // Отображаем ответ
        val botMessage = Message(
            text = textResponse.ifEmpty { "Получен пустой ответ" },
            isUser = false,
            toolCalls = null
        )
        
        _uiState.update { 
            it.copy(
                messages = it.messages + botMessage,
                isLoading = false
            ) 
        }
    }
    
    private fun updateApiKey(newKey: String) {
        _uiState.update { it.copy(apiKey = newKey) }
    }
    
    private fun showApiKeyDialog() {
        _uiState.update { it.copy(showApiKeyDialog = true) }
    }
    
    private fun dismissApiKeyDialog() {
        _uiState.update { it.copy(showApiKeyDialog = false) }
    }
    
    private fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
