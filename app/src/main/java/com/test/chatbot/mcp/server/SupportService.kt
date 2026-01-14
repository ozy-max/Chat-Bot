package com.test.chatbot.mcp.server

import android.content.Context
import com.test.chatbot.rag.OllamaRAGService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

/**
 * Сервис поддержки пользователей
 * Работает с базой данных пользователей и тикетов (CRM)
 * Использует RAG для поиска решений в документации
 */
class SupportService(
    private val context: Context,
    private val ollamaRAGService: OllamaRAGService
) {
    
    // Данные из CRM
    private var usersData: JSONObject? = null
    private var ticketsData: JSONObject? = null
    
    // Текущий пользователь для контекста
    private var currentUserId: String = "user_005" // По умолчанию - разработчик
    
    init {
        loadCRMData()
        indexSupportDocumentation()
    }
    
    /**
     * Загрузка данных из JSON файлов (имитация CRM)
     */
    private fun loadCRMData() {
        try {
            // Загружаем пользователей
            val usersInputStream = context.assets.open("support_data/users.json")
            val usersReader = BufferedReader(InputStreamReader(usersInputStream))
            val usersJsonString = usersReader.readText()
            usersReader.close()
            usersData = JSONObject(usersJsonString)
            
            // Загружаем тикеты
            val ticketsInputStream = context.assets.open("support_data/tickets.json")
            val ticketsReader = BufferedReader(InputStreamReader(ticketsInputStream))
            val ticketsJsonString = ticketsReader.readText()
            ticketsReader.close()
            ticketsData = JSONObject(ticketsJsonString)
            
            println("✅ CRM данные загружены: ${getUsersCount()} пользователей, ${getTicketsCount()} тикетов")
        } catch (e: Exception) {
            println("❌ Ошибка загрузки CRM данных: ${e.message}")
        }
    }
    
    /**
     * Индексация документации поддержки для RAG
     */
    private fun indexSupportDocumentation() {
        try {
            val docs = mutableListOf<Pair<String, String>>()
            
            // Индексируем все документы поддержки
            val docFiles = listOf(
                "product_docs/product_overview.txt",
                "product_docs/faq_common.txt",
                "product_docs/troubleshooting.txt"
            )
            
            for (docFile in docFiles) {
                try {
                    val inputStream = context.assets.open(docFile)
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val content = reader.readText()
                    reader.close()
                    
                    val docName = docFile.substringAfterLast("/").removeSuffix(".txt")
                    docs.add(docName to content)
                } catch (e: Exception) {
                    println("⚠️ Не удалось загрузить документ $docFile: ${e.message}")
                }
            }
            
            println("📚 Индексация ${docs.size} документов поддержки...")
            // Документы будут автоматически проиндексированы при первом RAG запросе
        } catch (e: Exception) {
            println("❌ Ошибка индексации документации: ${e.message}")
        }
    }
    
    /**
     * Установить текущего пользователя
     */
    fun setCurrentUser(userId: String) {
        currentUserId = userId
    }
    
    /**
     * Получить информацию о пользователе
     */
    suspend fun getUserInfo(userId: String = currentUserId): String = withContext(Dispatchers.IO) {
        try {
            val users = usersData?.getJSONArray("users") ?: return@withContext "❌ Данные пользователей не загружены"
            
            for (i in 0 until users.length()) {
                val user = users.getJSONObject(i)
                if (user.getString("id") == userId) {
                    return@withContext buildString {
                        appendLine("👤 **Информация о пользователе:**")
                        appendLine("ID: ${user.getString("id")}")
                        appendLine("Имя: ${user.getString("name")}")
                        appendLine("Email: ${user.getString("email")}")
                        appendLine("Подписка: ${user.getString("subscription")}")
                        appendLine("Устройство: ${user.getString("device")}")
                        appendLine("Android: ${user.getString("android_version")}")
                        appendLine("Версия приложения: ${user.getString("app_version")}")
                        appendLine("Последний вход: ${user.getString("last_login")}")
                    }
                }
            }
            
            "❌ Пользователь $userId не найден"
        } catch (e: Exception) {
            "❌ Ошибка получения данных пользователя: ${e.message}"
        }
    }
    
    /**
     * Получить тикеты пользователя
     */
    suspend fun getUserTickets(userId: String = currentUserId): String = withContext(Dispatchers.IO) {
        try {
            val tickets = ticketsData?.getJSONArray("tickets") ?: return@withContext "❌ Данные тикетов не загружены"
            
            val userTickets = mutableListOf<JSONObject>()
            for (i in 0 until tickets.length()) {
                val ticket = tickets.getJSONObject(i)
                if (ticket.getString("user_id") == userId) {
                    userTickets.add(ticket)
                }
            }
            
            if (userTickets.isEmpty()) {
                return@withContext "📭 У пользователя $userId нет тикетов"
            }
            
            buildString {
                appendLine("🎫 **Тикеты пользователя:** (${userTickets.size})")
                appendLine()
                
                for (ticket in userTickets) {
                    appendLine("**${ticket.getString("id")}** - ${ticket.getString("status").uppercase()}")
                    appendLine("📌 ${ticket.getString("subject")}")
                    appendLine("Категория: ${ticket.getString("category")}")
                    appendLine("Приоритет: ${ticket.getString("priority")}")
                    appendLine("Создан: ${ticket.getString("created_at")}")
                    appendLine()
                }
            }
        } catch (e: Exception) {
            "❌ Ошибка получения тикетов: ${e.message}"
        }
    }
    
    /**
     * Получить детали конкретного тикета
     */
    suspend fun getTicketDetails(ticketId: String): String = withContext(Dispatchers.IO) {
        try {
            val tickets = ticketsData?.getJSONArray("tickets") ?: return@withContext "❌ Данные тикетов не загружены"
            
            for (i in 0 until tickets.length()) {
                val ticket = tickets.getJSONObject(i)
                if (ticket.getString("id") == ticketId) {
                    return@withContext buildString {
                        appendLine("🎫 **Детали тикета ${ticket.getString("id")}**")
                        appendLine()
                        appendLine("**Статус:** ${ticket.getString("status").uppercase()}")
                        appendLine("**Приоритет:** ${ticket.getString("priority")}")
                        appendLine("**Категория:** ${ticket.getString("category")}")
                        appendLine("**Тема:** ${ticket.getString("subject")}")
                        appendLine()
                        appendLine("**Описание:**")
                        appendLine(ticket.getString("description"))
                        appendLine()
                        appendLine("**Создан:** ${ticket.getString("created_at")}")
                        appendLine("**Обновлен:** ${ticket.getString("updated_at")}")
                        appendLine("**Назначен:** ${ticket.getString("assigned_to")}")
                        
                        if (ticket.has("resolution")) {
                            appendLine()
                            appendLine("**Решение:**")
                            appendLine(ticket.getString("resolution"))
                        }
                        
                        // История сообщений
                        if (ticket.has("messages")) {
                            appendLine()
                            appendLine("**История переписки:**")
                            val messages = ticket.getJSONArray("messages")
                            for (j in 0 until messages.length()) {
                                val msg = messages.getJSONObject(j)
                                val from = if (msg.getString("from") == "user") "👤 Пользователь" else "🛟 Поддержка"
                                appendLine()
                                appendLine("$from (${msg.getString("timestamp")}):")
                                appendLine(msg.getString("text"))
                            }
                        }
                    }
                }
            }
            
            "❌ Тикет $ticketId не найден"
        } catch (e: Exception) {
            "❌ Ошибка получения деталей тикета: ${e.message}"
        }
    }
    
    /**
     * Создать новый тикет
     */
    suspend fun createTicket(
        subject: String,
        description: String,
        category: String = "general",
        priority: String = "medium",
        userId: String = currentUserId
    ): String = withContext(Dispatchers.IO) {
        try {
            val tickets = ticketsData?.getJSONArray("tickets") ?: return@withContext "❌ Данные тикетов не загружены"
            
            // Генерируем ID нового тикета
            val newTicketNumber = tickets.length() + 1
            val newTicketId = "TICKET-${String.format("%03d", newTicketNumber)}"
            
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            val currentTime = dateFormat.format(Date())
            
            // Создаем новый тикет
            val newTicket = JSONObject().apply {
                put("id", newTicketId)
                put("user_id", userId)
                put("status", "open")
                put("priority", priority)
                put("category", category)
                put("subject", subject)
                put("description", description)
                put("created_at", currentTime)
                put("updated_at", currentTime)
                put("assigned_to", "support_team")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("from", "user")
                        put("text", description)
                        put("timestamp", currentTime)
                    })
                })
            }
            
            // Добавляем в массив (в памяти, не сохраняем в файл)
            tickets.put(newTicket)
            
            buildString {
                appendLine("✅ **Тикет создан успешно!**")
                appendLine()
                appendLine("🎫 Номер тикета: **$newTicketId**")
                appendLine("📌 Тема: $subject")
                appendLine("📋 Категория: $category")
                appendLine("⚡ Приоритет: $priority")
                appendLine()
                appendLine("Мы ответим в течение 24 часов.")
                appendLine("Отслеживайте статус через команду: /support status $newTicketId")
            }
        } catch (e: Exception) {
            "❌ Ошибка создания тикета: ${e.message}"
        }
    }
    
    /**
     * Ответить на вопрос с использованием RAG + контекста CRM
     */
    suspend fun answerSupportQuestion(
        question: String,
        userId: String = currentUserId
    ): String = withContext(Dispatchers.IO) {
        try {
            println("🔍 Поиск ответа на вопрос поддержки: $question")
            
            // Получаем контекст пользователя
            val userInfo = getUserInfo(userId)
            
            // Получаем открытые тикеты пользователя
            val tickets = ticketsData?.getJSONArray("tickets")
            val userOpenTickets = mutableListOf<JSONObject>()
            if (tickets != null) {
                for (i in 0 until tickets.length()) {
                    val ticket = tickets.getJSONObject(i)
                    if (ticket.getString("user_id") == userId && 
                        ticket.getString("status") != "closed") {
                        userOpenTickets.add(ticket)
                    }
                }
            }
            
            // Формируем расширенный запрос для RAG
            val ragQuery = buildString {
                append(question)
                
                // Добавляем контекст из открытых тикетов
                if (userOpenTickets.isNotEmpty()) {
                    append(" Контекст: пользователь имеет открытые тикеты по темам: ")
                    append(userOpenTickets.joinToString(", ") { it.getString("category") })
                }
            }
            
            // Ищем в документации через RAG
            val ragResult = ollamaRAGService.queryWithRAG(
                question = ragQuery,
                topK = 10
            )
            
            if (ragResult.isFailure || ragResult.getOrNull()?.answer.isNullOrBlank()) {
                return@withContext buildString {
                    appendLine("⚠️ К сожалению, я не нашел точного ответа в документации.")
                    appendLine()
                    appendLine("Рекомендую создать тикет в поддержку:")
                    appendLine("Используйте команду: `/support ticket \"$question\"`")
                }
            }
            
            // Получаем ответ RAG
            val ragResponse = ragResult.getOrNull()!!
            
            // Формируем ответ с учетом контекста
            buildString {
                appendLine("🛟 **Ответ службы поддержки:**")
                appendLine()
                appendLine(ragResponse.toFormattedString())
                appendLine()
                
                // Добавляем информацию о связанных тикетах
                if (userOpenTickets.isNotEmpty()) {
                    appendLine("---")
                    appendLine("📌 **Ваши открытые тикеты по теме:**")
                    for (ticket in userOpenTickets) {
                        if (ticket.getString("category") in question.lowercase() ||
                            question.lowercase() in ticket.getString("subject").lowercase()) {
                            appendLine("• ${ticket.getString("id")}: ${ticket.getString("subject")} (${ticket.getString("status")})")
                        }
                    }
                    appendLine()
                }
                
                appendLine("---")
                appendLine("💡 Если ответ не помог, создайте тикет: `/support ticket \"Ваш вопрос\"`")
            }
            
        } catch (e: Exception) {
            "❌ Ошибка обработки вопроса: ${e.message}"
        }
    }
    
    /**
     * Поиск по всем тикетам (для администратора)
     */
    suspend fun searchTickets(query: String): String = withContext(Dispatchers.IO) {
        try {
            val tickets = ticketsData?.getJSONArray("tickets") ?: return@withContext "❌ Данные тикетов не загружены"
            
            val foundTickets = mutableListOf<JSONObject>()
            val searchLower = query.lowercase()
            
            for (i in 0 until tickets.length()) {
                val ticket = tickets.getJSONObject(i)
                val subject = ticket.getString("subject").lowercase()
                val description = ticket.getString("description").lowercase()
                val category = ticket.getString("category").lowercase()
                
                if (subject.contains(searchLower) || 
                    description.contains(searchLower) ||
                    category.contains(searchLower)) {
                    foundTickets.add(ticket)
                }
            }
            
            if (foundTickets.isEmpty()) {
                return@withContext "🔍 Тикеты по запросу \"$query\" не найдены"
            }
            
            buildString {
                appendLine("🔍 **Найдено тикетов:** ${foundTickets.size}")
                appendLine()
                
                for (ticket in foundTickets.take(10)) {
                    appendLine("**${ticket.getString("id")}** - ${ticket.getString("status").uppercase()}")
                    appendLine("👤 ${ticket.getString("user_id")} | 📌 ${ticket.getString("subject")}")
                    appendLine("Категория: ${ticket.getString("category")} | Приоритет: ${ticket.getString("priority")}")
                    appendLine()
                }
                
                if (foundTickets.size > 10) {
                    appendLine("... и еще ${foundTickets.size - 10} тикетов")
                }
            }
        } catch (e: Exception) {
            "❌ Ошибка поиска тикетов: ${e.message}"
        }
    }
    
    /**
     * Получить статистику поддержки
     */
    fun getSupportStats(): String {
        return buildString {
            appendLine("📊 **Статистика службы поддержки:**")
            appendLine()
            appendLine("👥 Всего пользователей: ${getUsersCount()}")
            appendLine("🎫 Всего тикетов: ${getTicketsCount()}")
            appendLine()
            
            try {
                val tickets = ticketsData?.getJSONArray("tickets")
                if (tickets != null) {
                    var openCount = 0
                    var closedCount = 0
                    var inProgressCount = 0
                    
                    for (i in 0 until tickets.length()) {
                        val status = tickets.getJSONObject(i).getString("status")
                        when (status) {
                            "open" -> openCount++
                            "closed" -> closedCount++
                            "in_progress" -> inProgressCount++
                        }
                    }
                    
                    appendLine("📂 Статусы тикетов:")
                    appendLine("  • Открыто: $openCount")
                    appendLine("  • В работе: $inProgressCount")
                    appendLine("  • Закрыто: $closedCount")
                }
            } catch (e: Exception) {
                appendLine("⚠️ Не удалось получить детальную статистику")
            }
        }
    }
    
    private fun getUsersCount(): Int {
        return try {
            usersData?.getJSONArray("users")?.length() ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    private fun getTicketsCount(): Int {
        return try {
            ticketsData?.getJSONArray("tickets")?.length() ?: 0
        } catch (e: Exception) {
            0
        }
    }
}
