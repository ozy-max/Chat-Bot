package com.test.chatbot.models

data class Message(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val toolCalls: List<ToolCall>? = null,
    // Информация о токенах
    val inputTokens: Int? = null,   // Токены запроса (для сообщений пользователя)
    val outputTokens: Int? = null,  // Токены ответа (для сообщений бота)
    // Провайдер (для отображения названия модели)
    val provider: AiProvider? = null
)

data class ToolCall(
    val toolName: String,
    val input: Map<String, Any>,
    val result: String? = null
)

