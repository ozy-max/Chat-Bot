package com.test.chatbot.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Singleton для передачи сообщений между экранами
 */
object MessageBridge {
    private val _messages = MutableSharedFlow<String>(replay = 0)
    val messages = _messages.asSharedFlow()
    
    suspend fun sendMessage(message: String) {
        _messages.emit(message)
    }
}
