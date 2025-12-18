package com.test.chatbot

import android.app.Application
import android.util.Log
import com.test.chatbot.mcp.server.McpServer
import com.test.chatbot.utils.NotificationHelper

/**
 * Application класс для управления глобальным состоянием приложения
 * Автоматически запускает и останавливает MCP сервер
 */
class ChatBotApplication : Application() {

    companion object {
        private const val TAG = "ChatBotApplication"
        
        // Глобальный экземпляр MCP сервера
        lateinit var mcpServer: McpServer
            private set
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "🚀 ChatBot Application запускается...")
        
        // Создаем и запускаем MCP сервер
        try {
            mcpServer = McpServer(applicationContext)
            mcpServer.initialize()
            
            // Настраиваем callback для summary уведомлений
            mcpServer.setSummaryCallback { summaryText ->
                Log.i(TAG, "📊 Получен summary, отправляем уведомление...")
                NotificationHelper.sendSummaryNotification(applicationContext, summaryText)
            }
            
            mcpServer.startServer()
            
            Log.i(TAG, "✅ MCP Server запущен автоматически")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка запуска MCP сервера: ${e.message}", e)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.i(TAG, "🛑 ChatBot Application останавливается...")
        
        // Останавливаем MCP сервер
        try {
            mcpServer.stopServer()
            Log.i(TAG, "✅ MCP Server остановлен")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка остановки MCP сервера: ${e.message}", e)
        }
    }
}

