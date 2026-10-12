package com.senati.mitiemposemanal

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var tvHeaderInfo: TextView
    private lateinit var btnTabTasks: Button
    private lateinit var btnTabCategories: Button
    private lateinit var layoutTasksSection: LinearLayout
    private lateinit var layoutCategoriesSection: LinearLayout
    private lateinit var listViewTasks: ListView
    private lateinit var listViewCategories: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = DatabaseHelper(this)

        // Vincular vistas
        tvHeaderInfo = findViewById(R.id.tvHeaderInfo)
        btnTabTasks = findViewById(R.id.btnTabTasks)
        btnTabCategories = findViewById(R.id.btnTabCategories)
        layoutTasksSection = findViewById(R.id.layoutTasksSection)
        layoutCategoriesSection = findViewById(R.id.layoutCategoriesSection)
        listViewTasks = findViewById(R.id.listViewTasks)
        listViewCategories = findViewById(R.id.listViewCategories)

        tvHeaderInfo.text = "Jeffrey Chiroque · v0.1.0 (1)"

        // Pestañas
        btnTabTasks.setOnClickListener {
            layoutTasksSection.visibility = View.VISIBLE
            layoutCategoriesSection.visibility = View.GONE
            btnTabTasks.setBackgroundColor(resources.getColor(R.color.primary))
            btnTabCategories.setBackgroundColor(resources.getColor(R.color.text_secondary))
            loadTasks()
        }

        btnTabCategories.setOnClickListener {
            layoutCategoriesSection.visibility = View.VISIBLE
            layoutTasksSection.visibility = View.GONE
            btnTabCategories.setBackgroundColor(resources.getColor(R.color.primary))
            btnTabTasks.setBackgroundColor(resources.getColor(R.color.text_secondary))
            loadCategories()
        }

        findViewById<Button>(R.id.btnAddCategory).setOnClickListener { showCategoryDialog(null, null) }
        findViewById<Button>(R.id.btnAddTask).setOnClickListener { showTaskDialog(null, null, null) }

        loadTasks()
    }

    // --- GESTIÓN DE CATEGORÍAS ---
    private fun loadCategories() {
        val categories = dbHelper.getAllCategories()
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, categories.map { it.second })
        listViewCategories.adapter = adapter

        listViewCategories.setOnItemClickListener { _, _, position, _ ->
            val cat = categories[position]
            val options = arrayOf("Editar", "Eliminar")
            AlertDialog.Builder(this)
                .setTitle("Opciones para: ${cat.second}")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> showCategoryDialog(cat.first, cat.second)
                        1 -> confirmDeleteCategory(cat.first, cat.second)
                    }
                }.show()
        }
    }

    private fun showCategoryDialog(id: Int?, currentName: String?) {
        val input = EditText(this)
        input.setText(currentName)
        input.setPadding(50, 40, 50, 40)

        AlertDialog.Builder(this)
            .setTitle(if (id == null) "Nueva Categoría" else "Editar Categoría")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val success = if (id == null) dbHelper.addCategory(name) else dbHelper.updateCategory(id, name)
                if (success) {
                    loadCategories()
                    Toast.makeText(this, "Guardado correctamente", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Error: El nombre ya existe", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmDeleteCategory(id: Int, name: String) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar Categoría")
            .setMessage("¿Deseas eliminar la categoría '$name'?")
            .setPositiveButton("Eliminar") { _, _ ->
                val deleted = dbHelper.deleteCategory(id)
                if (deleted) {
                    loadCategories()
                    Toast.makeText(this, "Categoría eliminada", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "No se puede eliminar: La categoría tiene tareas asociadas.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // --- GESTIÓN DE TAREAS CON TARJETAS PROFESIONALES ---
    private fun loadTasks() {
        val tasks = dbHelper.getAllTasks()
        val adapter = TaskAdapter(this, tasks)
        listViewTasks.adapter = adapter

        listViewTasks.setOnItemClickListener { _, _, position, _ ->
            val task = tasks[position]
            val options = arrayOf("Editar", "Eliminar")
            AlertDialog.Builder(this)
                .setTitle("Opciones para: ${task.second}")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> showTaskDialog(task.first, task.second, task.third)
                        1 -> confirmDeleteTask(task.first)
                    }
                }.show()
        }
    }

    private fun showTaskDialog(id: Int?, currentName: String?, currentCat: String?) {
        val categories = dbHelper.getAllCategories()
        if (categories.isEmpty()) {
            Toast.makeText(this, "Debe crear al menos una categoría primero", Toast.LENGTH_SHORT).show()
            return
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
        }

        val inputName = EditText(this).apply {
            hint = "Nombre de la tarea"
            setText(currentName)
        }
        layout.addView(inputName)

        val spinnerCategories = Spinner(this)
        val catNames = categories.map { it.second }
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, catNames)
        spinnerCategories.adapter = spinnerAdapter

        if (currentCat != null) {
            val catIndex = catNames.indexOf(currentCat)
            if (catIndex >= 0) spinnerCategories.setSelection(catIndex)
        }
        layout.addView(spinnerCategories)

        AlertDialog.Builder(this)
            .setTitle(if (id == null) "Nueva Tarea" else "Editar Tarea")
            .setView(layout)
            .setPositiveButton("Guardar") { _, _ ->
                val name = inputName.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "No se puede guardar una tarea sin nombre", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val selectedCatPair = categories[spinnerCategories.selectedItemPosition]

                val success = if (id == null) {
                    dbHelper.addTask(name, selectedCatPair.first)
                } else {
                    dbHelper.updateTask(id, name, selectedCatPair.first)
                }

                if (success) {
                    loadTasks()
                    Toast.makeText(this, "Tarea guardada con éxito", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Error: El nombre de la tarea ya existe", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmDeleteTask(id: Int) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar Tarea")
            .setMessage("¿Deseas eliminar esta tarea?")
            .setPositiveButton("Eliminar") { _, _ ->
                dbHelper.deleteTask(id)
                loadTasks()
                Toast.makeText(this, "Tarea eliminada", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}