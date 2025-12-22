package com.test.chatbot.rag

import android.util.Log

/**
 * Сервис для разбиения текста на чанки (chunks)
 */
class TextChunker {
    
    companion object {
        private const val TAG = "TextChunker"
        
        // Настройки по умолчанию
        private const val DEFAULT_CHUNK_SIZE = 1000 // символов (увеличено с 500 для лучшего контекста)
        private const val DEFAULT_CHUNK_OVERLAP = 150 // перекрытие между чанками (увеличено для сохранения контекста)
    }
    
    /**
     * Разбить текст на чанки фиксированного размера
     */
    fun chunkBySize(
        text: String,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        overlap: Int = DEFAULT_CHUNK_OVERLAP
    ): List<TextChunk> {
        if (text.isBlank()) {
            return emptyList()
        }
        
        val chunks = mutableListOf<TextChunk>()
        var startIndex = 0
        var chunkIndex = 0
        
        while (startIndex < text.length) {
            val endIndex = minOf(startIndex + chunkSize, text.length)
            val chunkText = text.substring(startIndex, endIndex).trim()
            
            if (chunkText.isNotBlank()) {
                chunks.add(
                    TextChunk(
                        index = chunkIndex,
                        text = chunkText,
                        startPos = startIndex,
                        endPos = endIndex
                    )
                )
                chunkIndex++
            }
            
            // Двигаемся вперёд с учётом перекрытия
            startIndex += (chunkSize - overlap)
        }
        
        Log.i(TAG, "Создано ${chunks.size} чанков из текста длиной ${text.length}")
        return chunks
    }
    
