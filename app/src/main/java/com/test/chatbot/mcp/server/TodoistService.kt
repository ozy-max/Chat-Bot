package com.test.chatbot.mcp.server

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class TodoistService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private var token: String = ""
    private val dateFormatInput = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    private val dateFormatOutput = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    companion object {
        private const val BASE_URL_V2 = "https://api.todoist.com/rest/v2"
        private const val BASE_URL_V1 = "https://api.todoist.com/api/v1"
    }

    fun setToken(token: String) {
        this.token = token
    }

    suspend fun getCompletedTasksToday(): Int = withContext(Dispatchers.IO) {
        if (token.isBlank()) {
            return@withContext 0
        }
        
        try {
            val completedTasks = fetchCompletedTodoistTasks()
            return@withContext completedTasks.size
        } catch (e: Exception) {
            return@withContext 0
        }
    }

    suspend fun getCreatedTasksToday(): Int = withContext(Dispatchers.IO) {
        if (token.isBlank()) {
            return@withContext 0
        }
        
        try {
            val calendar = java.util.Calendar.getInstance()
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            val startOfDayMillis = calendar.timeInMillis
            
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
            calendar.set(java.util.Calendar.MINUTE, 59)
            calendar.set(java.util.Calendar.SECOND, 59)
            calendar.set(java.util.Calendar.MILLISECOND, 999)
            val endOfDayMillis = calendar.timeInMillis
            
            val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            
            val activeTasks = fetchAllTodoistTasks()
            var activeCreatedToday = 0
            activeTasks.forEach { task ->
                try {
                    if (task.createdAt != null) {
                        val createdAtStr = task.createdAt.substringBefore("Z").substringBefore("+")
                        val createdDate = isoFormat.parse(createdAtStr)
                        val createdMillis = createdDate?.time ?: 0L
                        if (createdMillis in startOfDayMillis..endOfDayMillis) {
                            activeCreatedToday++
                        }
                    }
                } catch (e: Exception) {
                }
            }
            
            val completedTasks = fetchCompletedTodoistTasks()
            var completedCreatedToday = 0
            completedTasks.forEach { task ->
                try {
                    if (task.createdAt != null) {
                        val createdAtStr = task.createdAt.substringBefore("Z").substringBefore("+")
                        val createdDate = isoFormat.parse(createdAtStr)
                        val createdMillis = createdDate?.time ?: 0L
                        if (createdMillis in startOfDayMillis..endOfDayMillis) {
                            completedCreatedToday++
                        }
                    }
                } catch (e: Exception) {
                }
            }
            
            return@withContext activeCreatedToday + completedCreatedToday
        } catch (e: Exception) {
            return@withContext 0
        }
    }

    suspend fun syncTasks(taskRepository: TaskRepository): Int = withContext(Dispatchers.IO) {
        if (token.isBlank()) {
            return@withContext 0
        }

        try {
            val todoistTasks = fetchAllTodoistTasks()
            val todoistIds = mutableSetOf<String>()
            var importedCount = 0
            var updatedCount = 0
            
            for (todoistTask in todoistTasks) {
                todoistIds.add(todoistTask.id)
                val existingTask = taskRepository.findByTodoistId(todoistTask.id)

                if (existingTask != null) {
                    val isCompleted = todoistTask.isCompleted
                    if (existingTask.completed != isCompleted) {
                        val completedDate = if (isCompleted) {
                            todoistTask.completedAt?.let { completedAtStr ->
                                try {
                                    val parsedDate = dateFormatInput.parse(completedAtStr)
                                    if (parsedDate != null) {
                                        dateFormatOutput.format(parsedDate)
                                    } else {
                                        dateFormatOutput.format(Date())
                                    }
                                } catch (e: Exception) {
                                    dateFormatOutput.format(Date())
                                }
                            } ?: dateFormatOutput.format(Date())
                        } else {
                            null
                        }
                        
                        val updated = existingTask.copy(
                            completed = isCompleted,
                            completedAt = completedDate
                        )
                        taskRepository.upsertTask(updated)
                        updatedCount++
                    }
                } else {
                    val createdDate = try {
                        if (todoistTask.createdAt != null) {
                            val parsedDate = dateFormatInput.parse(todoistTask.createdAt)
                            if (parsedDate != null) {
                                dateFormatOutput.format(parsedDate)
                            } else {
                                dateFormatOutput.format(Date())
                            }
                        } else {
                            dateFormatOutput.format(Date())
                        }
                    } catch (e: Exception) {
                        dateFormatOutput.format(Date())
                    }
                    
                    val completedDate = todoistTask.completedAt?.let { completedAtStr ->
                        try {
                            val parsedDate = dateFormatInput.parse(completedAtStr)
                            if (parsedDate != null) {
                                dateFormatOutput.format(parsedDate)
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }
                    
                    val newTask = Task(
                        title = todoistTask.content,
                        description = "[TODOIST-${todoistTask.id}] ${todoistTask.description}",
                        completed = todoistTask.isCompleted,
                        createdAt = createdDate,
                        completedAt = completedDate
                    )
                    taskRepository.upsertTask(newTask)
                    importedCount++
                }
            }

            val localTasks = taskRepository.getAllTasks()
            var deletedCount = 0

            for (localTask in localTasks) {
                if (localTask.description.contains("[TODOIST-")) {
                    val todoistId = extractTodoistId(localTask.description)
                    if (todoistId != null && todoistId !in todoistIds) {
                        taskRepository.deleteTask(localTask.id)
                        deletedCount++
                    }
                }
            }

            importedCount + updatedCount + deletedCount

        } catch (e: Exception) {
            0
        }
    }

    private fun fetchAllTodoistTasks(): List<TodoistTask> {
        val url = "$BASE_URL_V2/tasks"
        
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}")
            }

            val body = response.body?.string() ?: throw Exception("Empty response")
            return gson.fromJson(body, Array<TodoistTask>::class.java).toList()
        }
    }

    private fun fetchCompletedTodoistTasks(): List<TodoistTask> {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startOfDay = calendar.time
        
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
        calendar.set(java.util.Calendar.MINUTE, 59)
        calendar.set(java.util.Calendar.SECOND, 59)
        calendar.set(java.util.Calendar.MILLISECOND, 999)
        val endOfDay = calendar.time
        
        val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        isoFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
        
        val since = isoFormat.format(startOfDay)
        val until = isoFormat.format(endOfDay)
        
        val url = "$BASE_URL_V1/tasks/completed/by_completion_date?since=$since&until=$until"
        
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}")
            }

            val body = response.body?.string() ?: throw Exception("Empty response")
            val result = gson.fromJson(body, CompletedTasksResponse::class.java)
            return result.items ?: emptyList()
        }
    }

    private fun extractTodoistId(description: String): String? {
        val regex = """\[TODOIST-(\d+)\]""".toRegex()
        return regex.find(description)?.groupValues?.get(1)
    }
}

private data class TodoistTask(
    @SerializedName("id")
    val id: String,
    @SerializedName("content")
    val content: String,
    @SerializedName("description")
    val description: String = "",
    @SerializedName("is_completed")
    val isCompleted: Boolean = false,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("completed_at")
    val completedAt: String? = null
)

private data class CompletedTasksResponse(
    @SerializedName("items")
    val items: List<TodoistTask>? = null
)
