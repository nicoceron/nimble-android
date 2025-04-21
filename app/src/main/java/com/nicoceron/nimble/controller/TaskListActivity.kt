package com.nicoceron.nimble.controller

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.nicoceron.nimble.R
import com.nicoceron.nimble.model.SoapRepository
import com.nicoceron.nimble.model.Task
import com.nicoceron.nimble.model.TaskPriority
import com.nicoceron.nimble.model.TaskStatus // Import TaskStatus
import kotlinx.coroutines.launch

class TaskListActivity : AppCompatActivity() {

    private lateinit var tasksRecyclerView: RecyclerView
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var noTasksTextView: TextView
    private lateinit var fabAddTask: FloatingActionButton
    private lateinit var emptyStateContainer: LinearLayout // Added reference

    private var currentUserId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_list)

        // Initialize views
        tasksRecyclerView = findViewById(R.id.recyclerViewTasks)
        progressBar = findViewById(R.id.progressBarTasks)
        // Correctly initialize noTasksTextView from its container
        emptyStateContainer = findViewById(R.id.emptyStateContainer) // Find container first
        noTasksTextView = findViewById(R.id.textViewNoTasks) // This is inside emptyStateContainer
        fabAddTask = findViewById(R.id.fabAddTask)

        // Setup Toolbar (Optional, if you have one in your layout)
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        currentUserId = intent.getLongExtra("USER_ID", -1L)
        if (currentUserId == -1L) {
            Toast.makeText(this, getString(R.string.error_user_id_not_found), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupRecyclerView()
        loadTasks()

        fabAddTask.setOnClickListener {
            showCreateTaskDialog()
        }
    }

    private fun setupRecyclerView() {
        // --- Pass both lambdas to the adapter ---
        taskAdapter = TaskAdapter(
            onDeleteClick = { task -> confirmAndDeleteTask(task) },
            onStatusChangeClick = { task, newStatus -> updateTaskStatus(task, newStatus) }
        )
        // --- End of change ---

        tasksRecyclerView.apply {
            adapter = taskAdapter
            layoutManager = LinearLayoutManager(this@TaskListActivity)
        }
    }

    private fun loadTasks() {
        progressBar.visibility = View.VISIBLE
        emptyStateContainer.visibility = View.GONE // Hide empty state
        tasksRecyclerView.visibility = View.GONE // Hide recycler view initially

        lifecycleScope.launch {
            val result = SoapRepository.getTasksForUser(currentUserId)
            progressBar.visibility = View.GONE // Hide progress bar after fetch

            result.onSuccess { tasks ->
                if (!tasks.isNullOrEmpty()) {
                    // Submit list to ListAdapter
                    taskAdapter.submitList(tasks)
                    tasksRecyclerView.visibility = View.VISIBLE // Show recycler view
                    emptyStateContainer.visibility = View.GONE // Keep empty state hidden
                } else {
                    taskAdapter.submitList(emptyList()) // Submit empty list
                    tasksRecyclerView.visibility = View.GONE // Hide recycler view
                    emptyStateContainer.visibility = View.VISIBLE // Show empty state
                    noTasksTextView.text = getString(R.string.info_no_tasks_found) // Set text in empty state
                }
            }.onFailure { exception ->
                tasksRecyclerView.visibility = View.GONE // Hide recycler view
                emptyStateContainer.visibility = View.VISIBLE // Show empty state
                noTasksTextView.text = getString(R.string.error_loading_tasks) // Set error text
                val errorMsg = getString(R.string.error_generic_prefix, exception.message ?: "Unknown error")
                Toast.makeText(this@TaskListActivity, errorMsg , Toast.LENGTH_LONG).show()
                exception.printStackTrace()
            }
        }
    }


    // --- Task Creation ---
    private fun showCreateTaskDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_task, null)

        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // Find views in the custom layout
        val titleEditText = dialogView.findViewById<EditText>(R.id.editTextTaskTitleDialog)
        val descriptionEditText = dialogView.findViewById<EditText>(R.id.editTextTaskDescriptionDialog)
        val prioritySpinner = dialogView.findViewById<Spinner>(R.id.spinnerTaskPriorityDialog)

        // Setup priority spinner
        val priorities = TaskPriority.values().map { it.name } // Use enum values directly
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, priorities)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        prioritySpinner.adapter = adapter
        // Optionally set a default selection (e.g., MEDIUM)
        prioritySpinner.setSelection(adapter.getPosition(TaskPriority.MEDIUM.name))


        // Set click listeners for buttons in the custom layout
        dialogView.findViewById<Button>(R.id.buttonCancelTask).setOnClickListener {
            alertDialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.buttonCreateTask).setOnClickListener {
            val title = titleEditText.text.toString().trim()
            val description = descriptionEditText.text.toString().trim()
            val priorityString = prioritySpinner.selectedItem.toString()

            // Convert the String priority to TaskPriority enum safely
            val priority = TaskPriority.values().firstOrNull { it.name == priorityString } ?: TaskPriority.MEDIUM


            if (title.isNotEmpty()) {
                // Create task with these values
                createTask(title, description, priority)
                alertDialog.dismiss()
            } else {
                titleEditText.error = "Title is required"
            }
        }

        alertDialog.show()
    }

    private fun createTask(title: String, description: String?, priority: TaskPriority) {
        progressBar.visibility = View.VISIBLE // Show progress
        lifecycleScope.launch {
            val result = SoapRepository.createTaskWithoutDate(currentUserId, title, description, priority)
            progressBar.visibility = View.GONE // Hide progress
            result.onSuccess { newTask ->
                if (newTask?.taskId != null) {
                    Toast.makeText(this@TaskListActivity, getString(R.string.success_task_created, newTask.title ?: "Untitled"), Toast.LENGTH_SHORT).show()
                    // Efficiently add to ListAdapter: Create a new list including the new task
                    val currentList = taskAdapter.currentList.toMutableList()
                    currentList.add(0, newTask) // Add to the beginning
                    taskAdapter.submitList(currentList.toList()) // Submit an immutable copy

                    tasksRecyclerView.scrollToPosition(0) // Scroll to the new task
                    emptyStateContainer.visibility = View.GONE // Ensure empty state is hidden
                    tasksRecyclerView.visibility = View.VISIBLE // Ensure recycler view is visible
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
        if (task.taskId == null) {
            Toast.makeText(this, getString(R.string.error_delete_invalid_id), Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_title_delete_task))
            .setMessage(getString(R.string.dialog_message_delete_confirm, task.title ?: "Untitled Task"))
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(getString(R.string.dialog_button_delete)) { dialog, _ ->
                deleteTask(task)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.dialog_button_cancel), null)
            .show()
    }

    private fun deleteTask(task: Task) {
        val taskIdToDelete = task.taskId ?: run {
            Toast.makeText(this, getString(R.string.error_delete_invalid_id), Toast.LENGTH_SHORT).show()
            return // Exit if taskId is null
        }

        progressBar.visibility = View.VISIBLE // Show progress
        lifecycleScope.launch {
            val result = SoapRepository.deleteTask(taskIdToDelete)
            progressBar.visibility = View.GONE // Hide progress

            result.onSuccess { success ->
                if (success) {
                    Toast.makeText(this@TaskListActivity, getString(R.string.success_task_deleted, task.title ?: "Untitled Task"), Toast.LENGTH_SHORT).show()

                    // Efficiently remove from ListAdapter
                    val currentList = taskAdapter.currentList.toMutableList()
                    val removed = currentList.removeAll { it.taskId == taskIdToDelete }
                    if (removed) {
                        taskAdapter.submitList(currentList.toList()) // Submit immutable copy
                    }

                    // Show "No tasks" if the list becomes empty
                    if (currentList.isEmpty()) {
                        emptyStateContainer.visibility = View.VISIBLE
                        tasksRecyclerView.visibility = View.GONE
                    } else {
                        emptyStateContainer.visibility = View.GONE
                        tasksRecyclerView.visibility = View.VISIBLE
                    }

                } else {
                    Toast.makeText(this@TaskListActivity, getString(R.string.error_task_delete_failed_server), Toast.LENGTH_LONG).show()
                }
            }.onFailure { exception ->
                val errorMsg = getString(R.string.error_deleting_task_prefix, exception.message ?: "Unknown error")
                Toast.makeText(this@TaskListActivity, errorMsg, Toast.LENGTH_LONG).show()
                exception.printStackTrace()
            }
        }
    }


    // --- Function to handle Task Status Update ---
    private fun updateTaskStatus(task: Task, newStatus: TaskStatus) {
        val taskIdToUpdate = task.taskId ?: run {
            Toast.makeText(this, "Cannot update task without ID", Toast.LENGTH_SHORT).show()
            return // Exit if taskId is null
        }

        progressBar.visibility = View.VISIBLE // Show progress indicator
        lifecycleScope.launch {
            // Pass the original task object and the desired new status
            val result = SoapRepository.updateTask(task, newStatus)
            progressBar.visibility = View.GONE // Hide progress indicator

            result.onSuccess { updatedTaskFromServer ->
                // Check if the update was successful and we got back an updated task
                if (updatedTaskFromServer?.taskId != null) {
                    Toast.makeText(this@TaskListActivity, "Task '${updatedTaskFromServer.title}' status updated to ${updatedTaskFromServer.status?.name}", Toast.LENGTH_SHORT).show()

                    // Efficiently update ListAdapter
                    val currentList = taskAdapter.currentList.toMutableList()
                    val index = currentList.indexOfFirst { it.taskId == taskIdToUpdate }
                    if (index != -1) {
                        // Replace the old task with the updated one from the server
                        currentList[index] = updatedTaskFromServer
                        taskAdapter.submitList(currentList.toList()) // Submit immutable copy
                    } else {
                        // Task wasn't found in the current list? This shouldn't ideally happen.
                        // As a fallback, reload the whole list.
                        Log.w("TaskListActivity", "Task with ID $taskIdToUpdate not found in adapter after update. Reloading list.")
                        loadTasks()
                    }

                } else {
                    // The server indicated failure or returned null
                    Toast.makeText(this@TaskListActivity, "Failed to update task status on server.", Toast.LENGTH_LONG).show()
                    // Optional: Consider reverting the UI change if the update failed decisively
                    // You might need to fetch the original task state again or handle this based on API response.
                }
            }.onFailure { exception ->
                Toast.makeText(this@TaskListActivity, "Error updating task: ${exception.message}", Toast.LENGTH_LONG).show()
                exception.printStackTrace()
                // Optional: Revert UI change on network/SOAP error too
            }
        }
    }

}