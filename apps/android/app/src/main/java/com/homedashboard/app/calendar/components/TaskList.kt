package com.homedashboard.app.calendar.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homedashboard.app.handwriting.HandwritingRecognizer
import com.homedashboard.app.handwriting.InlineTaskWritingArea
import com.homedashboard.app.ui.theme.LocalDimensions
import com.homedashboard.app.ui.theme.LocalIsEInk
import com.homedashboard.app.ui.theme.LocalIsWallCalendar

/**
 * UI model for tasks
 */
data class TaskUi(
    val id: String,
    val title: String,
    val isCompleted: Boolean,
    val dueDate: String? = null, // Formatted date string
    val priority: TaskPriority = TaskPriority.NORMAL
)

enum class TaskPriority {
    LOW, NORMAL, HIGH
}

/**
 * Task list component for displaying in layouts
 */
@Composable
fun TaskList(
    tasks: List<TaskUi>,
    modifier: Modifier = Modifier,
    title: String = "Tasks",
    showCompleted: Boolean = true,
    isCompact: Boolean = false,
    columns: Int = 2,
    showAddButton: Boolean = true,
    showWriteHint: Boolean = true,
    // Inline handwriting support
    recognizer: HandwritingRecognizer? = null,
    onTaskTextRecognized: ((String) -> Unit)? = null,
    onHandwritingUsed: (() -> Unit)? = null,
    // Callbacks
    onTaskToggle: ((TaskUi) -> Unit)? = null,
    onTaskClick: ((TaskUi) -> Unit)? = null,
    onAddTask: (() -> Unit)? = null
) {
    val dims = LocalDimensions.current
    val isEInk = LocalIsEInk.current

    val displayedTasks = if (showCompleted) {
        tasks
    } else {
        tasks.filter { !it.isCompleted }
    }

    val incompleteTasks = displayedTasks.filter { !it.isCompleted }
    val completedTasks = displayedTasks.filter { it.isCompleted }

    Column(modifier = modifier) {
        // Header with title, count, and + button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (isCompact) 6.dp else dims.cellHeaderPaddingHorizontal,
                    vertical = if (isCompact) 4.dp else dims.cellHeaderPaddingVertical
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Title and count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = if (isCompact) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.headlineMedium
                    },
                    fontWeight = FontWeight.SemiBold
                )

                if (incompleteTasks.isNotEmpty()) {
                    Text(
                        text = "${incompleteTasks.size}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Add button
            if (showAddButton && onAddTask != null) {
                IconButton(
                    onClick = onAddTask,
                    modifier = Modifier.size(dims.buttonSizeSmall)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add task",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(dims.buttonIconSize)
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Content area with task list and handwriting overlay
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Layer 1: Task list (receives finger taps)
            if (displayedTasks.isEmpty()) {
                // Empty state hint - only shown if write hints haven't been dismissed
                if (showWriteHint) {
                    val isWallCalendar = LocalIsWallCalendar.current
                    val hintColor = if (isEInk) {
                        androidx.compose.ui.graphics.Color(0xFFA0A0A0)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (recognizer == null && onAddTask != null) {
                                    Modifier.clickable { onAddTask() }
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Add task",
                            tint = hintColor,
                            modifier = Modifier.size(if (isCompact) 14.dp else if (isWallCalendar) 20.dp else 16.dp)
                        )
                    }
                }
            } else {
                // All tasks in the same two-column grid (incomplete first, then completed)
                // so checked/unchecked tasks have identical layout and spacing.
                val orderedTasks = incompleteTasks + completedTasks
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(orderedTasks, key = { it.id }) { task ->
                        TaskItem(
                            task = task,
                            isCompact = isCompact,
                            onToggle = { onTaskToggle?.invoke(task) },
                            onClick = { onTaskClick?.invoke(task) }
                        )
                    }
                }
            }

            // Layer 2: Handwriting overlay (on top, but transparent to finger touches)
            if (recognizer != null && onTaskTextRecognized != null) {
                InlineTaskWritingArea(
                    recognizer = recognizer,
                    modifier = Modifier.fillMaxSize(),
                    isCompact = isCompact,
                    onTaskTextRecognized = onTaskTextRecognized,
                    onHandwritingUsed = onHandwritingUsed,
                    stylusOnly = true,
                    onFingerTap = if (displayedTasks.isEmpty()) onAddTask else null
                )
            }
        }
    }
}

@Composable
private fun TaskItem(
    task: TaskUi,
    isCompact: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    val dims = LocalDimensions.current
    val isEInk = LocalIsEInk.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(
                    horizontal = 8.dp,
                    vertical = if (isCompact) 4.dp else 2.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Checkbox
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(if (isCompact) 24.dp else dims.checkboxSize)
            ) {
                Icon(
                    imageVector = if (task.isCompleted) {
                        Icons.Default.CheckBox
                    } else {
                        Icons.Default.CheckBoxOutlineBlank
                    },
                    contentDescription = if (task.isCompleted) "Mark incomplete" else "Mark complete",
                    tint = if (task.isCompleted) {
                        MaterialTheme.colorScheme.outline
                    } else {
                        when (task.priority) {
                            TaskPriority.HIGH -> MaterialTheme.colorScheme.error
                            TaskPriority.NORMAL -> MaterialTheme.colorScheme.onSurface
                            TaskPriority.LOW -> MaterialTheme.colorScheme.outline
                        }
                    }
                )
            }

            // Task text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = if (isCompact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleLarge,
                    textDecoration = if (task.isCompleted) {
                        TextDecoration.LineThrough
                    } else {
                        TextDecoration.None
                    },
                    color = if (task.isCompleted) {
                        MaterialTheme.colorScheme.outline
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = if (isCompact) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!isCompact && task.dueDate != null) {
                    Text(
                        text = task.dueDate,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Bottom border separator
        if (isEInk) {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

/**
 * Quick add area for handwriting input
 */
@Composable
fun QuickAddArea(
    modifier: Modifier = Modifier,
    placeholder: String = "Write to add...",
    onInput: (String) -> Unit = {}
) {
    val isEInk = LocalIsEInk.current
    val isWallCalendar = LocalIsWallCalendar.current
    val hintColor = if (isEInk) {
        androidx.compose.ui.graphics.Color(0xFFA0A0A0)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    }

    Surface(
        modifier = modifier,
        color = if (isEInk) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        shape = MaterialTheme.shapes.medium
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "Handwriting input",
                modifier = Modifier.size(if (isWallCalendar) 20.dp else 16.dp),
                tint = hintColor
            )
        }
    }
}
