package com.test.chatbot.presentation

import com.test.chatbot.models.AiProvider
import com.test.chatbot.models.PromptTemplates

sealed interface ChatUiEvents {
    data class SendMessage(val message: String) : ChatUiEvents
    data class UpdateApiKey(val apiKey: String) : ChatUiEvents
    data class UpdateYandexApiKey(val apiKey: String) : ChatUiEvents
    data class UpdateYandexFolderId(val folderId: String) : ChatUiEvents
    data class UpdateTodoistToken(val token: String) : ChatUiEvents
    data class UpdateTemperature(val temperature: Double) : ChatUiEvents
    data class UpdateMaxTokens(val maxTokens: Int) : ChatUiEvents
    data class UpdateProvider(val provider: AiProvider) : ChatUiEvents
    data class UpdateTaskType(val taskType: PromptTemplates.TaskType) : ChatUiEvents
    data class UpdateOllamaModel(val model: String) : ChatUiEvents
    data class UpdateContextWindow(val contextWindow: Int) : ChatUiEvents
    data object ShowApiKeyDialog : ChatUiEvents
    data object DismissApiKeyDialog : ChatUiEvents
    data object ShowSettingsDialog : ChatUiEvents
    data object DismissSettingsDialog : ChatUiEvents
    data object DismissError : ChatUiEvents
    data object ClearChat : ChatUiEvents
    
    // События для сравнения моделей
    data class CompareModels(val query: String) : ChatUiEvents
    data object ShowComparisonDialog : ChatUiEvents
    data object DismissComparisonDialog : ChatUiEvents
    data object ClearComparisonResult : ChatUiEvents
    
    // События для компрессии диалога
    data class ToggleCompression(val enabled: Boolean) : ChatUiEvents
    data class UpdateCompressionThreshold(val threshold: Int) : ChatUiEvents
    data object ManualCompress : ChatUiEvents // Ручная компрессия
    data object ShowCompressionInfo : ChatUiEvents
    data object DismissCompressionInfo : ChatUiEvents
    
    // События для долговременной памяти (хранит только summary предыдущего диалога)
    data class ToggleMemory(val enabled: Boolean) : ChatUiEvents
    data object ClearAllMemories : ChatUiEvents // Очистить сохранённый summary
    data object ShowMemoryDialog : ChatUiEvents
    data object DismissMemoryDialog : ChatUiEvents
    
    // AI Features Bottom Sheet
    data object ShowAiFeaturesSheet : ChatUiEvents
    data object DismissAiFeaturesSheet : ChatUiEvents
    
    // Lifecycle события
    data object OnAppPause : ChatUiEvents // Сохранить summary при уходе в фон
    
    // Анализ данных
    data object ShowDataAnalysisPanel : ChatUiEvents
    data object DismissDataAnalysisPanel : ChatUiEvents
    data class AnalyzeFile(val uri: android.net.Uri) : ChatUiEvents
    data class AskDataQuestion(val question: String) : ChatUiEvents
}

