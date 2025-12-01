package com.test.chatbot.models

data class Message(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val toolCalls: List<ToolCall>? = null
)

data class ToolCall(
    val toolName: String,
    val input: Map<String, Any>,
    val result: String? = null
)

