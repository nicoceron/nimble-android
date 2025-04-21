// controller/TaskListActivity.kt
package com.nicoceron.nimble.controller // Ensure this matches your file location

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.nicoceron.nimble.R // Ensure R is imported from your app's package
import com.nicoceron.nimble.model.SoapRepository
import com.nicoceron.nimble.model.Task
import com.nicoceron.nimble.model.TaskPriority
import kotlinx.coroutines.launch

class TaskListActivity : AppCompatActivity() {

    private lateinit var tasksRecyclerView: RecyclerView
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var noTasksTextView: TextView
    private lateinit var fabAddTask: FloatingActionButton

    // CORRECT: Use Long type
    private var currentUserId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_list) // Ensure this layout exists

        tasksRecyclerView = findViewById(R.id.recyclerViewTasks)
        progressBar = findViewById(R.id.progressBarTasks)
        noTasksTextView = findViewById(R.id.textViewNoTasks)
        fabAddTask = findViewById(R.id.fabAddTask)

        // CORRECT: Use Long type for getting extra and comparing
        currentUserId = intent.getLongExtra("USER_ID", -1L) // Default value is -1L (Long)
        if (currentUserId == -1L) { // Compare with -1L (Long)
            // Use string resource
            Toast.makeText(this, getString(R.string.error_user_id_not_found), Toast.LENGTH_LONG).show()
            finish() // Close activity if user ID is invalid
            return
        }

        setupRecyclerView()
        loadTasks()

        // Set listener for the Floating Action Button
        fabAddTask.setOnClickListener {
            showCreateTaskDialog()
        }
    }

    private fun setupRecyclerView() {
        // Initialize adapter, passing the delete confirmation lambda
        taskAdapter = TaskAdapter(mutableListOf()) { task ->
            confirmAndDeleteTask(task)
        }
        tasksRecyclerView.apply {
            adapter = taskAdapter
            layoutManager = LinearLayoutManager(this@TaskListActivity)
        }
    }

    private fun loadTasks() {
        progressBar.visibility = View.VISIBLE
        noTasksTextView.visibility = View.GONE
        tasksRecyclerView.visibility = View.GONE

        lifecycleScope.launch {
            // currentUserId is already Long
            val result = SoapRepository.getTasksForUser(currentUserId)
            progressBar.visibility = View.GONE // Hide progress bar after call finishes
            result.onSuccess { tasks ->
                if (!tasks.isNullOrEmpty()) { // Use Kotlin's isNullOrEmpty
                    taskAdapter.updateTasks(tasks)
                    tasksRecyclerView.visibility = View.VISIBLE
                    noTasksTextView.visibility = View.GONE
                } else {
                    taskAdapter.updateTasks(emptyList()) // Clear adapter
                    tasksRecyclerView.visibility = View.GONE
                    noTasksTextView.visibility = View.VISIBLE
                    // Use string resource
                    noTasksTextView.text = getString(R.string.info_no_tasks_found)
                }
            }.onFailure { exception ->
                // Handle failure: show error message
                tasksRecyclerView.visibility = View.GONE
                noTasksTextView.visibility = View.VISIBLE
                // Use string resource
                noTasksTextView.text = getString(R.string.error_loading_tasks)
                // Format error message for Toast
                val errorMsg = getString(R.string.error_generic_prefix, exception.message ?: "Unknown error")
                Toast.makeText(this@TaskListActivity, errorMsg , Toast.LENGTH_LONG).show()
                exception.printStackTrace() // Log detailed error
            }
        }
    }

    // --- Task Creation ---
    private fun showCreateTaskDialog() {
        // Ensure layout file exists: res/layout/dialog_create_task.xml
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_task, null)
        val titleEditText = dialogView.findViewById<EditText>(R.id.editTextTaskTitleDialog)
        val descriptionEditText = dialogView.findViewById<EditText>(R.id.editTextTaskDescriptionDialog)
        val prioritySpinner = dialogView.findViewById<Spinner>(R.id.spinnerTaskPriorityDialog)

        // CORRECT: Use Enum.entries instead of Enum.values()
        val priorities = TaskPriority.entries.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, priorities)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        prioritySpinner.adapter = adapter

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_title_create_task))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.dialog_button_create)) { dialog, _ ->
                val title = titleEditText.text.toString().trim()
                val description = descriptionEditText.text.toString().trim() // Get description
                val selectedPriorityName = prioritySpinner.selectedItem as String
                val priority = try { TaskPriority.valueOf(selectedPriorityName) } catch (e: IllegalArgumentException) { TaskPriority.MEDIUM }

                // --- ADD VALIDATION FOR DESCRIPTION ---
                if (title.isEmpty()) {
                    // Use string resource for title validation
                    Toast.makeText(this, getString(R.string.validation_title_empty), Toast.LENGTH_SHORT).show()
                } else if (description.isEmpty()) { // Check if description is empty
                    // Use string resource for description validation
                    Toast.makeText(this, getString(R.string.validation_description_empty), Toast.LENGTH_SHORT).show()
                }
                // --- END OF ADDED VALIDATION ---
                else {
                    // Only proceed if both title and description are NOT empty
                    // Note: description.ifEmpty { null } is no longer needed here
                    // as we ensure description is not empty.
                    createTask(title, description, priority)
                    dialog.dismiss() // Dismiss dialog only on successful validation
                }
                // Removed dialog.dismiss() from here to prevent dismissing on validation error
            }
            .setNegativeButton(getString(R.string.dialog_button_cancel)) { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    private fun createTask(title: String, description: String?, priority: TaskPriority) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = SoapRepository.createTaskWithoutDate(currentUserId, title, description, priority)
            progressBar.visibility = View.GONE
            result.onSuccess { newTask ->
                if (newTask?.taskId != null) {
                    Toast.makeText(this@TaskListActivity, getString(R.string.success_task_created, newTask.title ?: "Untitled"), Toast.LENGTH_SHORT).show()

                    // *** CHANGE THIS PART ***
                    // Instead of reloading the whole list:
                    // loadTasks()

                    // Directly add the new task to the adapter's list:
                    taskAdapter.addTask(newTask)

                    // Optional: Scroll to the top to see the new task
                    tasksRecyclerView.scrollToPosition(0)

                    // Ensure "No tasks" text is hidden if it was visible
                    noTasksTextView.visibility = View.GONE
                    tasksRecyclerView.visibility = View.VISIBLE
                    // *** END OF CHANGE ***

                } else {
                    Toast.makeText(this@TaskListActivity, getString(R.string.error_task_create_failed_null), Toast.LENGTH_LONG).show()
                }
            }.onFailure { exception ->
                val errorMsg = getString(R.string.error_creating_task_prefix, exception.message ?: "Unknown error")
                Toast.makeText(this@TaskListActivity, errorMsg, Toast.LENGTH_LONG).show()
                exception.printStackTrace()
            }
        }
    }

    // --- Task Deletion ---
    private fun confirmAndDeleteTask(task: Task) {
        // CORRECT: Check task.taskId (which is Long?) against null
        if (task.taskId == null) {
            // Use string resource
            Toast.makeText(this, getString(R.string.error_delete_invalid_id), Toast.LENGTH_SHORT).show()
            return // Exit if ID is null
        }

        // Show confirmation dialog
        AlertDialog.Builder(this)
            // Use string resources
            .setTitle(getString(R.string.dialog_title_delete_task))
            // Use string resource with placeholder (handle potential null title)
            .setMessage(getString(R.string.dialog_message_delete_confirm, task.title ?: "Untitled Task"))
            .setIcon(android.R.drawable.ic_dialog_alert) // Standard warning icon
            // Use string resource
            .setPositiveButton(getString(R.string.dialog_button_delete)) { dialog, _ ->
                // Call deleteTask only if user confirms
                deleteTask(task)
                dialog.dismiss() // Dismiss dialog
            }
            // Use string resource
            .setNegativeButton(getString(R.string.dialog_button_cancel), null) // No action on cancel, just dismiss
            .show()
    }

    private fun deleteTask(task: Task) {
        lifecycleScope.launch {
            // CORRECT: Ensure taskId is non-null and explicitly use Long type
            val taskId: Long = task.taskId ?: return@launch // Exit coroutine if taskId is null
            // taskId is now guaranteed non-null and is Long
            val result = SoapRepository.deleteTask(taskId) // Pass the Long taskId

            result.onSuccess { success ->
                if (success) {
                    // Use string resource with placeholder
                    Toast.makeText(this@TaskListActivity, getString(R.string.success_task_deleted, task.title ?: "Untitled Task"), Toast.LENGTH_SHORT).show()
                    loadTasks() // Reload list to reflect deletion
                } else {
                    // Use string resource
                    Toast.makeText(this@TaskListActivity, getString(R.string.error_task_delete_failed_server), Toast.LENGTH_LONG).show()
                }
            }.onFailure { exception ->
                // Use string resource with placeholder
                val errorMsg = getString(R.string.error_deleting_task_prefix, exception.message ?: "Unknown error")
                Toast.makeText(this@TaskListActivity, errorMsg, Toast.LENGTH_LONG).show()
                exception.printStackTrace() // Log detailed error
            }
        }
    }
}