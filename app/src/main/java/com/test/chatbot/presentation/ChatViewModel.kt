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
            is ChatUiEvents.ClearChat -> clearChat()
        }
    }
    
    private fun clearChat() {
        conversationHistory.clear()
        _uiState.update { it.copy(messages = emptyList()) }
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
    
    private suspend fun handleClaudeResponse(response: ClaudeResponse) {
        val toolCalls = mutableListOf<ToolCall>()
        var textResponse = ""
        var needsToolExecution = false
        
        // Обрабатываем блоки контента
        for (block in response.content) {
            when (block.type) {
                "text" -> {
                    textResponse += block.text ?: ""
                }
                "tool_use" -> {
                    needsToolExecution = true
                    val toolName = block.name ?: ""
                    val input = block.input ?: emptyMap()
                    val toolUseId = block.id ?: ""
                    
                    // Выполняем инструмент
                    val result = repository.executeToolCall(toolName, input)
                    
                    toolCalls.add(ToolCall(
                        toolName = toolName,
                        input = input,
                        result = result
                    ))
                }
            }
        }
        
        // Добавляем ответ ассистента в историю
        conversationHistory.add(ClaudeMessage(
            role = "assistant",
            content = response.content.map { block ->
                when (block.type) {
                    "text" -> mapOf("type" to "text", "text" to (block.text ?: ""))
                    "tool_use" -> mapOf(
                        "type" to "tool_use",
                        "id" to (block.id ?: ""),
                        "name" to (block.name ?: ""),
                        "input" to (block.input ?: emptyMap<String, Any>())
                    )
                    else -> emptyMap()
                }
            }
        ))
        
        if (needsToolExecution) {
            // Добавляем результаты выполнения инструментов в историю
            val toolResults = toolCalls.map { toolCall ->
                val toolUseBlock = response.content.find { 
                    it.type == "tool_use" && it.name == toolCall.toolName 
                }
                
                mapOf(
                    "type" to "tool_result",
                    "tool_use_id" to (toolUseBlock?.id ?: ""),
                    "content" to (toolCall.result ?: "")
                )
            }
            
            conversationHistory.add(ClaudeMessage(
                role = "user",
                content = toolResults
            ))
            
            // Отправляем повторный запрос для получения финального ответа
            // (без промежуточного сообщения)
            sendRequestToClaude()
        } else {
            // Это финальный ответ
            val botMessage = Message(
                text = textResponse.ifEmpty { "Получен пустой ответ" },
                isUser = false,
                toolCalls = if (toolCalls.isNotEmpty()) toolCalls else null
            )
            
            _uiState.update { 
                it.copy(
                    messages = it.messages + botMessage,
                    isLoading = false
                ) 
            }
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
