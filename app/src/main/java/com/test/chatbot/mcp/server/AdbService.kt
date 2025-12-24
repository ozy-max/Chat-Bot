package com.test.chatbot.mcp.server

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class AdbService(private val context: Context) {
    
    companion object {
        private const val TAG = "AdbService"
        private const val SCREENSHOTS_DIR = "screenshots"
    }
    
    private val screenshotsDir: File by lazy {
        File(context.filesDir, SCREENSHOTS_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    /**
     * Выполнить shell команду
     */
    suspend fun executeShellCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Выполнение команды: $command")
            
            val process = Runtime.getRuntime().exec(command)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            
            val output = StringBuilder()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            val errorOutput = StringBuilder()
            while (errorReader.readLine().also { line = it } != null) {
                errorOutput.append(line).append("\n")
            }
            
            process.waitFor()
            val exitCode = process.exitValue()
            
            reader.close()
            errorReader.close()
            
            if (exitCode == 0) {
                Log.i(TAG, "✅ Команда выполнена успешно")
                Result.success(output.toString())
            } else {
                val error = "Exit code: $exitCode\n${errorOutput}"
                Log.e(TAG, "❌ Ошибка выполнения: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Исключение при выполнении команды: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Сделать скриншот экрана (требует root или специальных разрешений)
     */
    suspend fun takeScreenshot(): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Создание скриншота...")
            
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val filename = "screenshot_$timestamp.png"
            val tempFile = File(context.cacheDir, "temp_screenshot.png")
            val finalFile = File(screenshotsDir, filename)
            
            // Используем shell команду screencap
            val command = "screencap -p ${tempFile.absolutePath}"
            val result = executeShellCommand(command)
            
            if (result.isSuccess && tempFile.exists()) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
                
                Log.i(TAG, "✅ Скриншот сохранён: ${finalFile.absolutePath}")
                Result.success(finalFile.absolutePath)
            } else {
                Result.failure(Exception("Не удалось создать скриншот. Возможно требуются root права."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка создания скриншота: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Получить логи приложения
     */
    suspend fun getAppLogs(packageName: String = context.packageName, lines: Int = 100): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Получение логов для: $packageName")
            
            // Получаем последние N строк логов для этого приложения
            val command = "logcat -d -t $lines"
            val result = executeShellCommand(command)
            
            if (result.isSuccess) {
                val logs = result.getOrNull() ?: ""
                // Фильтруем логи по нашему приложению
                val filteredLogs = logs.lines()
                    .filter { it.contains(packageName, ignoreCase = true) }
                    .takeLast(lines)
                    .joinToString("\n")
                
                Log.i(TAG, "✅ Получено ${filteredLogs.lines().size} строк логов")
                Result.success(filteredLogs)
            } else {
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка получения логов: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Получить информацию об устройстве
     */
    suspend fun getDeviceInfo(): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Получение информации об устройстве")
            
            val info = buildString {
                append("📱 Устройство: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                append("📊 Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
                append("🏷️ Device: ${Build.DEVICE}\n")
                append("🔖 Brand: ${Build.BRAND}\n")
                append("💾 Board: ${Build.BOARD}\n")
                append("🏗️ Product: ${Build.PRODUCT}\n")
                
                // Дополнительная информация
                val runtime = Runtime.getRuntime()
                val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                val maxMemory = runtime.maxMemory() / 1024 / 1024
                append("🧠 Память: $usedMemory MB / $maxMemory MB\n")
                
                // Процессоры
                append("⚙️ Процессоры: ${runtime.availableProcessors()}\n")
            }
            
            Log.i(TAG, "✅ Информация получена")
            Result.success(info)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка получения информации: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Запустить приложение
     */
    suspend fun startApp(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Запуск приложения: $packageName")
            
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
            
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.i(TAG, "✅ Приложение запущено: $packageName")
                Result.success("Приложение $packageName запущено")
            } else {
                val error = "Приложение $packageName не найдено"
                Log.e(TAG, "❌ $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка запуска приложения: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Проверить установлено ли приложение
     */
    suspend fun isAppInstalled(packageName: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            Result.success(true)
        } catch (e: PackageManager.NameNotFoundException) {
            Result.success(false)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Получить список установленных приложений
     */
    suspend fun getInstalledApps(limit: Int = 20): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Получение списка установленных приложений")
            
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.packageName.startsWith("com.") } // Фильтруем системные
                .sortedBy { it.packageName }
                .take(limit)
            
            val appsList = buildString {
                append("📱 Установленные приложения (${packages.size}):\n\n")
                packages.forEachIndexed { index, appInfo ->
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    append("${index + 1}. $appName\n")
                    append("   ${appInfo.packageName}\n\n")
                }
            }
            
            Log.i(TAG, "✅ Получено ${packages.size} приложений")
            Result.success(appsList)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка получения списка приложений: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Очистить кэш приложения (требует root)
     */
    suspend fun clearAppCache(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Очистка кэша приложения: $packageName")
            
            val command = "pm clear $packageName"
            val result = executeShellCommand(command)
            
            if (result.isSuccess) {
                Log.i(TAG, "✅ Кэш очищен")
                Result.success("Кэш приложения $packageName очищен")
            } else {
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка очистки кэша: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Получить директорию скриншотов
     */
    fun getScreenshotsDirectory(): String = screenshotsDir.absolutePath
}


