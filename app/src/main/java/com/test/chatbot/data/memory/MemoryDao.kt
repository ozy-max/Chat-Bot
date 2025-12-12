package com.test.chatbot.data.memory

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO для работы с памятью агента
 */
@Dao
interface MemoryDao {
    
    /**
     * Получить все активные записи памяти
     */
    @Query("SELECT * FROM memories WHERE isActive = 1 ORDER BY updatedAt DESC")
    fun getAllActiveMemories(): Flow<List<MemoryEntity>>
    
    /**
     * Получить все записи памяти (включая неактивные)
     */
    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>
    
    /**
     * Получить записи по типу
     */
    @Query("SELECT * FROM memories WHERE type = :type AND isActive = 1 ORDER BY updatedAt DESC")
    fun getMemoriesByType(type: MemoryType): Flow<List<MemoryEntity>>
    
    /**
     * Поиск по ключу
     */
    @Query("SELECT * FROM memories WHERE memory_key LIKE '%' || :query || '%' AND isActive = 1")
    fun searchByKey(query: String): Flow<List<MemoryEntity>>
    
    /**
     * Поиск по значению
     */
    @Query("SELECT * FROM memories WHERE memory_value LIKE '%' || :query || '%' AND isActive = 1")
    fun searchByValue(query: String): Flow<List<MemoryEntity>>
    
    /**
     * Найти запись по ключу (точное совпадение)
     */
    @Query("SELECT * FROM memories WHERE memory_key = :key AND isActive = 1 LIMIT 1")
    suspend fun findByKey(key: String): MemoryEntity?
    
    /**
     * Получить количество активных записей
     */
    @Query("SELECT COUNT(*) FROM memories WHERE isActive = 1")
    fun getActiveCount(): Flow<Int>
    
    /**
     * Вставить новую запись
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: MemoryEntity): Long
    
    /**
     * Вставить несколько записей
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(memories: List<MemoryEntity>)
    
    /**
     * Обновить запись
     */
    @Update
    suspend fun update(memory: MemoryEntity)
    
    /**
     * Обновить время использования и счетчик
     */
    @Query("""
        UPDATE memories 
        SET lastUsedAt = :timestamp, usageCount = usageCount + 1 
        WHERE id = :memoryId
    """)
    suspend fun updateUsage(memoryId: Long, timestamp: Long = System.currentTimeMillis())
    
    /**
     * Деактивировать запись (мягкое удаление)
     */
    @Query("UPDATE memories SET isActive = 0 WHERE id = :memoryId")
    suspend fun deactivate(memoryId: Long)
    
    /**
     * Активировать запись
     */
    @Query("UPDATE memories SET isActive = 1 WHERE id = :memoryId")
    suspend fun activate(memoryId: Long)
    
    /**
     * Удалить запись (физическое удаление)
     */
    @Delete
    suspend fun delete(memory: MemoryEntity)
    
    /**
     * Удалить по id
     */
    @Query("DELETE FROM memories WHERE id = :memoryId")
    suspend fun deleteById(memoryId: Long)
    
    /**
     * Очистить все записи
     */
    @Query("DELETE FROM memories")
    suspend fun deleteAll()
    
    /**
     * Получить записи для контекста (самые важные и часто используемые)
     * Приоритет: FACT > PREFERENCE > NOTE > CONTEXT
     * Сортировка: по частоте использования и времени обновления
     */
    @Query("""
        SELECT * FROM memories 
        WHERE isActive = 1 
        ORDER BY 
            CASE type 
                WHEN 'FACT' THEN 1 
                WHEN 'PREFERENCE' THEN 2 
                WHEN 'NOTE' THEN 3 
                WHEN 'CONTEXT' THEN 4 
            END,
            usageCount DESC,
            updatedAt DESC
        LIMIT :limit
    """)
    suspend fun getMemoriesForContext(limit: Int = 20): List<MemoryEntity>
}

