package com.test.chatbot.mcp.server

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class FileStorageService(private val context: Context) {
    
    companion object {
        private const val TAG = "FileStorageService"
        private const val PIPELINE_DIR = "pipeline_results"
    }
    
    private val pipelineDir: File by lazy {
        File(context.filesDir, PIPELINE_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    suspend fun saveToFile(
        content: String,
        filename: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Начинаем сохранение PDF файла...")
            Log.i(TAG, "Директория: ${pipelineDir.absolutePath}")
            Log.i(TAG, "Директория существует: ${pipelineDir.exists()}")
            Log.i(TAG, "Можно записывать: ${pipelineDir.canWrite()}")
            
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val actualFilename = filename ?: "pipeline_$timestamp.pdf"
            
            val file = File(pipelineDir, actualFilename)
            Log.i(TAG, "Полный путь к файлу: ${file.absolutePath}")
            
            // Создаём PDF документ
            val pdfDocument = PdfDocument()
            val pageWidth = 595 // A4 width in points
            val pageHeight = 842 // A4 height in points
            val margin = 40f
            val lineHeight = 20f
            
            var currentY = margin
            var pageNumber = 1
            
            // Создаём первую страницу
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas
            
            // Настройка текста
            val paint = Paint().apply {
                textSize = 12f
                isAntiAlias = true
            }
            
            val titlePaint = Paint().apply {
                textSize = 16f
                isFakeBoldText = true
                isAntiAlias = true
            }
            
            val headerPaint = Paint().apply {
                textSize = 14f
                isFakeBoldText = true
                isAntiAlias = true
            }
            
            // Разбиваем текст на строки
            val lines = content.lines()
            
            for (line in lines) {
                // Проверяем нужна ли новая страница
                if (currentY + lineHeight > pageHeight - margin) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    
                    val newPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(newPageInfo)
                    canvas = page.canvas
                    currentY = margin
                }
                
                // Выбираем стиль в зависимости от содержимого строки
                val currentPaint = when {
                    line.contains("РЕЗУЛЬТАТЫ ПОИСКА И АНАЛИЗА") -> titlePaint
                    line.contains("НАЙДЕННЫЕ СТАТЬИ") || 
                    line.contains("СУММАРИЗАЦИЯ") ||
                    line.contains("КОНЕЦ ОТЧЁТА") -> headerPaint
                    else -> paint
                }
                
                // Обрабатываем длинные строки
                if (line.length > 80) {
                    val words = line.split(" ")
                    var currentLine = ""
                    
                    for (word in words) {
                        val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                        val textWidth = currentPaint.measureText(testLine)
                        
                        if (textWidth > pageWidth - 2 * margin) {
                            canvas.drawText(currentLine, margin, currentY, currentPaint)
                            currentY += lineHeight
                            currentLine = word
                            
                            // Проверяем нужна ли новая страница
                            if (currentY + lineHeight > pageHeight - margin) {
                                pdfDocument.finishPage(page)
                                pageNumber++
                                
                                val newPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                                page = pdfDocument.startPage(newPageInfo)
                                canvas = page.canvas
                                currentY = margin
                            }
                        } else {
                            currentLine = testLine
                        }
                    }
                    
                    if (currentLine.isNotEmpty()) {
                        canvas.drawText(currentLine, margin, currentY, currentPaint)
                        currentY += lineHeight
                    }
                } else {
                    canvas.drawText(line, margin, currentY, currentPaint)
                    currentY += lineHeight
                }
            }
            
            pdfDocument.finishPage(page)
            
            // Сохраняем PDF в файл
            FileOutputStream(file).use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }
            pdfDocument.close()
            
            Log.i(TAG, "PDF файл записан. Размер: ${file.length()} байт")
            Log.i(TAG, "Файл существует: ${file.exists()}")
            Log.i(TAG, "✅ PDF файл сохранён: ${file.absolutePath}")
            
            // Проверяем список файлов в директории
            val filesInDir = pipelineDir.listFiles()
            Log.i(TAG, "Файлов в директории: ${filesInDir?.size ?: 0}")
            filesInDir?.forEach {
                Log.i(TAG, "  - ${it.name} (${it.length()} байт)")
            }
            
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка сохранения PDF файла: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    suspend fun readFile(filename: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(pipelineDir, filename)
            if (!file.exists()) {
                return@withContext Result.failure(Exception("Файл не найден: $filename"))
            }
            
            val content = file.readText()
            Log.i(TAG, "✅ Файл прочитан: ${file.absolutePath}")
            Result.success(content)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка чтения файла: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    suspend fun listFiles(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val files = pipelineDir.listFiles()?.map { it.name }?.sorted() ?: emptyList()
            Log.i(TAG, "📋 Найдено файлов: ${files.size}")
            Result.success(files)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка получения списка файлов: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    suspend fun deleteFile(filename: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val file = File(pipelineDir, filename)
            val deleted = file.delete()
            
            if (deleted) {
                Log.i(TAG, "✅ Файл удалён: $filename")
            } else {
                Log.w(TAG, "⚠️ Не удалось удалить файл: $filename")
            }
            
            Result.success(deleted)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка удаления файла: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    fun getStorageDir(): String = pipelineDir.absolutePath
}

