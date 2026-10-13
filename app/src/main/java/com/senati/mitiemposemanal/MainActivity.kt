package com.senati.mitiemposemanal

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper

    private lateinit var btnTasks: Button
    private lateinit var btnCategories: Button
    private lateinit var btnEvents: Button
    private lateinit var tasksSection: View
    private lateinit var categoriesSection: View
    private lateinit var eventsSection: View

    private lateinit var taskList: ListView
    private lateinit var categoryList: ListView
    private lateinit var eventList: ListView
    private lateinit var eventTaskInput: EditText
    private lateinit var suggestionList: ListView
    private lateinit var selectedCategoryText: TextView
    private lateinit var runningEventText: TextView
    private lateinit var startButton: Button
    private lateinit var finishButton: Button

    private var tasks: List<TaskItem> = emptyList()
    private var categories: List<Category> = emptyList()
    private var suggestions: List<TaskItem> = emptyList()
    private var selectedEventTask: TaskItem? = null

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applySystemBarInsets()
        db = DatabaseHelper(this)

        bindViews()
        configureHeader()
        configureTabs()
        configureCategoryActions()
        configureTaskActions()
        configureEventActions()

        showSection(Section.EVENTS)
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    override fun onDestroy() {
        db.close()
        super.onDestroy()
    }

    private fun bindViews() {
        btnTasks = findViewById(R.id.btnTabTasks)
        btnCategories = findViewById(R.id.btnTabCategories)
        btnEvents = findViewById(R.id.btnTabEvents)
        tasksSection = findViewById(R.id.layoutTasksSection)
        categoriesSection = findViewById(R.id.layoutCategoriesSection)
        eventsSection = findViewById(R.id.layoutEventsSection)
        taskList = findViewById(R.id.listViewTasks)
        categoryList = findViewById(R.id.listViewCategories)
        eventList = findViewById(R.id.listViewEvents)
        eventTaskInput = findViewById(R.id.etEventTask)
        suggestionList = findViewById(R.id.listViewSuggestions)
        selectedCategoryText = findViewById(R.id.tvSelectedCategory)
        runningEventText = findViewById(R.id.tvRunningEvent)
        startButton = findViewById(R.id.btnStartEvent)
        finishButton = findViewById(R.id.btnFinishEvent)
    }

    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(16) + bars.left, bars.top, dp(16) + bars.right, bars.bottom)
            insets
        }
    }

    private fun configureHeader() {
        @Suppress("DEPRECATION")
        val info = packageManager.getPackageInfo(packageName, 0)
        findViewById<TextView>(R.id.tvHeaderInfo).text = getString(
            R.string.header_info,
            getString(R.string.student_name),
            info.versionName ?: "0.2.0",
            PackageInfoCompat.getLongVersionCode(info),
        )
    }

    private fun configureTabs() {
        btnTasks.setOnClickListener { showSection(Section.TASKS) }
        btnCategories.setOnClickListener { showSection(Section.CATEGORIES) }
        btnEvents.setOnClickListener { showSection(Section.EVENTS) }
    }

    private fun showSection(section: Section) {
        tasksSection.visibility = if (section == Section.TASKS) View.VISIBLE else View.GONE
        categoriesSection.visibility = if (section == Section.CATEGORIES) View.VISIBLE else View.GONE
        eventsSection.visibility = if (section == Section.EVENTS) View.VISIBLE else View.GONE

        styleTab(btnTasks, section == Section.TASKS)
        styleTab(btnCategories, section == Section.CATEGORIES)
        styleTab(btnEvents, section == Section.EVENTS)
        refreshAll()
    }

    private fun styleTab(button: Button, selected: Boolean) {
        button.setBackgroundColor(
            ContextCompat.getColor(
                this,
                if (selected) R.color.primary else R.color.surface_variant,
            ),
        )
        button.setTextColor(
            ContextCompat.getColor(
                this,
                if (selected) R.color.white else R.color.text_primary,
            ),
        )
    }

    private fun configureCategoryActions() {
        findViewById<Button>(R.id.btnAddCategory).setOnClickListener {
            showCategoryDialog()
        }
        categoryList.setOnItemClickListener { _, _, position, _ ->
            val category = categories[position]
            AlertDialog.Builder(this)
                .setTitle(category.name)
                .setItems(arrayOf(getString(R.string.edit), getString(R.string.delete))) { _, which ->
                    if (which == 0) showCategoryDialog(category) else confirmDeleteCategory(category)
                }
                .show()
        }
    }

    private fun showCategoryDialog(category: Category? = null) {
        val input = EditText(this).apply {
            hint = getString(R.string.category_name)
            setText(category?.name.orEmpty())
            setSelection(text.length)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (category == null) R.string.new_category else R.string.edit_category)
            .setView(dialogContainer(input))
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    input.error = getString(R.string.required_name)
                    return@setOnClickListener
                }
                val saved = if (category == null) {
                    db.addCategory(name)
                } else {
                    db.updateCategory(category.id, name)
                }
                if (saved) {
                    dialog.dismiss()
                    refreshAll()
                    toast(R.string.category_saved)
                } else {
                    input.error = getString(R.string.duplicate_category)
                }
            }
        }
        dialog.show()
    }

    private fun confirmDeleteCategory(category: Category) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_category)
            .setMessage(getString(R.string.confirm_delete_category, category.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                if (db.deleteCategory(category.id)) {
                    refreshAll()
                    toast(R.string.category_deleted)
                } else {
                    toast(R.string.category_has_tasks, Toast.LENGTH_LONG)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun configureTaskActions() {
        findViewById<Button>(R.id.btnAddTask).setOnClickListener { showTaskDialog() }
        taskList.setOnItemClickListener { _, _, position, _ ->
            val task = tasks[position]
            AlertDialog.Builder(this)
                .setTitle(task.name)
                .setItems(arrayOf(getString(R.string.edit), getString(R.string.delete))) { _, which ->
                    if (which == 0) showTaskDialog(task) else confirmDeleteTask(task)
                }
                .show()
        }
    }

    private fun showTaskDialog(task: TaskItem? = null) {
        val availableCategories = db.getCategories()
        if (availableCategories.isEmpty()) {
            toast(R.string.create_category_first)
            return
        }
        val nameInput = EditText(this).apply {
            hint = getString(R.string.task_name)
            setText(task?.name.orEmpty())
            setSelection(text.length)
        }
        val spinner = categorySpinner(availableCategories, task?.categoryId)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(nameInput)
            addView(TextView(this@MainActivity).apply {
                text = getString(R.string.category)
                setPadding(0, dp(14), 0, dp(4))
            })
            addView(spinner)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (task == null) R.string.new_task else R.string.edit_task)
            .setView(content)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString().trim()
                if (name.isBlank()) {
                    nameInput.error = getString(R.string.required_task_name)
                    return@setOnClickListener
                }
                val category = availableCategories[spinner.selectedItemPosition]
                val saved = if (task == null) {
                    db.addTask(name, category.id) != -1L
                } else {
                    db.updateTask(task.id, name, category.id)
                }
                if (saved) {
                    dialog.dismiss()
                    refreshAll()
                    toast(R.string.task_saved)
                } else {
                    nameInput.error = getString(R.string.duplicate_task)
                }
            }
        }
        dialog.show()
    }

    private fun confirmDeleteTask(task: TaskItem) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_task)
            .setMessage(getString(R.string.confirm_delete_task, task.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                if (db.deleteTask(task.id)) {
                    refreshAll()
                    toast(R.string.task_deleted)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun configureEventActions() {
        eventTaskInput.doAfterTextChanged { editable ->
            val text = editable?.toString().orEmpty().trim()
            if (!selectedEventTask?.name.equals(text, ignoreCase = true)) {
                selectedEventTask = null
                selectedCategoryText.text = getString(R.string.no_category_selected)
            }
            if (text.length >= 2 && eventTaskInput.isEnabled) {
                suggestions = db.searchTasks(text)
                suggestionList.adapter = textAdapter(
                    suggestions.map { "${it.name} · ${it.categoryName}" },
                )
                suggestionList.visibility =
                    if (suggestions.isEmpty()) View.GONE else View.VISIBLE
            } else {
                suggestions = emptyList()
                suggestionList.visibility = View.GONE
            }
        }
        suggestionList.setOnItemClickListener { _, _, position, _ ->
            val task = suggestions[position]
            selectedEventTask = task
            eventTaskInput.setText(task.name)
            eventTaskInput.setSelection(task.name.length)
            selectedCategoryText.text = getString(R.string.selected_category, task.categoryName)
            suggestionList.visibility = View.GONE
        }
        startButton.setOnClickListener { beginEvent() }
        finishButton.setOnClickListener {
            if (db.finishRunningEvent()) {
                eventTaskInput.setText("")
                selectedEventTask = null
                toast(R.string.event_finished)
                refreshAll()
            }
        }
    }

    private fun beginEvent() {
        val name = eventTaskInput.text.toString().trim()
        if (name.isBlank()) {
            eventTaskInput.error = getString(R.string.required_task_name)
            return
        }
        val existing = selectedEventTask ?: db.findTaskByName(name)
        if (existing != null) {
            startEventFor(existing)
        } else {
            showCreateAndStartDialog(name)
        }
    }

    private fun showCreateAndStartDialog(taskName: String) {
        val availableCategories = db.getCategories()
        if (availableCategories.isEmpty()) {
            toast(R.string.create_category_first)
            return
        }
        val spinner = categorySpinner(availableCategories, null)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(TextView(this@MainActivity).apply {
                text = getString(R.string.create_task_prompt, taskName)
            })
            addView(spinner)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.task_not_found)
            .setView(content)
            .setPositiveButton(R.string.create_and_start, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val category = availableCategories[spinner.selectedItemPosition]
                val taskId = db.addTask(taskName, category.id)
                val task = if (taskId != -1L) {
                    TaskItem(taskId, taskName, category.id, category.name)
                } else {
                    db.findTaskByName(taskName)
                }
                if (task == null) {
                    toast(R.string.duplicate_task, Toast.LENGTH_LONG)
                    return@setOnClickListener
                }
                dialog.dismiss()
                startEventFor(task)
            }
        }
        dialog.show()
    }

    private fun startEventFor(task: TaskItem) {
        if (db.getRunningEvent() != null) {
            toast(R.string.only_one_event, Toast.LENGTH_LONG)
            refreshEventState()
            return
        }
        if (db.startEvent(task.id)) {
            selectedEventTask = task
            eventTaskInput.setText(task.name)
            toast(R.string.event_started)
            refreshAll()
        } else {
            toast(R.string.only_one_event, Toast.LENGTH_LONG)
            refreshEventState()
        }
    }

    private fun refreshAll() {
        if (!::db.isInitialized) return
        categories = db.getCategories()
        categoryList.adapter = textAdapter(categories.map { it.name })

        tasks = db.getTasks()
        taskList.adapter = textAdapter(tasks.map { "${it.name} · ${it.categoryName}" })

        val events = db.getEvents()
        eventList.adapter = textAdapter(
            events.map { event ->
                val end = event.endMillis?.let(::formatDate) ?: getString(R.string.in_progress)
                val duration = event.durationSeconds?.let(::formatDuration) ?: "—"
                "${event.taskName} · ${event.categoryName}\n" +
                    getString(R.string.event_times, formatDate(event.startMillis), end) +
                    "\n" + getString(R.string.event_duration, duration)
            },
        )
        findViewById<TextView>(R.id.tvEmptyTasks).visibility =
            if (tasks.isEmpty()) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.tvEmptyEvents).visibility =
            if (events.isEmpty()) View.VISIBLE else View.GONE
        refreshEventState()
    }

    private fun refreshEventState() {
        val running = db.getRunningEvent()
        val isRunning = running != null
        startButton.isEnabled = !isRunning
        finishButton.isEnabled = isRunning
        eventTaskInput.isEnabled = !isRunning
        if (running != null) {
            runningEventText.text = getString(
                R.string.running_event,
                running.taskName,
                running.categoryName,
                formatDate(running.startMillis),
            )
            runningEventText.setTextColor(ContextCompat.getColor(this, R.color.running))
            if (eventTaskInput.text.toString() != running.taskName) {
                selectedEventTask = TaskItem(
                    running.taskId,
                    running.taskName,
                    0,
                    running.categoryName,
                )
                eventTaskInput.setText(running.taskName)
            }
            selectedCategoryText.text = getString(
                R.string.selected_category,
                running.categoryName,
            )
            suggestionList.visibility = View.GONE
        } else {
            runningEventText.text = getString(R.string.no_running_event)
            runningEventText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun categorySpinner(items: List<Category>, selectedId: Long?): Spinner =
        Spinner(this).apply {
            adapter = textAdapter(items.map { it.name })
            val index = items.indexOfFirst { it.id == selectedId }
            if (index >= 0) setSelection(index)
        }

    private fun textAdapter(items: List<String>): ArrayAdapter<String> = ArrayAdapter(
        this,
        android.R.layout.simple_list_item_1,
        items,
    )

    private fun dialogContainer(view: View): LinearLayout = LinearLayout(this).apply {
        setPadding(dp(24), dp(8), dp(24), 0)
        addView(view)
    }

    private fun formatDate(millis: Long): String = Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(dateFormatter)

    private fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(message: Int, length: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, length).show()
    }

    private enum class Section { TASKS, CATEGORIES, EVENTS }
}
