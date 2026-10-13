package com.senati.mitiemposemanal

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Category(val id: Long, val name: String)

data class TaskItem(
    val id: Long,
    val name: String,
    val categoryId: Long,
    val categoryName: String,
)

data class TimeEvent(
    val id: Long,
    val taskId: Long,
    val taskName: String,
    val categoryName: String,
    val startMillis: Long,
    val endMillis: Long?,
    val durationSeconds: Long?,
)

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        createSprintOneTables(db)
        createEventsTable(db)
        insertDefaultCategories(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Sprint 2 is an additive migration: Sprint 1 categories and tasks are preserved.
        if (oldVersion < 2) {
            createEventsTable(db)
        }
    }

    private fun createSprintOneTables(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE $TABLE_CATEGORIES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NAME TEXT NOT NULL COLLATE NOCASE UNIQUE
            )""".trimIndent(),
        )
        db.execSQL(
            """CREATE TABLE $TABLE_TASKS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NAME TEXT NOT NULL COLLATE NOCASE UNIQUE,
                $COL_CATEGORY_ID INTEGER NOT NULL,
                FOREIGN KEY($COL_CATEGORY_ID) REFERENCES $TABLE_CATEGORIES($COL_ID)
                    ON UPDATE CASCADE ON DELETE RESTRICT
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_tasks_category ON $TABLE_TASKS($COL_CATEGORY_ID)")
    }

    private fun createEventsTable(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS $TABLE_EVENTS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TASK_ID INTEGER NOT NULL,
                $COL_START_TIME INTEGER NOT NULL,
                $COL_END_TIME INTEGER,
                $COL_DURATION_SECONDS INTEGER,
                CHECK($COL_END_TIME IS NULL OR $COL_END_TIME >= $COL_START_TIME),
                FOREIGN KEY($COL_TASK_ID) REFERENCES $TABLE_TASKS($COL_ID)
                    ON UPDATE CASCADE ON DELETE CASCADE
            )""".trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_events_task ON $TABLE_EVENTS($COL_TASK_ID)",
        )
        // An expression-based partial unique index guarantees one running event globally.
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_one_running_event " +
                "ON $TABLE_EVENTS((1)) WHERE $COL_END_TIME IS NULL",
        )
    }

    private fun insertDefaultCategories(db: SQLiteDatabase) {
        listOf("Estudio", "Trabajo", "Descanso", "Ocio").forEach { name ->
            db.insertOrThrow(
                TABLE_CATEGORIES,
                null,
                ContentValues().apply { put(COL_NAME, name) },
            )
        }
    }

    fun getCategories(): List<Category> = readableDatabase.rawQuery(
        "SELECT $COL_ID, $COL_NAME FROM $TABLE_CATEGORIES ORDER BY $COL_NAME COLLATE NOCASE",
        null,
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(Category(cursor.getLong(0), cursor.getString(1)))
            }
        }
    }

    fun addCategory(name: String): Boolean {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || categoryNameExists(cleanName)) return false
        return insertSafely(TABLE_CATEGORIES) { put(COL_NAME, cleanName) } != -1L
    }

    fun updateCategory(id: Long, name: String): Boolean {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || categoryNameExists(cleanName, id)) return false
        return try {
            writableDatabase.update(
                TABLE_CATEGORIES,
                ContentValues().apply { put(COL_NAME, cleanName) },
                "$COL_ID = ?",
                arrayOf(id.toString()),
            ) > 0
        } catch (_: SQLiteConstraintException) {
            false
        }
    }

    fun categoryHasTasks(id: Long): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM $TABLE_TASKS WHERE $COL_CATEGORY_ID = ? LIMIT 1",
        arrayOf(id.toString()),
    ).use { it.moveToFirst() }

    fun deleteCategory(id: Long): Boolean {
        if (categoryHasTasks(id)) return false
        return writableDatabase.delete(
            TABLE_CATEGORIES,
            "$COL_ID = ?",
            arrayOf(id.toString()),
        ) > 0
    }

    fun getTasks(): List<TaskItem> = queryTasks(
        """SELECT t.$COL_ID, t.$COL_NAME, c.$COL_ID, c.$COL_NAME
            FROM $TABLE_TASKS t
            JOIN $TABLE_CATEGORIES c ON c.$COL_ID = t.$COL_CATEGORY_ID
            ORDER BY t.$COL_NAME COLLATE NOCASE""".trimIndent(),
        emptyArray(),
    )

    fun searchTasks(text: String): List<TaskItem> {
        val escaped = text.trim()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return queryTasks(
            """SELECT t.$COL_ID, t.$COL_NAME, c.$COL_ID, c.$COL_NAME
                FROM $TABLE_TASKS t
                JOIN $TABLE_CATEGORIES c ON c.$COL_ID = t.$COL_CATEGORY_ID
                WHERE t.$COL_NAME LIKE ? ESCAPE '\\' COLLATE NOCASE
                ORDER BY t.$COL_NAME COLLATE NOCASE
                LIMIT 20""".trimIndent(),
            arrayOf("%$escaped%"),
        )
    }

    fun findTaskByName(name: String): TaskItem? = queryTasks(
        """SELECT t.$COL_ID, t.$COL_NAME, c.$COL_ID, c.$COL_NAME
            FROM $TABLE_TASKS t
            JOIN $TABLE_CATEGORIES c ON c.$COL_ID = t.$COL_CATEGORY_ID
            WHERE t.$COL_NAME = ? COLLATE NOCASE
            LIMIT 1""".trimIndent(),
        arrayOf(name.trim()),
    ).firstOrNull()

    fun addTask(name: String, categoryId: Long): Long {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || taskNameExists(cleanName)) return -1L
        return insertSafely(TABLE_TASKS) {
            put(COL_NAME, cleanName)
            put(COL_CATEGORY_ID, categoryId)
        }
    }

    fun updateTask(id: Long, name: String, categoryId: Long): Boolean {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || taskNameExists(cleanName, id)) return false
        return try {
            writableDatabase.update(
                TABLE_TASKS,
                ContentValues().apply {
                    put(COL_NAME, cleanName)
                    put(COL_CATEGORY_ID, categoryId)
                },
                "$COL_ID = ?",
                arrayOf(id.toString()),
            ) > 0
        } catch (_: SQLiteConstraintException) {
            false
        }
    }

    fun deleteTask(id: Long): Boolean = writableDatabase.delete(
        TABLE_TASKS,
        "$COL_ID = ?",
        arrayOf(id.toString()),
    ) > 0

    fun getEvents(): List<TimeEvent> = queryEvents(
        """SELECT e.$COL_ID, t.$COL_ID, t.$COL_NAME, c.$COL_NAME,
                   e.$COL_START_TIME, e.$COL_END_TIME, e.$COL_DURATION_SECONDS
            FROM $TABLE_EVENTS e
            JOIN $TABLE_TASKS t ON t.$COL_ID = e.$COL_TASK_ID
            JOIN $TABLE_CATEGORIES c ON c.$COL_ID = t.$COL_CATEGORY_ID
            ORDER BY e.$COL_START_TIME DESC""".trimIndent(),
        emptyArray(),
    )

    fun getRunningEvent(): TimeEvent? = queryEvents(
        """SELECT e.$COL_ID, t.$COL_ID, t.$COL_NAME, c.$COL_NAME,
                   e.$COL_START_TIME, e.$COL_END_TIME, e.$COL_DURATION_SECONDS
            FROM $TABLE_EVENTS e
            JOIN $TABLE_TASKS t ON t.$COL_ID = e.$COL_TASK_ID
            JOIN $TABLE_CATEGORIES c ON c.$COL_ID = t.$COL_CATEGORY_ID
            WHERE e.$COL_END_TIME IS NULL
            LIMIT 1""".trimIndent(),
        emptyArray(),
    ).firstOrNull()

    fun startEvent(taskId: Long, startMillis: Long = System.currentTimeMillis()): Boolean =
        insertSafely(TABLE_EVENTS) {
            put(COL_TASK_ID, taskId)
            put(COL_START_TIME, startMillis)
            putNull(COL_END_TIME)
            putNull(COL_DURATION_SECONDS)
        } != -1L

    fun finishRunningEvent(endMillis: Long = System.currentTimeMillis()): Boolean {
        val running = getRunningEvent() ?: return false
        val safeEnd = maxOf(endMillis, running.startMillis)
        return writableDatabase.update(
            TABLE_EVENTS,
            ContentValues().apply {
                put(COL_END_TIME, safeEnd)
                put(COL_DURATION_SECONDS, (safeEnd - running.startMillis) / 1_000L)
            },
            "$COL_ID = ? AND $COL_END_TIME IS NULL",
            arrayOf(running.id.toString()),
        ) > 0
    }

    private fun queryTasks(sql: String, args: Array<String>): List<TaskItem> =
        readableDatabase.rawQuery(sql, args).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        TaskItem(
                            id = cursor.getLong(0),
                            name = cursor.getString(1),
                            categoryId = cursor.getLong(2),
                            categoryName = cursor.getString(3),
                        ),
                    )
                }
            }
        }

    private fun queryEvents(sql: String, args: Array<String>): List<TimeEvent> =
        readableDatabase.rawQuery(sql, args).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        TimeEvent(
                            id = cursor.getLong(0),
                            taskId = cursor.getLong(1),
                            taskName = cursor.getString(2),
                            categoryName = cursor.getString(3),
                            startMillis = cursor.getLong(4),
                            endMillis = cursor.getLongOrNull(5),
                            durationSeconds = cursor.getLongOrNull(6),
                        ),
                    )
                }
            }
        }

    private fun categoryNameExists(name: String, excludingId: Long? = null): Boolean =
        nameExists(TABLE_CATEGORIES, name, excludingId)

    private fun taskNameExists(name: String, excludingId: Long? = null): Boolean =
        nameExists(TABLE_TASKS, name, excludingId)

    private fun nameExists(table: String, name: String, excludingId: Long?): Boolean {
        val excludeClause = if (excludingId == null) "" else " AND $COL_ID != ?"
        val args = if (excludingId == null) {
            arrayOf(name)
        } else {
            arrayOf(name, excludingId.toString())
        }
        return readableDatabase.rawQuery(
            "SELECT 1 FROM $table WHERE $COL_NAME = ? COLLATE NOCASE$excludeClause LIMIT 1",
            args,
        ).use { it.moveToFirst() }
    }

    private fun android.database.Cursor.getLongOrNull(index: Int): Long? =
        if (isNull(index)) null else getLong(index)

    private inline fun insertSafely(
        table: String,
        valuesBuilder: ContentValues.() -> Unit,
    ): Long = try {
        writableDatabase.insertOrThrow(table, null, ContentValues().apply(valuesBuilder))
    } catch (_: SQLiteConstraintException) {
        -1L
    }

    companion object {
        private const val DATABASE_NAME = "mi_tiempo_semanal.db"
        private const val DATABASE_VERSION = 2

        private const val TABLE_CATEGORIES = "categories"
        private const val TABLE_TASKS = "tasks"
        private const val TABLE_EVENTS = "events"

        private const val COL_ID = "id"
        private const val COL_NAME = "name"
        private const val COL_CATEGORY_ID = "category_id"
        private const val COL_TASK_ID = "task_id"
        private const val COL_START_TIME = "start_time"
        private const val COL_END_TIME = "end_time"
        private const val COL_DURATION_SECONDS = "duration_seconds"
    }
}
