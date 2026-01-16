package com.test.chatbot.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Хранение пользовательских данных
 */
class UserPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "chatbot_user_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_FIRST_NAME = "first_name"
        private const val KEY_LAST_NAME = "last_name"
        private const val KEY_EMAIL = "email"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_PROJECT_PATH = "project_path"
    }

    // User ID (генерируется при первом запуске)
    var userId: String
        get() = prefs.getString(KEY_USER_ID, null) ?: generateUserId().also { userId = it }
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    // Имя пользователя
    var firstName: String?
        get() = prefs.getString(KEY_FIRST_NAME, null)
        set(value) = prefs.edit().putString(KEY_FIRST_NAME, value).apply()

    // Фамилия пользователя
    var lastName: String?
        get() = prefs.getString(KEY_LAST_NAME, null)
        set(value) = prefs.edit().putString(KEY_LAST_NAME, value).apply()

    // Email (опционально)
    var email: String?
        get() = prefs.getString(KEY_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    // Флаг завершения онбординга
    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    // URL GitHub репозитория для сканирования
    var githubRepoUrl: String
        get() = prefs.getString(KEY_PROJECT_PATH, "https://github.com/ozy-max/Chat-Bot") ?: "https://github.com/ozy-max/Chat-Bot"
        set(value) = prefs.edit().putString(KEY_PROJECT_PATH, value).apply()
    
    // Ветка GitHub (по умолчанию main)
    var githubBranch: String
        get() = prefs.getString("github_branch", "main") ?: "main"
        set(value) = prefs.edit().putString("github_branch", value).apply()

    // Полное имя
    val fullName: String
        get() = "${firstName ?: ""} ${lastName ?: ""}".trim().ifEmpty { "Пользователь" }

    // Проверка заполнения профиля
    val isProfileComplete: Boolean
        get() = !firstName.isNullOrBlank() && !lastName.isNullOrBlank()

    // Генерация уникального ID пользователя
    private fun generateUserId(): String {
        return "user_${System.currentTimeMillis() % 1000000}"
    }

    // Очистка данных (для тестирования)
    fun clear() {
        prefs.edit().clear().apply()
    }
}
