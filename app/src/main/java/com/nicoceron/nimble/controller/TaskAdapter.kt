package com.nicoceron.nimble.controller

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nicoceron.nimble.R
import com.nicoceron.nimble.model.Task
import com.nicoceron.nimble.model.TaskPriority
import com.nicoceron.nimble.model.TaskStatus

class TaskAdapter(
    private val onDeleteClick: (Task) -> Unit,
    private val onStatusChangeClick: (Task, TaskStatus) -> Unit
) : ListAdapter<Task, TaskAdapter.TaskViewHolder>(TaskDiffCallback()) {

    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.textViewTaskTitle)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.textViewTaskDescription)
        private val statusTextView: TextView = itemView.findViewById(R.id.textViewTaskStatus)
        private val priorityTextView: TextView = itemView.findViewById(R.id.textViewTaskPriority)
        private val priorityIndicator: View = itemView.findViewById(R.id.viewPriorityIndicator)
        private val completionCheckIcon: ImageView = itemView.findViewById(R.id.imageViewTaskComplete)
        val deleteButton: ImageButton = itemView.findViewById(R.id.buttonDeleteTask)
        val startButton: ImageButton = itemView.findViewById(R.id.buttonStartTask)
        val completeButton: ImageButton = itemView.findViewById(R.id.buttonCompleteTask)

        fun bind(task: Task, onDeleteClick: (Task) -> Unit, onStatusChangeClick: (Task, TaskStatus) -> Unit) {
            titleTextView.text = task.title ?: "No Title"

            // Description handling
            if (task.description.isNullOrBlank()) {
                descriptionTextView.visibility = View.GONE
            } else {
                descriptionTextView.text = task.description
                descriptionTextView.visibility = View.VISIBLE
            }

            // Status handling
            val status = task.status ?: TaskStatus.PENDING
            statusTextView.text = status.name

            // Set status text color based on status
            val statusColor = when(status) {
                TaskStatus.PENDING -> Color.parseColor("#757575") // Grey
                TaskStatus.IN_PROGRESS -> Color.parseColor("#1976D2") // Blue
                TaskStatus.COMPLETED -> Color.parseColor("#4CAF50") // Green
            }
            statusTextView.setTextColor(statusColor)

            // Set priority text and color
            val priority = task.priority ?: TaskPriority.MEDIUM
            priorityTextView.text = priority.name

            // Set priority colors
            val priorityColor = when(priority) {
                TaskPriority.HIGH -> Color.parseColor("#F44336") // Red
                TaskPriority.MEDIUM -> Color.parseColor("#FF9800") // Orange
                TaskPriority.LOW -> Color.parseColor("#4CAF50") // Green
            }

            priorityTextView.backgroundTintList = ColorStateList.valueOf(priorityColor)
            priorityIndicator.setBackgroundColor(priorityColor)

            // Set completion check visibility
            completionCheckIcon.visibility = if (status == TaskStatus.COMPLETED) View.VISIBLE else View.GONE

            // Reset listeners to avoid conflicts with recycled views
            startButton.setOnClickListener(null)
            completeButton.setOnClickListener(null)
            deleteButton.setOnClickListener { onDeleteClick(task) }

            // Control Visibility and Set Listeners for Status Buttons
            when (status) {
                TaskStatus.PENDING -> {
                    startButton.visibility = View.VISIBLE
                    completeButton.visibility = View.GONE
                    startButton.setOnClickListener { onStatusChangeClick(task, TaskStatus.IN_PROGRESS) }
                }
                TaskStatus.IN_PROGRESS -> {
                    startButton.visibility = View.GONE
                    completeButton.visibility = View.VISIBLE
                    completeButton.setOnClickListener { onStatusChangeClick(task, TaskStatus.COMPLETED) }
                }
                TaskStatus.COMPLETED -> {
                    startButton.visibility = View.GONE
                    completeButton.visibility = View.GONE
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = getItem(position)
        holder.bind(task, onDeleteClick, onStatusChangeClick)
    }

    class TaskDiffCallback : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task): Boolean {
            return oldItem.taskId == newItem.taskId
        }

        override fun areContentsTheSame(oldItem: Task, newItem: Task): Boolean {
            return oldItem == newItem
        }
    }
}