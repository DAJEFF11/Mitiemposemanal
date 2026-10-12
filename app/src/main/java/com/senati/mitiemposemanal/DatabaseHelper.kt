package com.senati.mitiemposemanal

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "mi_tiempo_semanal.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_CATEGORIES = "categories"
        const val COL_CAT_ID = "id"
        const val COL_CAT_NAME = "name"

        const val TABLE_TASKS = "tasks"
        const val COL_TASK_ID = "id"
        const val COL_TASK_NAME = "name"
        const val COL_TASK_CAT_ID = "category_id"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val createCategoriesTable = ("CREATE TABLE $TABLE_CATEGORIES (" +
                "$COL_CAT_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$COL_CAT_NAME TEXT UNIQUE)")
        db?.execSQL(createCategoriesTable)

        val createTasksTable = ("CREATE TABLE $TABLE_TASKS (" +
                "$COL_TASK_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$COL_TASK_NAME TEXT UNIQUE, " +
                "$COL_TASK_CAT_ID INTEGER, " +
                "FOREIGN KEY($COL_TASK_CAT_ID) REFERENCES $TABLE_CATEGORIES($COL_CAT_ID))")
        db?.execSQL(createTasksTable)

        // Insertar las 4 categorías por defecto obligatorias
        val defaultCategories = listOf("Estudio", "Trabajo", "Descanso", "Ocio")
        for (cat in defaultCategories) {
            val values = ContentValues().apply { put(COL_CAT_NAME, cat) }
            db?.insert(TABLE_CATEGORIES, null, values)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_TASKS")
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_CATEGORIES")
        onCreate(db)
    }

    // --- Métodos de Categorías ---
    fun addCategory(name: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply { put(COL_CAT_NAME, name) }
        val result = db.insert(TABLE_CATEGORIES, null, values)
        return result != -1L
    }

    fun getAllCategories(): List<Pair<Int, String>> {
        val list = mutableListOf<Pair<Int, String>>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_CATEGORIES", null)
        if (cursor.moveToFirst()) {
            do {
                list.add(Pair(cursor.getInt(0), cursor.getString(1)))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun updateCategory(id: Int, newName: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply { put(COL_CAT_NAME, newName) }
        val rows = db.update(TABLE_CATEGORIES, values, "$COL_CAT_ID = ?", arrayOf(id.toString()))
        return rows > 0
    }

    fun deleteCategory(id: Int): Boolean {
        val db = this.writableDatabase
        // Verificar si tiene tareas asociadas
        val cursor = db.rawQuery("SELECT * FROM $TABLE_TASKS WHERE $COL_TASK_CAT_ID = ?", arrayOf(id.toString()))
        if (cursor.count > 0) {
            cursor.close()
            return false // No se puede eliminar si tiene tareas
        }
        cursor.close()
        val rows = db.delete(TABLE_CATEGORIES, "$COL_CAT_ID = ?", arrayOf(id.toString()))
        return rows > 0
    }

    // --- Métodos de Tareas ---
    fun addTask(name: String, categoryId: Int): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_TASK_NAME, name)
            put(COL_TASK_CAT_ID, categoryId)
        }
        val result = db.insert(TABLE_TASKS, null, values)
        return result != -1L
    }

    fun getAllTasks(): List<Triple<Int, String, String>> {
        val list = mutableListOf<Triple<Int, String, String>>()
        val db = this.readableDatabase
        val query = "SELECT t.$COL_TASK_ID, t.$COL_TASK_NAME, c.$COL_CAT_NAME " +
                "FROM $TABLE_TASKS t INNER JOIN $TABLE_CATEGORIES c ON t.$COL_TASK_CAT_ID = c.$COL_CAT_ID"
        val cursor = db.rawQuery(query, null)
        if (cursor.moveToFirst()) {
            do {
                list.add(Triple(cursor.getInt(0), cursor.getString(1), cursor.getString(2)))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun updateTask(id: Int, newName: String, categoryId: Int): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_TASK_NAME, newName)
            put(COL_TASK_CAT_ID, categoryId)
        }
        val rows = db.update(TABLE_TASKS, values, "$COL_TASK_ID = ?", arrayOf(id.toString()))
        return rows > 0
    }

    fun deleteTask(id: Int): Boolean {
        val db = this.writableDatabase
        val rows = db.delete(TABLE_TASKS, "$COL_TASK_ID = ?", arrayOf(id.toString()))
        return rows > 0
    }
}