package com.test.chatbot.models

import com.google.gson.annotations.SerializedName

// Запрос к YandexGPT API
data class YandexGptRequest(
    @SerializedName("modelUri")
    val modelUri: String,
    @SerializedName("completionOptions")
    val completionOptions: CompletionOptions,
    @SerializedName("messages")
    val messages: List<YandexGptMessage>
)

data class CompletionOptions(
    @SerializedName("stream")
    val stream: Boolean = false,
    @SerializedName("temperature")
    val temperature: Double = 0.7,
    @SerializedName("maxTokens")
    val maxTokens: String = "2000"
)

// Сообщение в запросе YandexGPT
data class YandexGptMessage(
    @SerializedName("role")
    val role: String, // "system", "user", "assistant"
    @SerializedName("text")
    val text: String
)

// Ответ от YandexGPT API
data class YandexGptResponse(
    @SerializedName("result")
    val result: YandexGptResult
)

data class YandexGptResult(
    @SerializedName("alternatives")
    val alternatives: List<Alternative>,
    @SerializedName("usage")
    val usage: YandexUsage?,
    @SerializedName("modelVersion")
    val modelVersion: String?
)

data class Alternative(
    @SerializedName("message")
    val message: YandexGptMessage,
    @SerializedName("status")
    val status: String?
)

data class YandexUsage(
    @SerializedName("inputTextTokens")
    val inputTextTokens: String?,
    @SerializedName("completionTokens")
    val completionTokens: String?,
    @SerializedName("totalTokens")
    val totalTokens: String?
)

// Enum для выбора AI провайдера
enum class AiProvider(val displayName: String) {
    CLAUDE("Claude"),
    YANDEX_GPT("YandexGPT")
}

