package com.test.chatbot.data.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Сущность для хранения долговременной памяти агента
 * 
 * Типы памяти:
 * - FACT: факт о пользователе (имя, возраст, место работы и т.д.)
 * - PREFERENCE: предпочтения пользователя
 * - CONTEXT: контекстная информация из разговора
 * - NOTE: заметки, которые пользователь попросил запомнить
 */
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Тип памяти
    val type: MemoryType = MemoryType.FACT,
    
    // Ключ/категория (например, "имя", "город", "работа")
    val key: String,
    
    // Значение/содержимое памяти
    val value: String,
    
    // Источник (из какого сообщения извлечено)
    val source: String = "",
    
    // Уровень уверенности (0.0 - 1.0)
    val confidence: Float = 1.0f,
    
    // Время создания
    val createdAt: Long = System.currentTimeMillis(),
    
    // Время последнего обновления
    val updatedAt: Long = System.currentTimeMillis(),
    
    // Время последнего использования
    val lastUsedAt: Long = System.currentTimeMillis(),
    
    // Количество использований
    val usageCount: Int = 0,
    
    // Активна ли запись
    val isActive: Boolean = true
)

/**
 * Типы памяти
 */
enum class MemoryType {
    FACT,       // Факт о пользователе
    PREFERENCE, // Предпочтение
    CONTEXT,    // Контекст разговора
    NOTE        // Заметка пользователя
}

/**
 * Модель памяти для UI
 */
data class Memory(
    val id: Long = 0,
    val type: MemoryType = MemoryType.FACT,
    val key: String,
    val value: String,
    val confidence: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

/**
 * Преобразование Entity в UI модель
 */
fun MemoryEntity.toMemory() = Memory(
    id = id,
    type = type,
    key = key,
    value = value,
    confidence = confidence,
    createdAt = createdAt,
    isActive = isActive
)

/**
 * Преобразование UI модели в Entity
 */
fun Memory.toEntity() = MemoryEntity(
    id = id,
    type = type,
    key = key,
    value = value,
    confidence = confidence,
    createdAt = createdAt,
    isActive = isActive
)

/**
 * Состояние памяти для UI
 * 
 * Долговременная память хранит только summary предыдущего диалога
 */
data class MemoryState(
    val isEnabled: Boolean = true,
    val hasSummary: Boolean = false,
    val summaryPreview: String = "",
    val isLoading: Boolean = false
)

/**
 * Форматирование памяти для контекста агента
 */
fun List<Memory>.toAgentContext(): String {
    if (isEmpty()) return ""
    
    val grouped = groupBy { it.type }
    val sb = StringBuilder()
    
    sb.appendLine("=== ДОЛГОВРЕМЕННАЯ ПАМЯТЬ ===")
    
    // Факты о пользователе
    grouped[MemoryType.FACT]?.let { facts ->
        if (facts.isNotEmpty()) {
            sb.appendLine("\n📋 ФАКТЫ О ПОЛЬЗОВАТЕЛЕ:")
            facts.forEach { memory ->
                sb.appendLine("• ${memory.key}: ${memory.value}")
            }
        }
    }
    
    // Предпочтения
    grouped[MemoryType.PREFERENCE]?.let { prefs ->
        if (prefs.isNotEmpty()) {
            sb.appendLine("\n⭐ ПРЕДПОЧТЕНИЯ:")
            prefs.forEach { memory ->
                sb.appendLine("• ${memory.key}: ${memory.value}")
            }
        }
    }
    
    // Заметки
    grouped[MemoryType.NOTE]?.let { notes ->
        if (notes.isNotEmpty()) {
            sb.appendLine("\n📝 ЗАМЕТКИ:")
            notes.forEach { memory ->
                sb.appendLine("• ${memory.key}: ${memory.value}")
            }
        }
    }
    
    // Контекст
    grouped[MemoryType.CONTEXT]?.let { context ->
        if (context.isNotEmpty()) {
            sb.appendLine("\n🔍 КОНТЕКСТ:")
            context.forEach { memory ->
                sb.appendLine("• ${memory.value}")
            }
        }
    }
    
    sb.appendLine("\n=============================")
    sb.appendLine("Используй эту информацию в разговоре, обращайся к пользователю персонально.")
    
    return sb.toString()
}