    /**
     * Разбить текст по предложениям
     */
    fun chunkBySentences(
        text: String,
        sentencesPerChunk: Int = 5
    ): List<TextChunk> {
        if (text.isBlank()) {
            return emptyList()
        }
        
        // Разбиваем на предложения (простая реализация)
        val sentences = text.split(Regex("[.!?]+\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        
        val chunks = mutableListOf<TextChunk>()
        var startPos = 0
        
        for (i in sentences.indices step sentencesPerChunk) {
            val chunkSentences = sentences.subList(
                i,
                minOf(i + sentencesPerChunk, sentences.size)
            )
            
            val chunkText = chunkSentences.joinToString(". ") + "."
            val endPos = startPos + chunkText.length
            
            chunks.add(
                TextChunk(
                    index = chunks.size,
                    text = chunkText,
                    startPos = startPos,
                    endPos = endPos
                )
            )
            
            startPos = endPos
        }
        
        Log.i(TAG, "Создано ${chunks.size} чанков из ${sentences.size} предложений")
        return chunks
    }
    
    /**
     * Разбить текст по параграфам
     */
    fun chunkByParagraphs(text: String): List<TextChunk> {
        if (text.isBlank()) {
            return emptyList()
        }
        
        val paragraphs = text.split(Regex("\n\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        
        val chunks = mutableListOf<TextChunk>()
        var startPos = 0
        
        paragraphs.forEachIndexed { index, paragraph ->
            val endPos = startPos + paragraph.length
            
            chunks.add(
                TextChunk(
                    index = index,
                    text = paragraph,
                    startPos = startPos,
                    endPos = endPos
                )
            )
            
            startPos = endPos + 2 // +2 для учёта \n\n
        }
        
        Log.i(TAG, "Создано ${chunks.size} чанков из параграфов")
        return chunks
    }
    
    /**
     * Умное разбиение с учётом структуры
     */
    fun chunkSmart(
        text: String,
        maxChunkSize: Int = DEFAULT_CHUNK_SIZE
    ): List<TextChunk> {
        if (text.isBlank()) {
            return emptyList()
        }
        
        // Сначала пробуем по параграфам
        val paragraphs = text.split(Regex("\n\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        
        val chunks = mutableListOf<TextChunk>()
        var currentChunk = StringBuilder()
        var chunkStartPos = 0
        var currentPos = 0
        
        for (paragraph in paragraphs) {
            // Если параграф слишком большой, разбиваем его
            if (paragraph.length > maxChunkSize) {
                // Сохраняем текущий чанк если есть
                if (currentChunk.isNotEmpty()) {
                    chunks.add(
                        TextChunk(
                            index = chunks.size,
                            text = currentChunk.toString().trim(),
                            startPos = chunkStartPos,
                            endPos = currentPos
                        )
                    )
                    currentChunk.clear()
                }
                
                // Разбиваем большой параграф на части
                val subChunks = chunkBySize(paragraph, maxChunkSize, DEFAULT_CHUNK_OVERLAP)
                subChunks.forEach { subChunk ->
                    chunks.add(
                        TextChunk(
                            index = chunks.size,
                            text = subChunk.text,
                            startPos = currentPos + subChunk.startPos,
                            endPos = currentPos + subChunk.endPos
                        )
                    )
                }
                
                currentPos += paragraph.length + 2
                chunkStartPos = currentPos
            } else {
                // Проверяем, влезет ли параграф в текущий чанк
                if (currentChunk.length + paragraph.length > maxChunkSize && currentChunk.isNotEmpty()) {
                    // Сохраняем текущий чанк
                    chunks.add(
                        TextChunk(
                            index = chunks.size,
                            text = currentChunk.toString().trim(),
                            startPos = chunkStartPos,
                            endPos = currentPos
                        )
                    )
                    currentChunk.clear()
                    chunkStartPos = currentPos
                }
                
                // Добавляем параграф к текущему чанку
                if (currentChunk.isNotEmpty()) {
                    currentChunk.append("\n\n")
                }
                currentChunk.append(paragraph)
                currentPos += paragraph.length + 2
            }
        }
        
        // Сохраняем последний чанк
        if (currentChunk.isNotEmpty()) {
            chunks.add(
                TextChunk(
                    index = chunks.size,
                    text = currentChunk.toString().trim(),
                    startPos = chunkStartPos,
                    endPos = currentPos
                )
            )
        }
        
        Log.i(TAG, "Умное разбиение: создано ${chunks.size} чанков")
        return chunks
    }
    
    /**
     * Разбить код на чанки (с учётом синтаксиса)
     */
    fun chunkCode(
        code: String,
        language: String = "kotlin"
    ): List<TextChunk> {
        if (code.isBlank()) {
            return emptyList()
        }
        
        // Разбиваем по функциям/классам (простая эвристика)
        val chunks = mutableListOf<TextChunk>()
        val lines = code.split("\n")
        
        var currentChunk = StringBuilder()
        var chunkStartLine = 0
        var currentLine = 0
        var braceLevel = 0
        
        for (line in lines) {
            currentChunk.append(line).append("\n")
            
            // Подсчитываем фигурные скобки
            braceLevel += line.count { it == '{' }
            braceLevel -= line.count { it == '}' }
            
            // Если вернулись на уровень 0, это конец функции/класса
            if (braceLevel == 0 && currentChunk.length > 50) {
                chunks.add(
                    TextChunk(
                        index = chunks.size,
                        text = currentChunk.toString().trim(),
                        startPos = chunkStartLine,
                        endPos = currentLine,
                        metadata = mapOf("language" to language)
                    )
                )
                currentChunk.clear()
                chunkStartLine = currentLine + 1
            }
            
            currentLine++
        }
        
        // Сохраняем остаток
        if (currentChunk.isNotEmpty()) {
            chunks.add(
                TextChunk(
                    index = chunks.size,
                    text = currentChunk.toString().trim(),
                    startPos = chunkStartLine,
                    endPos = currentLine,
                    metadata = mapOf("language" to language)
                )
            )
        }
        
        Log.i(TAG, "Создано ${chunks.size} чанков кода ($language)")
        return chunks
    }
}

/**
 * Представление чанка текста
 */
data class TextChunk(
    val index: Int,
    val text: String,
    val startPos: Int,
    val endPos: Int,
    val metadata: Map<String, String> = emptyMap()
) {
    val length: Int get() = text.length
    
    fun toJson(): Map<String, Any> {
        return mapOf(
            "index" to index,
            "text" to text,
            "startPos" to startPos,
            "endPos" to endPos,
            "length" to length,
            "metadata" to metadata
        )
    }
}

