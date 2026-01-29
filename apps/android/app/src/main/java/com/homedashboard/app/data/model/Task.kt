package com.homedashboard.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Represents a task/reminder in the local database.
 */
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey
    val id: String,

    // Task details
    val title: String,
    val description: String? = null,

    // Status
    val isCompleted: Boolean = false,
    val completedAt: ZonedDateTime? = null,

    // Due date (optional)
    val dueDate: LocalDate? = null,

    // Priority
    val priority: TaskPriority = TaskPriority.NORMAL,

    // Local state
    val createdAt: ZonedDateTime = ZonedDateTime.now(),
    val updatedAt: ZonedDateTime = ZonedDateTime.now(),
    val isDeleted: Boolean = false
)

/**
 * Task priority levels
 */
enum class TaskPriority {
    LOW,
    NORMAL,
    HIGH
}
