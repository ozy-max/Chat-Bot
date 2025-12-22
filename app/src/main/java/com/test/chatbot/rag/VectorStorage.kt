package com.test.chatbot.rag

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Хранилище векторов на основе SQLite
 */
class VectorStorage(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    
    companion object {
        private const val TAG = "VectorStorage"
        private const val DATABASE_NAME = "vector_store.db"
        private const val DATABASE_VERSION = 1
        
        // Таблицы
        private const val TABLE_DOCUMENTS = "documents"
        private const val TABLE_CHUNKS = "chunks"
        private const val TABLE_EMBEDDINGS = "embeddings"
        
        // Колонки для documents
        private const val COL_DOC_ID = "doc_id"
        private const val COL_DOC_NAME = "doc_name"
        private const val COL_DOC_TYPE = "doc_type"
        private const val COL_DOC_PATH = "doc_path"
        private const val COL_DOC_CONTENT = "doc_content"
        private const val COL_DOC_METADATA = "doc_metadata"
        private const val COL_DOC_CREATED_AT = "created_at"
        
        // Колонки для chunks
        private const val COL_CHUNK_ID = "chunk_id"
        private const val COL_CHUNK_DOC_ID = "chunk_doc_id"
        private const val COL_CHUNK_INDEX = "chunk_index"
        private const val COL_CHUNK_TEXT = "chunk_text"
        private const val COL_CHUNK_START_POS = "start_pos"
        private const val COL_CHUNK_END_POS = "end_pos"
        
        // Колонки для embeddings
        private const val COL_EMB_ID = "emb_id"
        private const val COL_EMB_CHUNK_ID = "emb_chunk_id"
        private const val COL_EMB_VECTOR = "emb_vector"
        private const val COL_EMB_DIMENSION = "emb_dimension"
    }
    
    private val gson = Gson()
    
    override fun onCreate(db: SQLiteDatabase) {
        // Создаём таблицу документов
        db.execSQL("""
            CREATE TABLE $TABLE_DOCUMENTS (
                $COL_DOC_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_DOC_NAME TEXT NOT NULL,
                $COL_DOC_TYPE TEXT NOT NULL,
                $COL_DOC_PATH TEXT,
                $COL_DOC_CONTENT TEXT,
                $COL_DOC_METADATA TEXT,
                $COL_DOC_CREATED_AT INTEGER NOT NULL
            )
        """)
        
        // Создаём таблицу чанков
        db.execSQL("""
            CREATE TABLE $TABLE_CHUNKS (
                $COL_CHUNK_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_CHUNK_DOC_ID INTEGER NOT NULL,
                $COL_CHUNK_INDEX INTEGER NOT NULL,
                $COL_CHUNK_TEXT TEXT NOT NULL,
                $COL_CHUNK_START_POS INTEGER NOT NULL,
                $COL_CHUNK_END_POS INTEGER NOT NULL,
                FOREIGN KEY ($COL_CHUNK_DOC_ID) REFERENCES $TABLE_DOCUMENTS($COL_DOC_ID) ON DELETE CASCADE
            )
        """)
        
        // Создаём таблицу эмбеддингов
        db.execSQL("""
            CREATE TABLE $TABLE_EMBEDDINGS (
                $COL_EMB_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_EMB_CHUNK_ID INTEGER NOT NULL,
                $COL_EMB_VECTOR TEXT NOT NULL,
                $COL_EMB_DIMENSION INTEGER NOT NULL,
                FOREIGN KEY ($COL_EMB_CHUNK_ID) REFERENCES $TABLE_CHUNKS($COL_CHUNK_ID) ON DELETE CASCADE
            )
        """)
        
        // Создаём индексы для быстрого поиска
        db.execSQL("CREATE INDEX idx_chunks_doc_id ON $TABLE_CHUNKS($COL_CHUNK_DOC_ID)")
        db.execSQL("CREATE INDEX idx_embeddings_chunk_id ON $TABLE_EMBEDDINGS($COL_EMB_CHUNK_ID)")
        
        Log.i(TAG, "✅ База данных создана")
    }
    
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_EMBEDDINGS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CHUNKS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_DOCUMENTS")
        onCreate(db)
    }
    
    /**
     * Сохранить документ
     */
    suspend fun saveDocument(document: Document): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val db = writableDatabase
            
            val values = ContentValues().apply {
                put(COL_DOC_NAME, document.name)
                put(COL_DOC_TYPE, document.type)
                put(COL_DOC_PATH, document.path)
                put(COL_DOC_CONTENT, document.content)
                put(COL_DOC_METADATA, gson.toJson(document.metadata))
                put(COL_DOC_CREATED_AT, System.currentTimeMillis())
            }
            
            val docId = db.insert(TABLE_DOCUMENTS, null, values)
            
            if (docId > 0) {
                Log.i(TAG, "✅ Документ сохранён: ${document.name} (ID: $docId)")
                Result.success(docId)
            } else {
                Result.failure(Exception("Не удалось сохранить документ"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка сохранения документа: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Сохранить чанк
     */
    suspend fun saveChunk(docId: Long, chunk: TextChunk): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val db = writableDatabase
            
            val values = ContentValues().apply {
                put(COL_CHUNK_DOC_ID, docId)
                put(COL_CHUNK_INDEX, chunk.index)
                put(COL_CHUNK_TEXT, chunk.text)
                put(COL_CHUNK_START_POS, chunk.startPos)
                put(COL_CHUNK_END_POS, chunk.endPos)
            }
            
            val chunkId = db.insert(TABLE_CHUNKS, null, values)
            
            if (chunkId > 0) {
                Result.success(chunkId)
            } else {
                Result.failure(Exception("Не удалось сохранить чанк"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка сохранения чанка: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Сохранить эмбеддинг
     */
    suspend fun saveEmbedding(chunkId: Long, embedding: FloatArray): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val db = writableDatabase
            
            // Сериализуем вектор в JSON
            val vectorJson = gson.toJson(embedding.toList())
            
            val values = ContentValues().apply {
                put(COL_EMB_CHUNK_ID, chunkId)
                put(COL_EMB_VECTOR, vectorJson)
                put(COL_EMB_DIMENSION, embedding.size)
            }
            
            val embId = db.insert(TABLE_EMBEDDINGS, null, values)
            
            if (embId > 0) {
                Result.success(embId)
            } else {
                Result.failure(Exception("Не удалось сохранить эмбеддинг"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка сохранения эмбеддинга: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Поиск похожих векторов (косинусное сходство)
     */
    suspend fun searchSimilar(
        queryEmbedding: FloatArray,
        topK: Int = 5
    ): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "🔍 Поиск с query embedding размерности: ${queryEmbedding.size}D")
            
            val db = readableDatabase
            val results = mutableListOf<SearchResult>()
            
            // Получаем все эмбеддинги
            val cursor = db.rawQuery("""
                SELECT 
                    e.$COL_EMB_ID,
                    e.$COL_EMB_VECTOR,
                    c.$COL_CHUNK_ID,
                    c.$COL_CHUNK_TEXT,
                    c.$COL_CHUNK_DOC_ID,
                    d.$COL_DOC_NAME,
                    d.$COL_DOC_TYPE
                FROM $TABLE_EMBEDDINGS e
                JOIN $TABLE_CHUNKS c ON e.$COL_EMB_CHUNK_ID = c.$COL_CHUNK_ID
                JOIN $TABLE_DOCUMENTS d ON c.$COL_CHUNK_DOC_ID = d.$COL_DOC_ID
            """, null)
            
            while (cursor.moveToNext()) {
                val vectorJson = cursor.getString(cursor.getColumnIndexOrThrow(COL_EMB_VECTOR))
                val vector = gson.fromJson(vectorJson, Array<Double>::class.java)
                    .map { it.toFloat() }
                    .toFloatArray()
                
                // Вычисляем косинусное сходство
                val similarity = cosineSimilarity(queryEmbedding, vector)
                
                // Логируем если размерности не совпадают
                if (queryEmbedding.size != vector.size) {
                    val docName = cursor.getString(cursor.getColumnIndexOrThrow(COL_DOC_NAME))
                    Log.w(TAG, "⚠️ Несовпадение размерностей для $docName: query=${queryEmbedding.size}D, stored=${vector.size}D")
                }
                
                results.add(
                    SearchResult(
                        chunkId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CHUNK_ID)),
                        chunkText = cursor.getString(cursor.getColumnIndexOrThrow(COL_CHUNK_TEXT)),
                        docId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CHUNK_DOC_ID)),
                        docName = cursor.getString(cursor.getColumnIndexOrThrow(COL_DOC_NAME)),
                        docType = cursor.getString(cursor.getColumnIndexOrThrow(COL_DOC_TYPE)),
                        similarity = similarity
                    )
                )
            }
            
            cursor.close()
            
            // Сортируем по убыванию сходства и берём топ-K
            val sortedResults = results.sortedByDescending { it.similarity }
            val topResults = sortedResults.take(topK)
            
            Log.i(TAG, "✅ Найдено ${topResults.size} похожих результатов из ${results.size} всего")
            
            // Логируем ВСЕ результаты чтобы найти kotlin_basics
            Log.i(TAG, "📊 ВСЕ результаты поиска:")
            sortedResults.forEachIndexed { index, result ->
                Log.i(TAG, "  ${index + 1}. ${result.docName} - similarity: ${(result.similarity * 100).toInt()}% (${result.similarity})")
            }
            
            // Проверяем есть ли kotlin_basics
            val kotlinBasicsResult = sortedResults.find { it.docName.contains("kotlin") }
            if (kotlinBasicsResult != null) {
                Log.w(TAG, "⚠️ kotlin_basics найден на позиции ${sortedResults.indexOf(kotlinBasicsResult) + 1} с similarity ${(kotlinBasicsResult.similarity * 100).toInt()}%")
            } else {
                Log.e(TAG, "❌ kotlin_basics НЕ НАЙДЕН в результатах!")
            }
            
            Result.success(topResults)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка поиска: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Получить все документы
     */
    suspend fun getAllDocuments(): Result<List<Document>> = withContext(Dispatchers.IO) {
        try {
            val db = readableDatabase
            val documents = mutableListOf<Document>()
            
            val cursor = db.query(
                TABLE_DOCUMENTS,
                null,
                null,
                null,
                null,
                null,
                "$COL_DOC_CREATED_AT DESC"
            )
            
            while (cursor.moveToNext()) {
                val metadataJson = cursor.getString(cursor.getColumnIndexOrThrow(COL_DOC_METADATA))
                val metadata = if (metadataJson != null) {
                    gson.fromJson(metadataJson, Map::class.java) as Map<String, String>
                } else {
                    emptyMap()
                }
                
                documents.add(
                    Document(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_DOC_ID)),
                        name = cursor.getString(cursor.getColumnIndexOrThrow(COL_DOC_NAME)),
                        type = cursor.getString(cursor.getColumnIndexOrThrow(COL_DOC_TYPE)),
                        path = cursor.getString(cursor.getColumnIndexOrThrow(COL_DOC_PATH)),
                        content = cursor.getString(cursor.getColumnIndexOrThrow(COL_DOC_CONTENT)),
                        metadata = metadata,
                        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_DOC_CREATED_AT))
                    )
                )
            }
            
            cursor.close()
            Log.i(TAG, "✅ Получено ${documents.size} документов")
            Result.success(documents)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка получения документов: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Удалить документ и все его чанки/эмбеддинги
     */
    suspend fun deleteDocument(docId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = writableDatabase
            val deleted = db.delete(TABLE_DOCUMENTS, "$COL_DOC_ID = ?", arrayOf(docId.toString()))
            
            if (deleted > 0) {
                Log.i(TAG, "✅ Документ удалён (ID: $docId)")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Документ не найден"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка удаления документа: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Получить статистику
     */
    suspend fun getStats(): Result<StorageStats> = withContext(Dispatchers.IO) {
        try {
            val db = readableDatabase
            
            val docCount = db.rawQuery("SELECT COUNT(*) FROM $TABLE_DOCUMENTS", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
            
            val chunkCount = db.rawQuery("SELECT COUNT(*) FROM $TABLE_CHUNKS", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
            
            val embCount = db.rawQuery("SELECT COUNT(*) FROM $TABLE_EMBEDDINGS", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
            
            Result.success(
                StorageStats(
                    documentCount = docCount,
                    chunkCount = chunkCount,
                    embeddingCount = embCount
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Очистить всю базу
     */
    suspend fun clearAll(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = writableDatabase
            
            // Удаляем все данные
            db.execSQL("DELETE FROM $TABLE_EMBEDDINGS")
            db.execSQL("DELETE FROM $TABLE_CHUNKS")
            db.execSQL("DELETE FROM $TABLE_DOCUMENTS")
            
            // Очищаем последовательности ID
            db.execSQL("DELETE FROM sqlite_sequence WHERE name='$TABLE_DOCUMENTS'")
            db.execSQL("DELETE FROM sqlite_sequence WHERE name='$TABLE_CHUNKS'")
            db.execSQL("DELETE FROM sqlite_sequence WHERE name='$TABLE_EMBEDDINGS'")
            
            // VACUUM для полной очистки
            db.execSQL("VACUUM")
            
            Log.i(TAG, "✅ База данных полностью очищена")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка очистки базы: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Удалить файл базы данных полностью
     */
    fun deleteDatabase(context: Context): Result<Unit> {
        return try {
            close()
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            val deleted = dbFile.delete()
            if (deleted) {
                Log.i(TAG, "✅ Файл базы данных удалён: ${dbFile.absolutePath}")
                Result.success(Unit)
            } else {
                Log.w(TAG, "⚠️ Не удалось удалить файл БД (возможно его нет)")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка удаления файла БД: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    private fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        if (vec1.size != vec2.size) return 0f
        
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        
        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }
        
        val denominator = kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2)
        return if (denominator > 0) dotProduct / denominator else 0f
    }
}

/**
 * Представление документа
 */
data class Document(
    val id: Long = 0,
    val name: String,
    val type: String, // markdown, code, pdf, text
    val path: String? = null,
    val content: String,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Результат поиска
 */
data class SearchResult(
    val chunkId: Long,
    val chunkText: String,
    val docId: Long,
    val docName: String,
    val docType: String,
    val similarity: Float
)

/**
 * Статистика хранилища
 */
data class StorageStats(
    val documentCount: Int,
    val chunkCount: Int,
    val embeddingCount: Int
)

