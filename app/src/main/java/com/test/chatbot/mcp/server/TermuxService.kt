package com.test.chatbot.mcp.server

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TermuxService(private val context: Context) {
    
    companion object {
        private const val TAG = "TermuxService"
        private const val TERMUX_PACKAGE = "com.termux"
        private const val TERMUX_API_PACKAGE = "com.termux.api"
        
        // Termux:API endpoints
        private const val TERMUX_SERVICE = "$TERMUX_PACKAGE.app.TermuxService"
    }
    
    /**
     * Проверить установлен ли Termux
     */
    suspend fun isTermuxInstalled(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            Result.success(true)
        } catch (e: PackageManager.NameNotFoundException) {
            Result.success(false)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Проверить установлен ли Termux:API
     */
    suspend fun isTermuxApiInstalled(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            context.packageManager.getPackageInfo(TERMUX_API_PACKAGE, 0)
            Result.success(true)
        } catch (e: PackageManager.NameNotFoundException) {
            Result.success(false)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Выполнить команду в Termux
     */
    suspend fun executeCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val isInstalled = isTermuxInstalled().getOrDefault(false)
            
            if (!isInstalled) {
                return@withContext Result.failure(
                    Exception(
                        "Termux не установлен.\n\n" +
                        "Установите Termux:\n" +
                        "https://f-droid.org/packages/com.termux/"
                    )
                )
            }
            
            Log.i(TAG, "Выполнение команды в Termux: $command")
            
            // Используем RUN_COMMAND intent для Termux
            val intent = Intent()
            intent.action = "$TERMUX_PACKAGE.RUN_COMMAND"
            intent.setClassName(TERMUX_PACKAGE, "$TERMUX_PACKAGE.app.RunCommandService")
            intent.putExtra("$TERMUX_PACKAGE.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            intent.putExtra("$TERMUX_PACKAGE.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
            intent.putExtra("$TERMUX_PACKAGE.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            intent.putExtra("$TERMUX_PACKAGE.RUN_COMMAND_BACKGROUND", false)
            intent.putExtra("$TERMUX_PACKAGE.RUN_COMMAND_SESSION_ACTION", "0") // Создать новую сессию
            
            context.startService(intent)
            
            Log.i(TAG, "✅ Команда отправлена в Termux")
            Result.success("✅ Команда отправлена в Termux:\n$command\n\nОткройте Termux для просмотра результата")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка выполнения команды: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Открыть Termux
     */
    suspend fun openTermux(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val isInstalled = isTermuxInstalled().getOrDefault(false)
            
            if (!isInstalled) {
                return@withContext Result.failure(Exception("Termux не установлен"))
            }
            
            val intent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Result.success("✅ Termux открыт")
            } else {
                Result.failure(Exception("Не удалось открыть Termux"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Получить информацию о Termux
     */
    suspend fun getTermuxInfo(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val termuxInstalled = isTermuxInstalled().getOrDefault(false)
            val apiInstalled = isTermuxApiInstalled().getOrDefault(false)
            
            val info = buildString {
                append("📱 TERMUX ИНТЕГРАЦИЯ\n")
                append("━━━━━━━━━━━━━━━━━━━━\n\n")
                
                append("Termux: ${if (termuxInstalled) "✅ Установлен" else "❌ Не установлен"}\n")
                append("Termux:API: ${if (apiInstalled) "✅ Установлен" else "❌ Не установлен"}\n\n")
                
                if (!termuxInstalled) {
                    append("━━━━━━━━━━━━━━━━━━━━\n")
                    append("📥 КАК УСТАНОВИТЬ:\n\n")
                    append("1. Скачайте Termux с F-Droid:\n")
                    append("   https://f-droid.org/packages/com.termux/\n\n")
                    append("2. Откройте Termux и выполните:\n")
                    append("   pkg update && pkg upgrade\n\n")
                    append("3. Для расширенных функций установите Termux:API:\n")
                    append("   https://f-droid.org/packages/com.termux.api/\n")
                } else {
                    append("━━━━━━━━━━━━━━━━━━━━\n")
                    append("💡 ДОСТУПНЫЕ КОМАНДЫ:\n\n")
                    append("Базовые:\n")
                    append("  • ls, cd, pwd - навигация\n")
                    append("  • cat, echo - работа с файлами\n")
                    append("  • python, node - скрипты\n")
                    append("  • git - контроль версий\n\n")
                    
                    if (apiInstalled) {
                        append("Termux:API:\n")
                        append("  • termux-battery-status - батарея\n")
                        append("  • termux-clipboard-get/set - буфер\n")
                        append("  • termux-notification - уведомления\n")
                        append("  • termux-toast - всплывающие сообщения\n")
                        append("  • termux-wifi-connectioninfo - WiFi\n")
                    }
                }
            }
            
            Result.success(info)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка получения информации: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Быстрые команды Termux
     */
    suspend fun runQuickCommand(commandType: String): Result<String> = withContext(Dispatchers.IO) {
        val command = when (commandType) {
            "battery" -> "termux-battery-status"
            "clipboard_get" -> "termux-clipboard-get"
            "wifi" -> "termux-wifi-connectioninfo"
            "location" -> "termux-location"
            "toast" -> "termux-toast 'Hello from ChatBot!'"
            "update" -> "pkg update && pkg upgrade -y"
            "python_version" -> "python --version"
            "node_version" -> "node --version"
            "git_version" -> "git --version"
            "disk_usage" -> "df -h"
            "memory" -> "free -h"
            else -> return@withContext Result.failure(Exception("Неизвестная быстрая команда: $commandType"))
        }
        
        executeCommand(command)
    }
    
    /**
     * Установить пакет в Termux
     */
    suspend fun installPackage(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        val command = "pkg install -y $packageName"
        executeCommand(command)
    }
    
    /**
     * Открыть ссылку на установку Termux
     */
    fun openTermuxInstallPage() {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://f-droid.org/packages/com.termux/")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка открытия ссылки: ${e.message}", e)
        }
    }
}


