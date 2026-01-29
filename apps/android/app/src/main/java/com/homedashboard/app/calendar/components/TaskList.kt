package com.homedashboard.app.calendar.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    showAddButton: Boolean = true,
    // Inline handwriting support
    recognizer: HandwritingRecognizer? = null,
    onTaskTextRecognized: ((String) -> Unit)? = null,
    // Callbacks
    onTaskToggle: ((TaskUi) -> Unit)? = null,
    onTaskClick: ((TaskUi) -> Unit)? = null,
    onAddTask: (() -> Unit)? = null
) {
    val displayedTasks = if (showCompleted) {
        tasks
    } else {
        tasks.filter { !it.isCompleted }
    }

    val incompleteTasks = displayedTasks.filter { !it.isCompleted }
    val completedTasks = displayedTasks.filter { it.isCompleted }

    Column(modifier = modifier) {
        // Header with title, count, and + button
        // Padding matches DayCellHeader (horizontal = 10.dp, vertical = 6.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isCompact) 6.dp else 10.dp, vertical = if (isCompact) 4.dp else 6.dp),
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
                        MaterialTheme.typography.headlineSmall
                    },
                    fontWeight = FontWeight.SemiBold
                )

                if (incompleteTasks.isNotEmpty()) {
                    Text(
                        text = "${incompleteTasks.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Add button
            if (showAddButton && onAddTask != null) {
                IconButton(
                    onClick = onAddTask,
                    modifier = Modifier.size(if (isCompact) 28.dp else 32.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add task",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(if (isCompact) 18.dp else 20.dp)
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
                // Empty state - handwriting overlay will show "Write here" hint
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    // Incomplete tasks first
                    items(incompleteTasks, key = { it.id }) { task ->
                        TaskItem(
                            task = task,
                            isCompact = isCompact,
                            onToggle = { onTaskToggle?.invoke(task) },
                            onClick = { onTaskClick?.invoke(task) }
                        )
                    }

                    // Completed tasks (dimmed)
                    if (showCompleted && completedTasks.isNotEmpty()) {
                        item {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }

                        items(completedTasks, key = { it.id }) { task ->
                            TaskItem(
                                task = task,
                                isCompact = isCompact,
                                onToggle = { onTaskToggle?.invoke(task) },
                                onClick = { onTaskClick?.invoke(task) }
                            )
                        }
                    }
                }
            }

            // Layer 2: Handwriting overlay (on top, but transparent to finger touches)
            // This allows users to write with stylus while tapping tasks with finger
            if (recognizer != null && onTaskTextRecognized != null) {
                InlineTaskWritingArea(
                    recognizer = recognizer,
                    modifier = Modifier.fillMaxSize(),
                    isCompact = isCompact,
                    onTaskTextRecognized = onTaskTextRecognized,
                    stylusOnly = true,  // Only capture stylus, let finger taps through
                    onFingerTap = if (displayedTasks.isEmpty()) onAddTask else null
                )
            } else if (displayedTasks.isEmpty() && onAddTask != null) {
                // Fallback to tap-to-add hint when no handwriting support
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onAddTask() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Add task",
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            modifier = Modifier.size(if (isCompact) 16.dp else 24.dp)
                        )
                        if (!isCompact) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Write here",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = 8.dp,
                vertical = if (isCompact) 4.dp else 6.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Checkbox
        IconButton(
            onClick = onToggle,
            modifier = Modifier.size(if (isCompact) 20.dp else 24.dp)
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
                style = if (isCompact) {
                    MaterialTheme.typography.bodySmall
                } else {
                    MaterialTheme.typography.bodyMedium
                },
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
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = MaterialTheme.shapes.medium
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Handwriting input",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            }
        }
    }
}
