// model/Task.kt
package com.nicoceron.nimble.model // Updated package

import java.util.Date

data class Task(
    val taskId: Long?,
    val userId: Long?,
    val title: String?,
    val description: String?,
    val dueDate: Date?,
    val priority: TaskPriority?,
    val status: TaskStatus?,
    val createdDate: Date?,
    val lastModifiedDate: Date?
)