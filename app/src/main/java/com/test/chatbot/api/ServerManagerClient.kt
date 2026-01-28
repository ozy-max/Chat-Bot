package com.test.chatbot.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Клиент для управления серверами (запуск/остановка)
 * Работает с локальным Ollama сервером
 */
class ServerManagerClient {
    
    companion object {
        private const val TAG = "ServerManagerClient"
        
        // Эндпоинты для управления серверами
        private const val OLLAMA_API_URL = "http://localhost:8080"
        private const val OLLAMA_DIRECT_URL = "http://localhost:11434"
        
        // Таймауты для HTTP запросов
        private const val CONNECT_TIMEOUT = 5L // секунды
        private const val READ_TIMEOUT = 10L // секунды
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
        .build()
    
    /**
     * Запустить серверы Ollama
     * В Android приложении мы не можем напрямую запускать процессы на хосте,
     * поэтому этот метод проверяет доступность серверов и возвращает результат
     */
    suspend fun startServers(): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Проверка доступности Ollama серверов...")
            
            // Проверяем API сервер
            val apiAvailable = checkServerAvailability(OLLAMA_API_URL)
            if (apiAvailable) {
                Log.i(TAG, "✅ Ollama API сервер уже запущен ($OLLAMA_API_URL)")
                return@withContext Result.success("Ollama API сервер доступен")
            }
            
            // Проверяем прямое подключение
            val directAvailable = checkServerAvailability(OLLAMA_DIRECT_URL)
            if (directAvailable) {
                Log.i(TAG, "✅ Ollama сервер уже запущен ($OLLAMA_DIRECT_URL)")
                return@withContext Result.success("Ollama сервер доступен")
            }
            
            // Серверы не запущены
            Log.w(TAG, "⚠️ Ollama серверы не доступны")
            Log.w(TAG, "Пожалуйста, запустите серверы вручную:")
            Log.w(TAG, "  1. Откройте терминал")
            Log.w(TAG, "  2. Перейдите в папку проекта")
            Log.w(TAG, "  3. Запустите: ./start_ollama_api.sh")
            
            Result.failure(
                Exception(
                    "Ollama серверы не доступны. " +
                    "Запустите их вручную: ./start_ollama_api.sh"
                )
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка проверки серверов: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Остановить серверы Ollama
     * В Android приложении мы не можем напрямую останавливать процессы на хосте,
     * поэтому этот метод просто логирует сообщение
     */
    suspend fun stopServers(): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Запрос на остановку Ollama серверов...")
            
            // В Android мы не можем остановить серверы на хосте
            // Серверы должны быть остановлены вручную или через отдельный скрипт
            Log.i(TAG, "ℹ️ Ollama серверы должны быть остановлены вручную")
            Log.i(TAG, "Для остановки:")
            Log.i(TAG, "  1. Найдите процесс: ps aux | grep ollama")
            Log.i(TAG, "  2. Остановите: kill <PID>")
            
            Result.success("Запрос на остановку отправлен (требуется ручная остановка)")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка при остановке серверов: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Проверить доступность сервера по URL
     */
    private fun checkServerAvailability(url: String): Boolean {
        return try {
            val request = Request.Builder()
                .url("$url/api/tags") // Ollama endpoint для списка моделей
                .get()
                .build()
            
            val response = httpClient.newCall(request).execute()
            val isAvailable = response.isSuccessful
            response.close()
            
            Log.d(TAG, "Проверка $url: ${if (isAvailable) "✅ доступен" else "❌ недоступен"}")
            isAvailable
            
        } catch (e: Exception) {
            Log.d(TAG, "Проверка $url: ❌ недоступен (${e.message})")
            false
        }
    }
    
    /**
     * Получить статус серверов
     */
    suspend fun getServerStatus(): ServerStatus = withContext(Dispatchers.IO) {
        val apiAvailable = checkServerAvailability(OLLAMA_API_URL)
        val directAvailable = checkServerAvailability(OLLAMA_DIRECT_URL)
        
        return@withContext ServerStatus(
            apiServerRunning = apiAvailable,
            directServerRunning = directAvailable,
            apiServerUrl = OLLAMA_API_URL,
            directServerUrl = OLLAMA_DIRECT_URL
        )
    }
    
    /**
     * Статус серверов
     */
    data class ServerStatus(
        val apiServerRunning: Boolean,
        val directServerRunning: Boolean,
        val apiServerUrl: String,
        val directServerUrl: String
    ) {
        val anyServerRunning: Boolean
            get() = apiServerRunning || directServerRunning
        
        val preferredUrl: String
            get() = if (apiServerRunning) apiServerUrl else directServerUrl
    }
}
