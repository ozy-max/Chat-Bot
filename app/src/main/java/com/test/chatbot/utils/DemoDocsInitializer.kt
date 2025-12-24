package com.test.chatbot.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Инициализатор демо-документов
 * Копирует документы из assets в internal storage при первом запуске
 */
class DemoDocsInitializer(private val context: Context) {
    
    companion object {
        private const val TAG = "DemoDocsInitializer"
        private const val DEMO_DOCS_FLAG = "demo_docs_initialized"
        private const val ASSETS_FOLDER = "demo_docs"
    }
    
    /**
     * Проверить и загрузить демо-документы если их ещё нет
     */
    suspend fun initializeDemoDocsIfNeeded(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            
            // Проверяем, были ли уже загружены документы
            if (prefs.getBoolean(DEMO_DOCS_FLAG, false)) {
                Log.i(TAG, "Демо-документы уже загружены")
                return@withContext Result.success(0)
            }
            
            Log.i(TAG, "Загрузка демо-документов...")
            
            val filesDir = context.filesDir
            var copiedCount = 0
            
            // Получаем список файлов из assets/demo_docs
            val assetManager = context.assets
            val files = assetManager.list(ASSETS_FOLDER) ?: emptyArray()
            
            for (fileName in files) {
                try {
                    val assetPath = "$ASSETS_FOLDER/$fileName"
                    val targetFile = File(filesDir, fileName)
                    
                    // Копируем файл из assets в internal storage
                    assetManager.open(assetPath).use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    copiedCount++
                    Log.i(TAG, "✅ Скопирован: $fileName")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Ошибка копирования $fileName: ${e.message}", e)
                }
            }
            
            // Отмечаем что документы загружены
            prefs.edit().putBoolean(DEMO_DOCS_FLAG, true).apply()
            
            Log.i(TAG, "✅ Загружено демо-документов: $copiedCount")
            Result.success(copiedCount)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка инициализации демо-документов: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Получить список демо-документов
     */
    fun getDemoDocsList(): List<String> {
        return try {
            val assetManager = context.assets
            assetManager.list(ASSETS_FOLDER)?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения списка демо-документов: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Сбросить флаг инициализации (для повторной загрузки)
     */
    fun resetInitializationFlag() {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(DEMO_DOCS_FLAG, false).apply()
        Log.i(TAG, "Флаг инициализации сброшен")
    }
}


