package com.test.chatbot.mcp.server

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class FileManagerService(private val context: Context) {
    
    companion object {
        private const val TAG = "FileManagerService"
        private const val MAX_FILE_SIZE = 10 * 1024 * 1024 // 10 MB
    }
    
    private val rootDir: File by lazy {
        context.filesDir
    }
    
    /**
     * Получить список файлов и директорий
     */
    suspend fun listDirectory(path: String = ""): Result<String> = withContext(Dispatchers.IO) {
        try {
            val targetDir = if (path.isBlank()) {
                rootDir
            } else {
                File(rootDir, path)
            }
            
            if (!targetDir.exists()) {
                return@withContext Result.failure(Exception("Директория не существует: $path"))
            }
            
            if (!targetDir.isDirectory) {
                return@withContext Result.failure(Exception("Это не директория: $path"))
            }
            
            val files = targetDir.listFiles()?.sortedWith(
                compareBy<File> { !it.isDirectory }.thenBy { it.name }
            ) ?: emptyList()
            
            val info = buildString {
                append("📁 ${targetDir.absolutePath}\n")
                append("━━━━━━━━━━━━━━━━━━━━\n\n")
                
                if (files.isEmpty()) {
                    append("Пусто\n")
                } else {
                    files.forEach { file ->
                        val icon = if (file.isDirectory) "📁" else "📄"
                        val size = if (file.isFile) formatFileSize(file.length()) else ""
                        val date = formatDate(file.lastModified())
                        
                        append("$icon ${file.name}\n")
                        if (size.isNotBlank()) {
                            append("   Размер: $size\n")
                        }
                        append("   Изменён: $date\n\n")
                    }
                }
                
                append("\nВсего: ${files.count { it.isDirectory }} папок, ${files.count { it.isFile }} файлов")
            }
            
            Log.i(TAG, "✅ Список директории получен: $path")
            Result.success(info)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка получения списка: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Прочитать содержимое файла
     */
    suspend fun readFile(path: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(rootDir, path)
            
            if (!file.exists()) {
                return@withContext Result.failure(Exception("Файл не существует: $path"))
            }
            
            if (!file.isFile) {
                return@withContext Result.failure(Exception("Это не файл: $path"))
            }
            
            if (file.length() > MAX_FILE_SIZE) {
                return@withContext Result.failure(
                    Exception("Файл слишком большой (>${formatFileSize(MAX_FILE_SIZE.toLong())}): $path")
                )
            }
            
            val content = file.readText()
            
            val info = buildString {
                append("📄 ${file.name}\n")
                append("━━━━━━━━━━━━━━━━━━━━\n")
                append("Размер: ${formatFileSize(file.length())}\n")
                append("Изменён: ${formatDate(file.lastModified())}\n")
                append("━━━━━━━━━━━━━━━━━━━━\n\n")
                append(content)
            }
            
            Log.i(TAG, "✅ Файл прочитан: $path")
            Result.success(info)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка чтения файла: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Записать содержимое в файл
     */
    suspend fun writeFile(path: String, content: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(rootDir, path)
            
            // Создаём родительские директории если нужно
            file.parentFile?.mkdirs()
            
            file.writeText(content)
            
            Log.i(TAG, "✅ Файл записан: $path (${formatFileSize(file.length())})")
            Result.success("✅ Файл успешно сохранён:\n${file.absolutePath}\nРазмер: ${formatFileSize(file.length())}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка записи файла: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Удалить файл или директорию
     */
    suspend fun deleteFile(path: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(rootDir, path)
            
            if (!file.exists()) {
                return@withContext Result.failure(Exception("Файл не существует: $path"))
            }
            
            val isDirectory = file.isDirectory
            val deleted = file.deleteRecursively()
            
            if (deleted) {
                Log.i(TAG, "✅ Удалено: $path")
                Result.success("✅ ${if (isDirectory) "Директория" else "Файл"} успешно удалён: $path")
            } else {
                Result.failure(Exception("Не удалось удалить: $path"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка удаления: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Создать директорию
     */
    suspend fun createDirectory(path: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val dir = File(rootDir, path)
            
            if (dir.exists()) {
                return@withContext Result.failure(Exception("Директория уже существует: $path"))
            }
            
            val created = dir.mkdirs()
            
            if (created) {
                Log.i(TAG, "✅ Директория создана: $path")
                Result.success("✅ Директория создана: ${dir.absolutePath}")
            } else {
                Result.failure(Exception("Не удалось создать директорию: $path"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка создания директории: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Переместить или переименовать файл
     */
    suspend fun moveFile(sourcePath: String, destPath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(rootDir, sourcePath)
            val destFile = File(rootDir, destPath)
            
            if (!sourceFile.exists()) {
                return@withContext Result.failure(Exception("Файл не существует: $sourcePath"))
            }
            
            if (destFile.exists()) {
                return@withContext Result.failure(Exception("Файл уже существует: $destPath"))
            }
            
            // Создаём родительские директории если нужно
            destFile.parentFile?.mkdirs()
            
            val moved = sourceFile.renameTo(destFile)
            
            if (moved) {
                Log.i(TAG, "✅ Перемещено: $sourcePath -> $destPath")
                Result.success("✅ Файл успешно перемещён:\nИз: $sourcePath\nВ: $destPath")
            } else {
                Result.failure(Exception("Не удалось переместить файл"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка перемещения: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Копировать файл
     */
    suspend fun copyFile(sourcePath: String, destPath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(rootDir, sourcePath)
            val destFile = File(rootDir, destPath)
            
            if (!sourceFile.exists()) {
                return@withContext Result.failure(Exception("Файл не существует: $sourcePath"))
            }
            
            if (!sourceFile.isFile) {
                return@withContext Result.failure(Exception("Можно копировать только файлы: $sourcePath"))
            }
            
            if (destFile.exists()) {
                return@withContext Result.failure(Exception("Файл уже существует: $destPath"))
            }
            
            // Создаём родительские директории если нужно
            destFile.parentFile?.mkdirs()
            
            sourceFile.copyTo(destFile)
            
            Log.i(TAG, "✅ Скопировано: $sourcePath -> $destPath")
            Result.success("✅ Файл успешно скопирован:\nИз: $sourcePath\nВ: $destPath")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка копирования: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Поиск файлов по имени
     */
    suspend fun searchFiles(pattern: String, searchPath: String = ""): Result<String> = withContext(Dispatchers.IO) {
        try {
            val startDir = if (searchPath.isBlank()) {
                rootDir
            } else {
                File(rootDir, searchPath)
            }
            
            if (!startDir.exists() || !startDir.isDirectory) {
                return@withContext Result.failure(Exception("Директория не существует: $searchPath"))
            }
            
            val results = mutableListOf<File>()
            searchRecursive(startDir, pattern.lowercase(), results)
            
            val info = buildString {
                append("🔍 Поиск: \"$pattern\"\n")
                append("📁 В: ${startDir.absolutePath}\n")
                append("━━━━━━━━━━━━━━━━━━━━\n\n")
                
                if (results.isEmpty()) {
                    append("Ничего не найдено\n")
                } else {
                    results.forEach { file ->
                        val relativePath = file.relativeTo(rootDir).path
                        val icon = if (file.isDirectory) "📁" else "📄"
                        val size = if (file.isFile) formatFileSize(file.length()) else ""
                        
                        append("$icon $relativePath\n")
                        if (size.isNotBlank()) {
                            append("   $size\n")
                        }
                    }
                    
                    append("\nНайдено: ${results.size}")
                }
            }
            
            Log.i(TAG, "✅ Поиск завершён: найдено ${results.size} файлов")
            Result.success(info)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка поиска: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    private fun searchRecursive(dir: File, pattern: String, results: MutableList<File>) {
        dir.listFiles()?.forEach { file ->
            if (file.name.lowercase().contains(pattern)) {
                results.add(file)
            }
            if (file.isDirectory) {
                searchRecursive(file, pattern, results)
            }
        }
    }
    
    /**
     * Получить информацию о файле
     */
    suspend fun getFileInfo(path: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(rootDir, path)
            
            if (!file.exists()) {
                return@withContext Result.failure(Exception("Файл не существует: $path"))
            }
            
            val info = buildString {
                val icon = if (file.isDirectory) "📁" else "📄"
                append("$icon ${file.name}\n")
                append("━━━━━━━━━━━━━━━━━━━━\n")
                append("Путь: ${file.absolutePath}\n")
                append("Тип: ${if (file.isDirectory) "Директория" else "Файл"}\n")
                
                if (file.isFile) {
                    append("Размер: ${formatFileSize(file.length())}\n")
                }
                
                append("Создан: ${formatDate(file.lastModified())}\n")
                append("Можно читать: ${if (file.canRead()) "✅" else "❌"}\n")
                append("Можно писать: ${if (file.canWrite()) "✅" else "❌"}\n")
                append("Можно выполнять: ${if (file.canExecute()) "✅" else "❌"}\n")
                
                if (file.isDirectory) {
                    val filesCount = file.listFiles()?.size ?: 0
                    append("Элементов: $filesCount\n")
                }
            }
            
            Result.success(info)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка получения информации: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    // Вспомогательные функции
    
    private fun formatFileSize(size: Long): String {
        val df = DecimalFormat("#.##")
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${df.format(size / 1024.0)} KB"
            size < 1024 * 1024 * 1024 -> "${df.format(size / (1024.0 * 1024.0))} MB"
            else -> "${df.format(size / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }
    
    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}


