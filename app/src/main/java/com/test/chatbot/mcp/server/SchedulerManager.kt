package com.test.chatbot.mcp.server

import android.util.Log
import kotlinx.coroutines.*
import java.util.Calendar

/**
 * Менеджер планировщиков для MCP сервера
 * Управляет периодической синхронизацией и ежедневными сводками
 */
class SchedulerManager(
    private val taskRepository: TaskRepository,
    private val todoistService: TodoistService,
    private val scope: CoroutineScope
) {

    private var syncJob: Job? = null
    private var dailySummaryJob: Job? = null
    private var intervalMinutes: Int = 1

    companion object {
        private const val TAG = "SchedulerManager"
        private const val DAILY_SUMMARY_HOUR = 18
        private const val DAILY_SUMMARY_MINUTE = 0
    }

    /**
     * Запустить планировщики
     */
    fun start(syncIntervalMinutes: Int) {
        this.intervalMinutes = syncIntervalMinutes
        startPeriodicSync()
        startDailySummary()
        Log.i(TAG, "✅ Планировщики запущены")
    }

    /**
     * Остановить планировщики
     */
    fun stop() {
        syncJob?.cancel()
        dailySummaryJob?.cancel()
        Log.i(TAG, "🛑 Планировщики остановлены")
    }

    /**
     * Обновить интервал синхронизации
     */
    fun updateInterval(minutes: Int) {
        if (minutes >= 1) {
            this.intervalMinutes = minutes
            // Перезапускаем периодическую синхронизацию с новым интервалом
            syncJob?.cancel()
            startPeriodicSync()
            Log.i(TAG, "✅ Интервал обновлен: $minutes минут")
        }
    }

    /**
     * Запустить периодическую синхронизацию
     */
    private fun startPeriodicSync() {
        syncJob = scope.launch {
            Log.i(TAG, "🔄 Периодическая синхронизация: каждые $intervalMinutes минут")
            
            // Немедленная синхронизация при старте
            performSyncAndNotify()
            
            while (isActive) {
                try {
                    // Ждем интервал
                    delay(intervalMinutes * 60 * 1000L)
                    
                    // Синхронизируем задачи и отправляем summary
                    performSyncAndNotify()
                    
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Ошибка периодической синхронизации: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Выполнить синхронизацию и отправить уведомление
     */
    private suspend fun performSyncAndNotify() {
        try {
            // Синхронизируем задачи
            Log.i(TAG, "🔍 Проверка новых задач в Todoist...")
            val syncedCount = todoistService.syncTasks(taskRepository)
            
            val summary = taskRepository.getTodaySummary(todoistService)
            
            val summaryText = buildString {
                append("📊 Сводка задач\n\n")
                append("✅ Выполнено сегодня: ${summary.completedToday}\n")
                append("📝 Создано сегодня: ${summary.createdToday}\n")
                append("⏳ Осталось активных: ${summary.pendingCount}")
                if (syncedCount > 0) {
                    append("\n\n🔄 Синхронизировано изменений: $syncedCount")
                }
            }
            
            Log.i(TAG, "\n" + "=".repeat(50))
            Log.i(TAG, summaryText)
            Log.i(TAG, "=".repeat(50) + "\n")
            
            // Отправляем уведомление через callback
            onSummaryGenerated?.invoke(summaryText)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка синхронизации: ${e.message}")
        }
    }
    
    // Callback для отправки summary
    private var onSummaryGenerated: ((String) -> Unit)? = null
    
    /**
     * Установить callback для получения summary
     */
    fun setOnSummaryGenerated(callback: (String) -> Unit) {
        this.onSummaryGenerated = callback
    }

    /**
     * Запустить ежедневную сводку
     */
    private fun startDailySummary() {
        dailySummaryJob = scope.launch {
            Log.i(TAG, "📊 Ежедневная сводка: каждый день в $DAILY_SUMMARY_HOUR:${DAILY_SUMMARY_MINUTE.toString().padStart(2, '0')}")
            
            while (isActive) {
                try {
                    // Вычисляем время до следующей сводки
                    val delayMs = calculateDelayUntilDailySummary()
                    
                    Log.i(TAG, "⏰ Следующая сводка через ${delayMs / 1000 / 60} минут")
                    
                    // Ждем до времени сводки
                    delay(delayMs)
                    
                    // Генерируем и отправляем сводку
                    if (isActive) {
                        sendDailySummary()
                    }
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Ошибка ежедневной сводки: ${e.message}")
                    // При ошибке ждем час и пытаемся снова
                    delay(60 * 60 * 1000L)
                }
            }
        }
    }

    /**
     * Вычислить задержку до следующей ежедневной сводки
     */
    private fun calculateDelayUntilDailySummary(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, DAILY_SUMMARY_HOUR)
            set(Calendar.MINUTE, DAILY_SUMMARY_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            
            // Если время уже прошло сегодня, планируем на завтра
            if (before(now)) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        
        return target.timeInMillis - now.timeInMillis
    }

    /**
     * Отправить ежедневную сводку
     */
    private suspend fun sendDailySummary() {
        try {
            Log.i(TAG, "\n" + "=".repeat(50))
            Log.i(TAG, "📊 Генерация ежедневной сводки...")
            
            // Синхронизируем с Todoist
            val syncedCount = todoistService.syncTasks(taskRepository)
            if (syncedCount > 0) {
                Log.i(TAG, "📥 Синхронизировано с Todoist: $syncedCount")
            }
            
            val summary = taskRepository.getTodaySummary(todoistService)
            
            val summaryText = buildString {
                append("📊 Ежедневная сводка задач\n\n")
                append("✅ Выполнено сегодня: ${summary.completedToday}\n")
                append("📝 Создано сегодня: ${summary.createdToday}\n")
                append("⏳ Осталось активных: ${summary.pendingCount}")
            }
            
            Log.i(TAG, summaryText)
            Log.i(TAG, "=".repeat(50) + "\n")
            
            // Здесь можно отправить push-уведомление
            // notificationManager.sendNotification(summaryText)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка генерации сводки: ${e.message}", e)
        }
    }
}

