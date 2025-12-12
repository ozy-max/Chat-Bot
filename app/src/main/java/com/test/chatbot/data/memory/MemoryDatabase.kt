package com.test.chatbot.data.memory

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * Конвертеры для Room
 */
class Converters {
    @TypeConverter
    fun fromMemoryType(type: MemoryType): String = type.name
    
    @TypeConverter
    fun toMemoryType(value: String): MemoryType = MemoryType.valueOf(value)
}

/**
 * База данных для хранения долговременной памяти агента
 */
@Database(
    entities = [MemoryEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MemoryDatabase : RoomDatabase() {
    
    abstract fun memoryDao(): MemoryDao
    
    companion object {
        private const val DATABASE_NAME = "agent_memory.db"
        
        @Volatile
        private var INSTANCE: MemoryDatabase? = null
        
        /**
         * Получить экземпляр базы данных (синглтон)
         */
        fun getInstance(context: Context): MemoryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MemoryDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration() // При изменении схемы пересоздаём БД
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

