package com.test.chatbot.presentation

import com.test.chatbot.models.AiProvider
import com.test.chatbot.models.Message
import com.test.chatbot.models.ModelComparisonResult

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showApiKeyDialog: Boolean = false, // По умолчанию false, покажем после загрузки настроек
    val apiKey: String = "",
    val yandexApiKey: String = "",
    val yandexFolderId: String = "",
    val temperature: Double = 0.7,
    val showSettingsDialog: Boolean = false,
    val selectedProvider: AiProvider = AiProvider.CLAUDE,
    
    // Состояние загрузки настроек из DataStore
    val isSettingsLoaded: Boolean = false,
    
    // Состояние сравнения моделей
    val showComparisonDialog: Boolean = false,
    val isComparing: Boolean = false,
    val comparisonResult: ModelComparisonResult? = null
)