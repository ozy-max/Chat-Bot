package com.test.chatbot.presentation

import com.test.chatbot.models.Message

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showApiKeyDialog: Boolean = true,
    val apiKey: String = ""
)