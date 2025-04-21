// view/TaskAdapter.kt
package com.nicoceron.nimble.controller

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nicoceron.nimble.R
import com.nicoceron.nimble.model.Task

class TaskAdapter(
    private var tasks: MutableList<Task>,
    private val onDeleteClick: (Task) -> Unit // Lambda for delete action
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    // ViewHolder class
    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.textViewTaskTitle)
        val descriptionTextView: TextView = itemView.findViewById(R.id.textViewTaskDescription)
        val statusTextView: TextView = itemView.findViewById(R.id.textViewTaskStatus)
        val deleteButton: ImageButton = itemView.findViewById(R.id.buttonDeleteTask) // Get button
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        holder.titleTextView.text = task.title ?: "No Title"
        holder.descriptionTextView.text = task.description ?: ""
        holder.descriptionTextView.visibility = if (task.description.isNullOrEmpty()) View.GONE else View.VISIBLE
        holder.statusTextView.text = "Status: ${task.status?.name ?: "Unknown"}"

        // Set listener for the delete button
        holder.deleteButton.setOnClickListener {
            onDeleteClick(task) // Call the lambda passed from the Activity
        }

        // Add click listener for the item itself if needed (e.g., to view/edit details)
        holder.itemView.setOnClickListener {
            // Handle item click - maybe open detail view
        }
    }

    override fun getItemCount(): Int = tasks.size

    fun updateTasks(newTasks: List<Task>) {
        tasks.clear()
        tasks.addAll(newTasks)
        notifyDataSetChanged() // Use DiffUtil in production
    }

    // Optional: Function to remove a task directly for immediate UI update
    fun removeTask(taskToRemove: Task) {
        val position = tasks.indexOfFirst { it.taskId == taskToRemove.taskId }
        if (position != -1) {
            tasks.removeAt(position)
            notifyItemRemoved(position)
        }
    }
    // Optional: Function to add a task directly for immediate UI update
    fun addTask(newTask: Task) {
        tasks.add(0, newTask) // Add to top
        notifyItemInserted(0)
    }
}