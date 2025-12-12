package com.test.chatbot.data.memory

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Репозиторий для работы с долговременной памятью агента
 * 
 * Долговременная память хранит ТОЛЬКО summary предыдущего диалога.
 * При новой сессии агент получает этот summary как контекст.
 */
class MemoryRepository(context: Context) {
    
    private val database = MemoryDatabase.getInstance(context)
    private val dao = database.memoryDao()
    
    companion object {
        private const val SUMMARY_KEY = "previous_dialog_summary"
    }
    
    /**
     * Поток summary (для UI)
     */
    val summaryFlow: Flow<String?> = dao.getAllActiveMemories().map { entities ->
        entities.find { it.key == SUMMARY_KEY }?.value
    }
    
    /**
     * Проверить наличие сохранённого summary
     */
    suspend fun hasSavedSummary(): Boolean {
        return dao.findByKey(SUMMARY_KEY) != null
    }
    
    /**
     * Получить сохранённый summary предыдущего диалога
     */
    suspend fun getSavedSummary(): String? {
        return dao.findByKey(SUMMARY_KEY)?.value
    }
    
    /**
     * Сохранить summary диалога
     * Перезаписывает предыдущий summary
     */
    suspend fun saveSummary(summary: String) {
        val existing = dao.findByKey(SUMMARY_KEY)
        
        if (existing != null) {
            // Обновляем существующий
            dao.update(existing.copy(
                value = summary,
                updatedAt = System.currentTimeMillis()
            ))
        } else {
            // Создаём новый
            dao.insert(MemoryEntity(
                key = SUMMARY_KEY,
                value = summary,
                type = MemoryType.CONTEXT,
                source = "Автоматическое сохранение диалога"
            ))
        }
    }
    
    /**
     * Очистить сохранённый summary
     */
    suspend fun clearSummary() {
        dao.findByKey(SUMMARY_KEY)?.let { entity ->
            dao.deleteById(entity.id)
        }
    }
    
    /**
     * Получить форматированный контекст для агента
     */
    suspend fun getAgentContext(): String {
        val summary = getSavedSummary() ?: return ""
        
        return buildString {
            appendLine("=== КОНТЕКСТ ПРЕДЫДУЩЕГО ДИАЛОГА ===")
            appendLine()
            appendLine(summary)
            appendLine()
            appendLine("=====================================")
            appendLine("Учитывай эту информацию при ответах.")
        }
    }
}
