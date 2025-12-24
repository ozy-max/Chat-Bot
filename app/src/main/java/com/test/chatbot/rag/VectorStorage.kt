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
            
            Result.success(topResults)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка поиска: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Поиск с keyword boosting - улучшает ranking если название документа содержит ключевые слова
     */
    suspend fun searchSimilarWithKeywordBoost(
        query: String,
        queryEmbedding: FloatArray,
        topK: Int = 5,
        keywordBoost: Float = 0.3f  // +30% к similarity если keyword match (было 15%)
    ): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        try {
            // 1. Получаем ВСЕ результаты для гарантированного покрытия
            val searchResult = searchSimilar(queryEmbedding, 100)  // Берем все документы
            
            if (searchResult.isFailure) {
                return@withContext searchResult
            }
            
            val results = searchResult.getOrNull()!!
            
            // 2. Извлекаем ключевые слова из запроса
            val keywords = extractKeywords(query)
            
            Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(TAG, "🔍 HYBRID SEARCH")
            Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(TAG, "📝 Запрос: \"$query\"")
            Log.i(TAG, "🔑 Ключевые слова (${keywords.size}): ${keywords.joinToString(", ")}")
            Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(TAG, "📊 Результаты ДО boost:")
            results.take(10).forEachIndexed { index, result ->
                Log.i(TAG, "  ${index + 1}. ${result.docName} - ${(result.similarity * 100).toInt()}%")
            }
            
            // 3. Применяем умный keyword boost
            val boostedResults = results.map { result ->
                var boost = 0f
                val docNameLower = result.docName.lowercase().removeSuffix(".txt")
                val docBaseName = docNameLower.substringBefore("_")  // kotlin_basics → kotlin
                val contentPreview = result.chunkText.lowercase().take(200)  // Первые 200 символов
                
                var matchReason = ""
                
                // Проверяем каждое ключевое слово
                for (keyword in keywords) {
                    val keywordLower = keyword.lowercase()
                    
                    when {
                        // Точное совпадение с началом имени файла - максимальный boost
                        docBaseName == keywordLower -> {
                            boost = 0.5f  // +50%
                            matchReason = "EXACT FILENAME"
                            Log.i(TAG, "  🎯 EXACT MATCH: ${result.docName} +50% (keyword: $keyword)")
                            break
                        }
                        // Точное совпадение где-то в названии - сильный boost
                        docNameLower.contains("_${keywordLower}_") || 
                        docNameLower.contains("_${keywordLower}") ||
                        docNameLower.startsWith(keywordLower) -> {
                            boost = maxOf(boost, 0.45f)  // +45%
                            matchReason = "STRONG FILENAME"
                            Log.i(TAG, "  ✨ STRONG MATCH: ${result.docName} +45% (keyword: $keyword)")
                        }
                        // Ключевое слово в начале содержимого - очень сильный boost
                        contentPreview.contains(keywordLower) && contentPreview.indexOf(keywordLower) < 50 -> {
                            boost = maxOf(boost, 0.4f)  // +40%
                            matchReason = "CONTENT START"
                            Log.i(TAG, "  📝 CONTENT START: ${result.docName} +40% (keyword: $keyword)")
                        }
                        // Частичное совпадение в названии - средний boost
                        docNameLower.contains(keywordLower) -> {
                            boost = maxOf(boost, 0.35f)  // +35%
                            matchReason = "PARTIAL FILENAME"
                            Log.i(TAG, "  💫 PARTIAL MATCH: ${result.docName} +35% (keyword: $keyword)")
                        }
                        // Ключевое слово где-то в содержимом - слабый boost
                        contentPreview.contains(keywordLower) -> {
                            boost = maxOf(boost, 0.3f)  // +30%
                            matchReason = "IN CONTENT"
                            Log.i(TAG, "  📄 IN CONTENT: ${result.docName} +30% (keyword: $keyword)")
                        }
                        // Fuzzy match (для опечаток) - маленький boost
                        isFuzzyMatch(docNameLower, keywordLower) -> {
                            boost = maxOf(boost, 0.25f)  // +25%
                            matchReason = "FUZZY"
                            Log.i(TAG, "  🔍 FUZZY MATCH: ${result.docName} +25% (keyword: $keyword)")
                        }
                    }
                }
                
                if (boost > 0f) {
                    val boosted = (result.similarity + boost).coerceAtMost(1.0f)
                    Log.i(TAG, "    ➜ ${result.docName}: ${(result.similarity * 100).toInt()}% → ${(boosted * 100).toInt()}% [$matchReason]")
                    result.copy(similarity = boosted)
                } else {
                    result
                }
            }
            
            // 4. Пересортируем и берем топ-K
            val finalResults = boostedResults
                .sortedByDescending { it.similarity }
                .take(topK)
            
            Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(TAG, "✅ ФИНАЛЬНЫЕ результаты после boost (топ-$topK):")
            Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            finalResults.forEachIndexed { index, result ->
                Log.i(TAG, "  ${index + 1}. ${result.docName} - ${(result.similarity * 100).toInt()}%")
            }
            Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            
            Result.success(finalResults)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка поиска с keyword boost: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Fuzzy matching для учета опечаток (простая версия - расстояние Левенштейна)
     */
    private fun isFuzzyMatch(docName: String, keyword: String): Boolean {
        if (keyword.length < 4) return false  // Слишком короткие слова не проверяем
        
        // Разбиваем название файла на части
        val parts = docName.split("_", "-")
        
        for (part in parts) {
            val distance = levenshteinDistance(part, keyword)
            // Допускаем 1-2 символа разницы
            if (distance <= 2 && distance < keyword.length / 2) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Вычисление расстояния Левенштейна
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }
        
        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j
        
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        
        return dp[len1][len2]
    }
    
    /**
     * Извлечь ключевые слова из запроса (убираем стоп-слова)
     */
    private fun extractKeywords(query: String): List<String> {
        val stopWords = setOf(
            "что", "такое", "как", "работает", "это", "где", "когда", 
            "почему", "расскажи", "про", "о", "в", "на", "и", "а", "или",
            "существуют", "бывают", "есть", "может", "быть", "можно"
        )
        
        // Маппинг русских технических терминов на английские и нормализованные формы
        val termMapping = mapOf(
            // Квантовые вычисления
            "квантовая" to listOf("quantum", "квант"),
            "квантовый" to listOf("quantum", "квант"),
            "квантовые" to listOf("quantum", "квант"),
            "квантовых" to listOf("quantum", "квант"),
            "кванта" to listOf("quantum", "квант"),
            "кубит" to listOf("qubit", "кубит"),
            "кубиты" to listOf("qubit", "кубит"),
            "запутанность" to listOf("entanglement", "запутан"),
            "запутанная" to listOf("entanglement", "запутан"),
            "суперпозиция" to listOf("superposition", "суперпозиц"),
            
            // Нейронные сети
            "нейронная" to listOf("neural", "нейрон"),
            "нейронные" to listOf("neural", "нейрон"),
            "нейронных" to listOf("neural", "нейрон"),
            "нейросеть" to listOf("neural", "network", "нейрон"),
            "нейросети" to listOf("neural", "network", "нейрон"),
            "сеть" to listOf("network", "сет"),
            "сети" to listOf("network", "сет"),
            "сетей" to listOf("network", "сет"),
            "lstm" to listOf("lstm", "rnn"),
            "rnn" to listOf("rnn", "recurrent"),
            "cnn" to listOf("cnn", "convolutional"),
            "transformer" to listOf("transformer", "трансформер"),
            
            // Android
            "activity" to listOf("activity", "активити"),
            "fragment" to listOf("fragment", "фрагмент"),
            "viewmodel" to listOf("viewmodel", "вьюмодел"),
            "jetpack" to listOf("jetpack", "джетпак"),
            "compose" to listOf("compose", "компоуз"),
            
            // Kotlin
            "kotlin" to listOf("kotlin", "котлин"),
            "корутины" to listOf("coroutine", "корутин"),
            "корутина" to listOf("coroutine", "корутин"),
            "suspend" to listOf("suspend", "саспенд"),
            
            // Docker/DevOps
            "docker" to listOf("docker", "докер"),
            "контейнер" to listOf("container", "docker"),
            "контейнеры" to listOf("container", "docker"),
            "devops" to listOf("devops", "девопс"),
            "kubernetes" to listOf("kubernetes", "k8s"),
            
            // Machine Learning
            "машинное" to listOf("machine", "learning", "ml"),
            "машинного" to listOf("machine", "learning", "ml"),
            "обучение" to listOf("learning", "ml"),
            "модель" to listOf("model", "модел"),
            "модели" to listOf("model", "модел"),
            
            // RAG
            "rag" to listOf("rag", "retrieval"),
            "reranking" to listOf("reranking", "rerank", "ранжирован"),
            "ранжирование" to listOf("ranking", "rerank", "ранжиров"),
            "эмбеддинг" to listOf("embedding", "эмбед"),
            "эмбеддинги" to listOf("embedding", "эмбед"),
            
            // Blockchain
            "блокчейн" to listOf("blockchain", "блок"),
            "криптовалюта" to listOf("crypto", "blockchain"),
            "биткоин" to listOf("bitcoin", "btc"),
            "ethereum" to listOf("ethereum", "eth"),
            "смарт" to listOf("smart", "contract"),
            
            // Web
            "react" to listOf("react", "реакт"),
            "javascript" to listOf("javascript", "js"),
            "typescript" to listOf("typescript", "ts"),
            "node" to listOf("node", "nodejs"),
            
            // Cybersecurity
            "кибербезопасность" to listOf("cybersecurity", "security", "кибер"),
            "безопасность" to listOf("security", "безопасн"),
            "шифрование" to listOf("encryption", "шифр"),
            "vpn" to listOf("vpn", "network")
        )
        
        // Извлекаем базовые ключевые слова
        val baseKeywords = query.lowercase()
            .replace("?", "")
            .replace("!", "")
            .split(Regex("[\\s,;:.]+"))
            .filter { word -> 
                word.length > 2 && !stopWords.contains(word)
            }
        
        // Расширяем ключевые слова через маппинг
        val expandedKeywords = mutableSetOf<String>()
        
        for (keyword in baseKeywords) {
            // Добавляем исходное слово
            expandedKeywords.add(keyword)
            
            // Добавляем нормализованную форму (первые 4-5 символов для стемминга)
            if (keyword.length > 4) {
                expandedKeywords.add(keyword.substring(0, minOf(5, keyword.length)))
            }
            
            // Добавляем маппинг если есть
            if (termMapping.containsKey(keyword)) {
                expandedKeywords.addAll(termMapping[keyword]!!)
            }
        }
        
        return expandedKeywords.toList()
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

