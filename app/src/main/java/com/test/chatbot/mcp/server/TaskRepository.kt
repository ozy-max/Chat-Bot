package com.test.chatbot.mcp.server

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class TaskRepository(context: Context) {

    private val dbHelper = TaskDatabaseHelper(context)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    suspend fun getAllTasks(): List<Task> = withContext(Dispatchers.IO) {
        val tasks = mutableListOf<Task>()
        val db = dbHelper.readableDatabase
        
        val cursor = db.query(
            TaskDatabaseHelper.TABLE_TASKS,
            null,
            "${TaskDatabaseHelper.COLUMN_STATUS} = ?",
            arrayOf("pending"),
            null, null,
            "${TaskDatabaseHelper.COLUMN_CREATED_AT} DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                tasks.add(cursorToTask(it))
            }
        }

        tasks
    }

    suspend fun upsertTask(task: Task): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = taskToContentValues(task)

        if (task.id > 0) {
            db.update(
                TaskDatabaseHelper.TABLE_TASKS,
                values,
                "${TaskDatabaseHelper.COLUMN_ID} = ?",
                arrayOf(task.id.toString())
            ).toLong()
        } else {
            db.insert(TaskDatabaseHelper.TABLE_TASKS, null, values)
        }
    }

    suspend fun findByTodoistId(todoistId: String): Task? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TaskDatabaseHelper.TABLE_TASKS,
            null,
            "${TaskDatabaseHelper.COLUMN_DESCRIPTION} LIKE ?",
            arrayOf("%[TODOIST-$todoistId]%"),
            null, null, null
        )

        cursor.use {
            if (it.moveToFirst()) {
                cursorToTask(it)
            } else {
                null
            }
        }
    }

    suspend fun deleteTask(id: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(
            TaskDatabaseHelper.TABLE_TASKS,
            "${TaskDatabaseHelper.COLUMN_ID} = ?",
            arrayOf(id.toString())
        )
    }

    suspend fun getTodaySummary(todoistService: TodoistService): TaskSummary = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase

        val completedToday = todoistService.getCompletedTasksToday()

        val pendingCount = db.query(
            TaskDatabaseHelper.TABLE_TASKS,
            arrayOf("COUNT(*)"),
            "${TaskDatabaseHelper.COLUMN_STATUS} = ?",
            arrayOf("pending"),
            null, null, null
        ).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }

        val createdToday = completedToday + pendingCount

        TaskSummary(createdToday, completedToday, pendingCount)
    }

    private fun cursorToTask(cursor: android.database.Cursor): Task {
        return Task(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(TaskDatabaseHelper.COLUMN_ID)),
            title = cursor.getString(cursor.getColumnIndexOrThrow(TaskDatabaseHelper.COLUMN_TITLE)),
            description = cursor.getString(cursor.getColumnIndexOrThrow(TaskDatabaseHelper.COLUMN_DESCRIPTION)) ?: "",
            completed = cursor.getString(cursor.getColumnIndexOrThrow(TaskDatabaseHelper.COLUMN_STATUS)) == "completed",
            createdAt = cursor.getString(cursor.getColumnIndexOrThrow(TaskDatabaseHelper.COLUMN_CREATED_AT)),
            completedAt = cursor.getString(cursor.getColumnIndexOrThrow(TaskDatabaseHelper.COLUMN_COMPLETED_AT))
        )
    }

    private fun taskToContentValues(task: Task): ContentValues {
        return ContentValues().apply {
            put(TaskDatabaseHelper.COLUMN_TITLE, task.title)
            put(TaskDatabaseHelper.COLUMN_DESCRIPTION, task.description)
            put(TaskDatabaseHelper.COLUMN_STATUS, if (task.completed) "completed" else "pending")
            put(TaskDatabaseHelper.COLUMN_CREATED_AT, task.createdAt)
            put(TaskDatabaseHelper.COLUMN_COMPLETED_AT, task.completedAt)
        }
    }
}

private class TaskDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {

    companion object {
        const val DATABASE_NAME = "mcp_tasks.db"
        const val DATABASE_VERSION = 2

        const val TABLE_TASKS = "tasks"
        const val COLUMN_ID = "id"
        const val COLUMN_TITLE = "title"
        const val COLUMN_DESCRIPTION = "description"
        const val COLUMN_STATUS = "status"
        const val COLUMN_CREATED_AT = "created_at"
        const val COLUMN_COMPLETED_AT = "completed_at"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_TASKS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TITLE TEXT NOT NULL,
                $COLUMN_DESCRIPTION TEXT,
                $COLUMN_STATUS TEXT DEFAULT 'pending',
                $COLUMN_CREATED_AT TEXT NOT NULL,
                $COLUMN_COMPLETED_AT TEXT
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TASKS")
        onCreate(db)
    }
}

data class Task(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val completed: Boolean = false,
    val createdAt: String,
    val completedAt: String? = null
)

data class TaskSummary(
    val createdToday: Int,
    val completedToday: Int,
    val pendingCount: Int
)
