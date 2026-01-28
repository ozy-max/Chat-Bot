package com.test.chatbot.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale

/**
 * Сервис для распознавания речи
 * Использует Android SpeechRecognizer API
 */
class SpeechRecognitionService(private val context: Context) {
    
    companion object {
        private const val TAG = "SpeechRecognitionService"
    }
    
    private var speechRecognizer: SpeechRecognizer? = null
    
    /**
     * Результат распознавания речи
     */
    sealed class RecognitionResult {
        data class Success(val text: String) : RecognitionResult()
        data class Error(val error: String) : RecognitionResult()
        data class PartialResult(val text: String) : RecognitionResult()
        object ReadyForSpeech : RecognitionResult()
        object BeginningOfSpeech : RecognitionResult()
        object EndOfSpeech : RecognitionResult()
    }
    
    /**
     * Проверка доступности распознавания речи на устройстве
     */
    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }
    
    /**
     * Запуск распознавания речи
     * 
     * @param language Язык распознавания (по умолчанию русский)
     * @return Flow с результатами распознавания
     */
    fun startListening(language: String = "ru-RU"): Flow<RecognitionResult> = callbackFlow {
        Log.d(TAG, "🎤 Attempting to start speech recognition...")
        
        if (!isAvailable()) {
            val error = "Распознавание речи недоступно на этом устройстве. " +
                "SpeechRecognizer.isRecognitionAvailable() вернул false. " +
                "Это может быть из-за: отсутствия Google Services, работы на эмуляторе, или отсутствия поддержки на устройстве."
            Log.e(TAG, "❌ $error")
            trySend(RecognitionResult.Error(error))
            close()
            return@callbackFlow
        }
        
        Log.d(TAG, "✅ Speech recognition is available")
        
        // Создаем SpeechRecognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "✅ Ready for speech - START SPEAKING!")
                    trySend(RecognitionResult.ReadyForSpeech)
                }
                
                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "🗣️ Beginning of speech detected!")
                    trySend(RecognitionResult.BeginningOfSpeech)
                }
                
                override fun onRmsChanged(rmsdB: Float) {
                    // Уровень громкости (можно использовать для визуализации)
                }
                
                override fun onBufferReceived(buffer: ByteArray?) {
                    // Получены аудио данные
                }
                
                override fun onEndOfSpeech() {
                    Log.d(TAG, "End of speech")
                    trySend(RecognitionResult.EndOfSpeech)
                }
                
                override fun onError(error: Int) {
                    val errorMessage = getErrorText(error)
                    Log.e(TAG, "Recognition error: $errorMessage")
                    trySend(RecognitionResult.Error(errorMessage))
                    close()
                }
                
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    Log.d(TAG, "📦 onResults called, matches: ${matches?.size ?: 0}")
                    
                    if (matches != null && matches.isNotEmpty()) {
                        val recognizedText = matches[0]
                        Log.d(TAG, "✅ Recognition SUCCESS: '$recognizedText'")
                        Log.d(TAG, "📝 All matches: ${matches.joinToString(", ")}")
                        trySend(RecognitionResult.Success(recognizedText))
                    } else {
                        val error = "Не удалось распознать речь (пустой результат)"
                        Log.e(TAG, "❌ $error")
                        trySend(RecognitionResult.Error(error))
                    }
                    close()
                }
                
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (matches != null && matches.isNotEmpty()) {
                        val partialText = matches[0]
                        Log.d(TAG, "Partial result: $partialText")
                        trySend(RecognitionResult.PartialResult(partialText))
                    }
                }
                
                override fun onEvent(eventType: Int, params: Bundle?) {
                    // Дополнительные события
                }
            })
        }
        
        // Создаем intent для распознавания
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            
            // Добавляем подсказки для лучшего распознавания
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        
        // Запускаем распознавание
        try {
            speechRecognizer?.startListening(intent)
            Log.d(TAG, "Speech recognition started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recognition: ${e.message}")
            trySend(RecognitionResult.Error("Ошибка запуска распознавания: ${e.message}"))
            close()
        }
        
        awaitClose {
            Log.d(TAG, "Stopping speech recognition")
            stopListening()
        }
    }
    
    /**
     * Остановка распознавания речи
     */
    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
            Log.d(TAG, "Speech recognition stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recognition: ${e.message}")
        }
    }
    
    /**
     * Получить текстовое описание ошибки
     */
    private fun getErrorText(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Ошибка записи аудио"
            SpeechRecognizer.ERROR_CLIENT -> "Ошибка клиента"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Недостаточно разрешений (требуется RECORD_AUDIO)"
            SpeechRecognizer.ERROR_NETWORK -> "Ошибка сети"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Тайм-аут сети"
            SpeechRecognizer.ERROR_NO_MATCH -> "Не удалось распознать речь"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознаватель занят"
            SpeechRecognizer.ERROR_SERVER -> "Ошибка сервера"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Не обнаружена речь"
            else -> "Неизвестная ошибка ($errorCode)"
        }
    }
    
    /**
     * Освобождение ресурсов
     */
    fun release() {
        stopListening()
    }
}
