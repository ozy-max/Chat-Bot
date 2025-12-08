package com.test.chatbot.presentation

sealed interface ChatUiEvents {
    data class SendMessage(val message: String) : ChatUiEvents
    data class UpdateApiKey(val apiKey: String) : ChatUiEvents
    data class UpdateTemperature(val temperature: Double) : ChatUiEvents
    data object ShowApiKeyDialog : ChatUiEvents
    data object DismissApiKeyDialog : ChatUiEvents
    data object ShowSettingsDialog : ChatUiEvents
    data object DismissSettingsDialog : ChatUiEvents
    data object DismissError : ChatUiEvents
    data object ClearChat : ChatUiEvents
}

