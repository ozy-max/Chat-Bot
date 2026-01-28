package com.test.chatbot.data

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Класс для анализа файлов данных (CSV, JSON, LOG)
 */
class DataAnalyzer(private val context: Context) {
    
    companion object {
        private const val TAG = "DataAnalyzer"
    }
    
    /**
     * Результат анализа файла
     */
    data class AnalysisResult(
        val fileName: String,
        val fileType: String,
        val size: Long,
        val preview: String,
        val rowCount: Int = 0,
        val columnNames: List<String> = emptyList(),
        val statistics: Map<String, Any> = emptyMap()
    )
    
    /**
     * Анализирует файл и возвращает результат
     */
    suspend fun analyzeFile(uri: Uri): Result<AnalysisResult> {
        return try {
            val contentResolver = context.contentResolver
            val cursor = contentResolver.query(uri, null, null, null, null)
            
            val fileName = cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) it.getString(nameIndex) else "unknown"
                } else "unknown"
            } ?: "unknown"
            
            val sizeIndex = cursor?.getColumnIndex(android.provider.OpenableColumns.SIZE)
            val size = if (sizeIndex != null && sizeIndex >= 0) {
                cursor.getLong(sizeIndex)
            } else 0L
            
            val fileType = when {
                fileName.endsWith(".csv", ignoreCase = true) -> "CSV"
                fileName.endsWith(".json", ignoreCase = true) -> "JSON"
                fileName.endsWith(".log", ignoreCase = true) -> "LOG"
                fileName.endsWith(".txt", ignoreCase = true) -> "TEXT"
                else -> "UNKNOWN"
            }
            
            // Читаем первые строки для preview
            val inputStream = contentResolver.openInputStream(uri)
            val preview = inputStream?.use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    val lines = mutableListOf<String>()
                    repeat(10) {
                        val line = reader.readLine() ?: return@repeat
                        lines.add(line)
                    }
                    lines.joinToString("\n")
                }
            } ?: ""
            
            val result = AnalysisResult(
                fileName = fileName,
                fileType = fileType,
                size = size,
                preview = preview,
                rowCount = preview.lines().size
            )
            
            Log.d(TAG, "File analyzed: $fileName ($fileType), size: $size bytes")
            Result.success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing file: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Создает аналитический промпт для LLM
     */
    fun createAnalyticalPrompt(analysis: AnalysisResult, question: String): String {
        return buildString {
            appendLine("📊 Анализ данных из файла: ${analysis.fileName}")
            appendLine()
            appendLine("Тип файла: ${analysis.fileType}")
            appendLine("Размер: ${analysis.size} байт")
            appendLine("Количество строк: ${analysis.rowCount}")
            appendLine()
            appendLine("Превью данных:")
            appendLine("```")
            appendLine(analysis.preview)
            appendLine("```")
            appendLine()
            appendLine("Вопрос пользователя: $question")
            appendLine()
            appendLine("Пожалуйста, проанализируй эти данные и ответь на вопрос.")
        }
    }
}
