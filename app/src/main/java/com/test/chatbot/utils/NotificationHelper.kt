package com.test.chatbot.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.test.chatbot.MainActivity
import com.test.chatbot.R

/**
 * Помощник для отправки уведомлений
 */
object NotificationHelper {

    private const val CHANNEL_ID = "chatbot_summary"
    private const val CHANNEL_NAME = "Сводки задач"
    private const val CHANNEL_DESCRIPTION = "Уведомления о summary задач из Todoist"
    private const val NOTIFICATION_ID = 1001

    /**
     * Создать канал уведомлений (для Android 8.0+)
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Отправить уведомление с summary
     */
    fun sendSummaryNotification(context: Context, summaryText: String) {
        createNotificationChannel(context)
        
        // Intent для открытия приложения при клике на уведомление
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Создаем уведомление
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Используем стандартную иконку
            .setContentTitle("📊 Сводка задач")
            .setContentText(summaryText.lines().firstOrNull() ?: "Новая сводка")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(summaryText)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        // Отправляем уведомление
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}

