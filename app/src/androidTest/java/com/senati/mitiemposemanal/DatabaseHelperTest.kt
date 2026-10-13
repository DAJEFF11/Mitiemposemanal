package com.senati.mitiemposemanal

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseHelperTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun freshDatabase_containsRequiredCategoriesAndRejectsDuplicates() {
        DatabaseHelper(context).use { db ->
            assertEquals(
                setOf("Estudio", "Trabajo", "Descanso", "Ocio"),
                db.getCategories().map { it.name }.toSet(),
            )
            assertFalse(db.addCategory("estudio"))
            assertFalse(db.addCategory("   "))
            val category = db.getCategories().first()
            assertEquals(-1L, db.addTask("   ", category.id))
            assertTrue(db.addTask("Leer Kotlin", category.id) > 0)
            assertEquals(-1L, db.addTask("leer kotlin", category.id))
        }
    }

    @Test
    fun eventRemainsRunningAcrossHelperInstances_andOnlyOneCanRun() {
        val taskId = DatabaseHelper(context).use { db ->
            val category = db.getCategories().first()
            val id = db.addTask("Leer Kotlin", category.id)
            assertTrue(db.startEvent(id, 1_000L))
            assertFalse(db.startEvent(id, 2_000L))
            id
        }

        DatabaseHelper(context).use { reopened ->
            assertEquals(taskId, reopened.getRunningEvent()?.taskId)
            assertTrue(reopened.finishRunningEvent(61_000L))
            assertEquals(60L, reopened.getEvents().first().durationSeconds)
        }
    }

    @Test
    fun upgradeFromVersionOne_preservesCategoriesAndTasks() {
        val path = context.getDatabasePath(DATABASE_NAME)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { legacy ->
            legacy.execSQL("CREATE TABLE categories (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE)")
            legacy.execSQL(
                "CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE, " +
                    "category_id INTEGER, FOREIGN KEY(category_id) REFERENCES categories(id))",
            )
            legacy.execSQL("INSERT INTO categories(name) VALUES ('Estudio')")
            legacy.execSQL("INSERT INTO tasks(name, category_id) VALUES ('Leer Kotlin', 1)")
            legacy.version = 1
        }

        DatabaseHelper(context).use { upgraded ->
            assertEquals("Leer Kotlin", upgraded.getTasks().single().name)
            assertNotNull(upgraded.writableDatabase)
            assertTrue(upgraded.startEvent(upgraded.getTasks().single().id))
        }
    }

    private companion object {
        const val DATABASE_NAME = "mi_tiempo_semanal.db"
    }
}
