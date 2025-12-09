package com.test.chatbot.presentation

import com.test.chatbot.models.AiProvider

sealed interface ChatUiEvents {
    data class SendMessage(val message: String) : ChatUiEvents
    data class UpdateApiKey(val apiKey: String) : ChatUiEvents
    data class UpdateYandexApiKey(val apiKey: String) : ChatUiEvents
    data class UpdateYandexFolderId(val folderId: String) : ChatUiEvents
    data class UpdateTemperature(val temperature: Double) : ChatUiEvents
    data class UpdateProvider(val provider: AiProvider) : ChatUiEvents
    data object ShowApiKeyDialog : ChatUiEvents
    data object DismissApiKeyDialog : ChatUiEvents
    data object ShowSettingsDialog : ChatUiEvents
    data object DismissSettingsDialog : ChatUiEvents
    data object DismissError : ChatUiEvents
    data object ClearChat : ChatUiEvents
}

