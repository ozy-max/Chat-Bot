package com.test.chatbot.presentation

sealed interface ChatUiEvents {
    data class SendMessage(val message: String) : ChatUiEvents
    data class UpdateApiKey(val apiKey: String) : ChatUiEvents
    data object ShowApiKeyDialog : ChatUiEvents
    data object DismissApiKeyDialog : ChatUiEvents
    data object DismissError : ChatUiEvents
}

