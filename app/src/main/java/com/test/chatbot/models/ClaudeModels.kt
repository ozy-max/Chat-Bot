package com.test.chatbot.models

import com.google.gson.annotations.SerializedName

// Запрос к Claude API
data class ClaudeRequest(
    val model: String = "claude-sonnet-4-20250514",
    val messages: List<ClaudeMessage>,
    @SerializedName("max_tokens")
    val maxTokens: Int = 1024,
    val tools: List<Tool>? = null
)

// Сообщение в запросе/ответе
data class ClaudeMessage(
    val role: String, // "user" или "assistant"
    val content: Any // может быть String или List<ContentBlock>
)

// Блок контента (для сложных ответов с tool_use)
sealed class ContentBlock {
    data class TextBlock(
        val type: String = "text",
        val text: String
    ) : ContentBlock()
    
    data class ToolUseBlock(
        val type: String = "tool_use",
        val id: String,
        val name: String,
        val input: Map<String, Any>
    ) : ContentBlock()
    
    data class ToolResultBlock(
        val type: String = "tool_result",
        @SerializedName("tool_use_id")
        val toolUseId: String,
        val content: String
    ) : ContentBlock()
}

// Определение инструмента
data class Tool(
    val name: String,
    val description: String,
    @SerializedName("input_schema")
    val inputSchema: InputSchema
)

data class InputSchema(
    val type: String = "object",
    val properties: Map<String, Property>,
    val required: List<String>? = null
)

data class Property(
    val type: String,
    val description: String,
    val enum: List<String>? = null
)

// Ответ от Claude API
data class ClaudeResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ContentBlockResponse>,
    val model: String,
    @SerializedName("stop_reason")
    val stopReason: String?,
    @SerializedName("stop_sequence")
    val stopSequence: String?,
    val usage: Usage
)

// Блок контента в ответе
data class ContentBlockResponse(
    val type: String,
    val text: String? = null,
    val id: String? = null,
    val name: String? = null,
    val input: Map<String, Any>? = null
)

data class Usage(
    @SerializedName("input_tokens")
    val inputTokens: Int,
    @SerializedName("output_tokens")
    val outputTokens: Int
)

// Ошибка от API
data class ClaudeError(
    val type: String,
    val error: ErrorDetails
)

data class ErrorDetails(
    val type: String,
    val message: String
)

