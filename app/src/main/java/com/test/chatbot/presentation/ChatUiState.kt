package com.test.chatbot.presentation

import com.test.chatbot.models.AiProvider
import com.test.chatbot.models.CompressionSettings
import com.test.chatbot.models.CompressionState
import com.test.chatbot.models.Message
import com.test.chatbot.models.ModelComparisonResult
import com.test.chatbot.models.TokenStats

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showApiKeyDialog: Boolean = false, // По умолчанию false, покажем после загрузки настроек
    val apiKey: String = "",
    val yandexApiKey: String = "",
    val yandexFolderId: String = "",
    val temperature: Double = 0.7,
    val maxTokens: Int = 4096, // Максимальное количество токенов для ответа
    val showSettingsDialog: Boolean = false,
    val selectedProvider: AiProvider = AiProvider.CLAUDE,
    
    // Состояние загрузки настроек из DataStore
    val isSettingsLoaded: Boolean = false,
    
    // Статистика токенов
    val tokenStats: TokenStats = TokenStats(),
    
    // Состояние сравнения моделей
    val showComparisonDialog: Boolean = false,
    val isComparing: Boolean = false,
    val comparisonResult: ModelComparisonResult? = null,
    
    // Компрессия диалога
    val compressionSettings: CompressionSettings = CompressionSettings(),
    val compressionState: CompressionState = CompressionState(),
    val isCompressing: Boolean = false,
    val showCompressionInfo: Boolean = false
)