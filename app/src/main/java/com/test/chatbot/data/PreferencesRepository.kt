package com.test.chatbot.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Extension для создания DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chat_settings")

/**
 * Репозиторий для сохранения настроек в DataStore
 */
class PreferencesRepository(private val context: Context) {
    
    companion object {
        // Ключи для хранения данных
        private val CLAUDE_API_KEY = stringPreferencesKey("claude_api_key")
        private val YANDEX_API_KEY = stringPreferencesKey("yandex_api_key")
        private val YANDEX_FOLDER_ID = stringPreferencesKey("yandex_folder_id")
        private val TEMPERATURE = doublePreferencesKey("temperature")
        private val SELECTED_PROVIDER = stringPreferencesKey("selected_provider")
        private val MAX_TOKENS = intPreferencesKey("max_tokens")
        private val MEMORY_ENABLED = booleanPreferencesKey("memory_enabled")
        // Pending messages для восстановления после kill
        private val PENDING_USER_MESSAGES = stringPreferencesKey("pending_user_messages")
    }
    
    /**
     * Данные настроек
     */
    data class AppSettings(
        val claudeApiKey: String = "",
        val yandexApiKey: String = "",
        val yandexFolderId: String = "",
        val temperature: Double = 0.7,
        val selectedProvider: String = "CLAUDE",
        val maxTokens: Int = 4096
    )
    
    /**
     * Поток настроек
     */
    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            claudeApiKey = preferences[CLAUDE_API_KEY] ?: "",
            yandexApiKey = preferences[YANDEX_API_KEY] ?: "",
            yandexFolderId = preferences[YANDEX_FOLDER_ID] ?: "",
            temperature = preferences[TEMPERATURE] ?: 0.7,
            selectedProvider = preferences[SELECTED_PROVIDER] ?: "CLAUDE",
            maxTokens = preferences[MAX_TOKENS] ?: 4096
        )
    }
    
    /**
     * Сохранить Claude API ключ
     */
    suspend fun saveClaudeApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[CLAUDE_API_KEY] = apiKey
        }
    }
    
    /**
     * Сохранить Yandex API ключ
     */
    suspend fun saveYandexApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[YANDEX_API_KEY] = apiKey
        }
    }
    
    /**
     * Сохранить Yandex Folder ID
     */
    suspend fun saveYandexFolderId(folderId: String) {
        context.dataStore.edit { preferences ->
            preferences[YANDEX_FOLDER_ID] = folderId
        }
    }
    
    /**
     * Сохранить температуру
     */
    suspend fun saveTemperature(temperature: Double) {
        context.dataStore.edit { preferences ->
            preferences[TEMPERATURE] = temperature
        }
    }
    
    /**
     * Сохранить выбранный провайдер
     */
    suspend fun saveSelectedProvider(provider: String) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_PROVIDER] = provider
        }
    }
    
    /**
     * Сохранить максимальное количество токенов
     */
    suspend fun saveMaxTokens(maxTokens: Int) {
        context.dataStore.edit { preferences ->
            preferences[MAX_TOKENS] = maxTokens
        }
    }
    
    /**
     * Сохранить все настройки сразу
     */
    suspend fun saveAllSettings(
        claudeApiKey: String,
        yandexApiKey: String,
        yandexFolderId: String,
        temperature: Double,
        selectedProvider: String
    ) {
        context.dataStore.edit { preferences ->
            preferences[CLAUDE_API_KEY] = claudeApiKey
            preferences[YANDEX_API_KEY] = yandexApiKey
            preferences[YANDEX_FOLDER_ID] = yandexFolderId
            preferences[TEMPERATURE] = temperature
            preferences[SELECTED_PROVIDER] = selectedProvider
        }
    }
    
    /**
     * Сохранить состояние памяти (включена/выключена)
     */
    suspend fun saveMemoryEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MEMORY_ENABLED] = enabled
        }
    }
    
    /**
     * Загрузить состояние памяти
     */
    suspend fun loadMemoryEnabled(): Boolean {
        val preferences = context.dataStore.data.first()
        return preferences[MEMORY_ENABLED] ?: true
    }
    
    /**
     * Сохранить pending сообщения пользователя (для восстановления после kill)
     * Сообщения сохраняются через разделитель
     */
    suspend fun savePendingUserMessages(messages: List<String>) {
        context.dataStore.edit { preferences ->
            if (messages.isEmpty()) {
                preferences.remove(PENDING_USER_MESSAGES)
            } else {
                // Используем специальный разделитель который вряд ли встретится в тексте
                preferences[PENDING_USER_MESSAGES] = messages.joinToString("|||SEPARATOR|||")
            }
        }
    }
    
    /**
     * Загрузить pending сообщения пользователя
     */
    suspend fun loadPendingUserMessages(): List<String> {
        val preferences = context.dataStore.data.first()
        val saved = preferences[PENDING_USER_MESSAGES] ?: return emptyList()
        return saved.split("|||SEPARATOR|||").filter { it.isNotBlank() }
    }
    
    /**
     * Очистить pending сообщения
     */
    suspend fun clearPendingUserMessages() {
        context.dataStore.edit { preferences ->
            preferences.remove(PENDING_USER_MESSAGES)
        }
    }
}

