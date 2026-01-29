package com.homedashboard.app.calendar.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.homedashboard.app.calendar.components.TaskPriority
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Data class containing full task details for display and editing.
 */
data class TaskDetails(
    val id: String,
    val title: String,
    val description: String?,
    val dueDate: LocalDate?,
    val priority: TaskPriority,
    val isCompleted: Boolean
)

/**
 * Dialog for viewing and editing task details.
 * Starts in view mode, can switch to edit mode.
 * Optimized for e-ink with clear contrast and simple interactions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailDialog(
    task: TaskDetails,
    onDismiss: () -> Unit,
    onUpdate: (
        taskId: String,
        title: String,
        dueDate: LocalDate?,
        priority: TaskPriority,
        description: String?
    ) -> Unit,
    onToggleCompletion: (taskId: String) -> Unit,
    onDelete: (taskId: String) -> Unit
) {
    var isEditMode by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // Editable fields
    var title by remember { mutableStateOf(task.title) }
    var dueDate by remember { mutableStateOf(task.dueDate) }
    var priority by remember { mutableStateOf(task.priority) }
    var description by remember { mutableStateOf(task.description ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
    val shortDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditMode) "Edit Task" else "Task Details",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (isEditMode) {
                    // Edit mode - show form fields
                    EditModeContent(
                        title = title,
                        onTitleChange = { title = it },
                        dueDate = dueDate,
                        onDueDateClick = { showDatePicker = true },
                        onClearDueDate = { dueDate = null },
                        priority = priority,
                        onPriorityChange = { priority = it },
                        description = description,
                        onDescriptionChange = { description = it },
                        dateFormatter = shortDateFormatter,
                        focusManager = focusManager
                    )
                } else {
                    // View mode - show read-only details
                    ViewModeContent(
                        title = task.title,
                        description = task.description,
                        dueDate = task.dueDate,
                        priority = task.priority,
                        isCompleted = task.isCompleted,
                        dateFormatter = dateFormatter,
                        onToggleCompletion = { onToggleCompletion(task.id) }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                if (isEditMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                // Reset to original values and exit edit mode
                                title = task.title
                                dueDate = task.dueDate
                                priority = task.priority
                                description = task.description ?: ""
                                isEditMode = false
                            }
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (title.isNotBlank()) {
                                    onUpdate(
                                        task.id,
                                        title.trim(),
                                        dueDate,
                                        priority,
                                        description.takeIf { it.isNotBlank() }
                                    )
                                }
                            },
                            enabled = title.isNotBlank()
                        ) {
                            Text("Save")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showDeleteConfirmation = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete")
                        }
                        Button(onClick = { isEditMode = true }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit")
                        }
                    }
                }
            }
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        TaskDatePickerDialog(
            initialDate = dueDate ?: LocalDate.now(),
            onDismiss = { showDatePicker = false },
            onConfirm = { date ->
                dueDate = date
                showDatePicker = false
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete Task?") },
            text = {
                Text("Are you sure you want to delete \"${task.title}\"? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete(task.id)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ViewModeContent(
    title: String,
    description: String?,
    dueDate: LocalDate?,
    priority: TaskPriority,
    isCompleted: Boolean,
    dateFormatter: DateTimeFormatter,
    onToggleCompletion: () -> Unit
) {
    // Title with completion toggle
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(
            onClick = onToggleCompletion,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (isCompleted) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = if (isCompleted) "Mark incomplete" else "Mark complete",
                tint = if (isCompleted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    when (priority) {
                        TaskPriority.HIGH -> MaterialTheme.colorScheme.error
                        TaskPriority.NORMAL -> MaterialTheme.colorScheme.onSurface
                        TaskPriority.LOW -> MaterialTheme.colorScheme.outline
                    }
                }
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
            color = if (isCompleted) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Priority
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Default.Flag,
            contentDescription = null,
            tint = when (priority) {
                TaskPriority.HIGH -> MaterialTheme.colorScheme.error
                TaskPriority.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
                TaskPriority.LOW -> MaterialTheme.colorScheme.outline
            },
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = when (priority) {
                TaskPriority.HIGH -> "High priority"
                TaskPriority.NORMAL -> "Normal priority"
                TaskPriority.LOW -> "Low priority"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = when (priority) {
                TaskPriority.HIGH -> MaterialTheme.colorScheme.error
                TaskPriority.NORMAL -> MaterialTheme.colorScheme.onSurface
                TaskPriority.LOW -> MaterialTheme.colorScheme.outline
            }
        )
    }

    // Due date (if present)
    if (dueDate != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.CalendarToday,
                contentDescription = null,
                tint = if (dueDate.isBefore(LocalDate.now()) && !isCompleted) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = if (dueDate.isBefore(LocalDate.now()) && !isCompleted) {
                    "Overdue: ${dueDate.format(dateFormatter)}"
                } else {
                    "Due: ${dueDate.format(dateFormatter)}"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (dueDate.isBefore(LocalDate.now()) && !isCompleted) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }

    // Description (if present)
    if (!description.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Completion status
    if (isCompleted) {
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.small
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Completed",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditModeContent(
    title: String,
    onTitleChange: (String) -> Unit,
    dueDate: LocalDate?,
    onDueDateClick: () -> Unit,
    onClearDueDate: () -> Unit,
    priority: TaskPriority,
    onPriorityChange: (TaskPriority) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    dateFormatter: DateTimeFormatter,
    focusManager: androidx.compose.ui.focus.FocusManager
) {
    // Title field
    OutlinedTextField(
        value = title,
        onValueChange = onTitleChange,
        label = { Text("Task title") },
        placeholder = { Text("What needs to be done?") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) }
        )
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Due date picker
    OutlinedCard(
        onClick = onDueDateClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column {
                    Text(
                        text = "Due date",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dueDate?.format(dateFormatter) ?: "No due date",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (dueDate != null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            if (dueDate != null) {
                TextButton(onClick = onClearDueDate) {
                    Text("Clear")
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Priority selector
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Flag,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Priority",
                style = MaterialTheme.typography.labelLarge
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TaskPriority.entries.forEach { p ->
                FilterChip(
                    selected = priority == p,
                    onClick = { onPriorityChange(p) },
                    label = {
                        Text(
                            text = when (p) {
                                TaskPriority.LOW -> "Low"
                                TaskPriority.NORMAL -> "Normal"
                                TaskPriority.HIGH -> "High"
                            }
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (p) {
                            TaskPriority.LOW -> MaterialTheme.colorScheme.tertiaryContainer
                            TaskPriority.NORMAL -> MaterialTheme.colorScheme.secondaryContainer
                            TaskPriority.HIGH -> MaterialTheme.colorScheme.errorContainer
                        }
                    )
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Description field
    OutlinedTextField(
        value = description,
        onValueChange = onDescriptionChange,
        label = { Text("Notes") },
        placeholder = { Text("Add notes") },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp),
        maxLines = 4,
        leadingIcon = {
            Icon(
                Icons.Default.Description,
                contentDescription = null
            )
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { focusManager.clearFocus() }
        )
    )
}

/**
 * Date picker dialog for task editing
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.toEpochDay() * 24 * 60 * 60 * 1000
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = LocalDate.ofEpochDay(millis / (24 * 60 * 60 * 1000))
                        onConfirm(date)
                    }
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}
