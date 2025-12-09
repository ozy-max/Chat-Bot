package com.test.chatbot.presentation

import com.test.chatbot.models.AiProvider
import com.test.chatbot.models.Message

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showApiKeyDialog: Boolean = true,
    val apiKey: String = "",
    val yandexApiKey: String = "",
    val yandexFolderId: String = "",
    val temperature: Double = 0.7,
    val showSettingsDialog: Boolean = false,
    val selectedProvider: AiProvider = AiProvider.CLAUDE
)