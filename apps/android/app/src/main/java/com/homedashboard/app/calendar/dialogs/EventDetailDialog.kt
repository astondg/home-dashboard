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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Data class containing full event details for display and editing.
 */
data class EventDetails(
    val id: String,
    val title: String,
    val description: String?,
    val location: String?,
    val date: LocalDate,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val isAllDay: Boolean,
    val calendarName: String
)

/**
 * Dialog for viewing and editing event details.
 * Starts in view mode, can switch to edit mode.
 * Optimized for e-ink with clear contrast and simple interactions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailDialog(
    event: EventDetails,
    onDismiss: () -> Unit,
    onUpdate: (
        eventId: String,
        title: String,
        date: LocalDate,
        startTime: LocalTime?,
        endTime: LocalTime?,
        isAllDay: Boolean,
        description: String?,
        location: String?
    ) -> Unit,
    onDelete: (eventId: String) -> Unit
) {
    var isEditMode by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // Editable fields
    var title by remember { mutableStateOf(event.title) }
    var isAllDay by remember { mutableStateOf(event.isAllDay) }
    var startTime by remember { mutableStateOf(event.startTime ?: LocalTime.of(9, 0)) }
    var endTime by remember { mutableStateOf(event.endTime ?: LocalTime.of(10, 0)) }
    var description by remember { mutableStateOf(event.description ?: "") }
    var location by remember { mutableStateOf(event.location ?: "") }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

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
                        text = if (isEditMode) "Edit Event" else "Event Details",
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

                Spacer(modifier = Modifier.height(8.dp))

                // Date display
                Text(
                    text = event.date.format(dateFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                // Calendar name
                Text(
                    text = event.calendarName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (isEditMode) {
                    // Edit mode - show form fields
                    EditModeContent(
                        title = title,
                        onTitleChange = { title = it },
                        isAllDay = isAllDay,
                        onAllDayChange = { isAllDay = it },
                        startTime = startTime,
                        endTime = endTime,
                        onStartTimeClick = { showStartTimePicker = true },
                        onEndTimeClick = { showEndTimePicker = true },
                        location = location,
                        onLocationChange = { location = it },
                        description = description,
                        onDescriptionChange = { description = it },
                        timeFormatter = timeFormatter,
                        focusManager = focusManager
                    )
                } else {
                    // View mode - show read-only details
                    ViewModeContent(
                        title = event.title,
                        isAllDay = event.isAllDay,
                        startTime = event.startTime,
                        endTime = event.endTime,
                        location = event.location,
                        description = event.description,
                        timeFormatter = timeFormatter
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
                                title = event.title
                                isAllDay = event.isAllDay
                                startTime = event.startTime ?: LocalTime.of(9, 0)
                                endTime = event.endTime ?: LocalTime.of(10, 0)
                                description = event.description ?: ""
                                location = event.location ?: ""
                                isEditMode = false
                            }
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (title.isNotBlank()) {
                                    onUpdate(
                                        event.id,
                                        title.trim(),
                                        event.date,
                                        if (isAllDay) null else startTime,
                                        if (isAllDay) null else endTime,
                                        isAllDay,
                                        description.takeIf { it.isNotBlank() },
                                        location.takeIf { it.isNotBlank() }
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

    // Time picker dialogs
    if (showStartTimePicker) {
        EventTimePickerDialog(
            initialTime = startTime,
            onDismiss = { showStartTimePicker = false },
            onConfirm = { time ->
                startTime = time
                if (time >= endTime) {
                    endTime = time.plusHours(1)
                }
                showStartTimePicker = false
            }
        )
    }

    if (showEndTimePicker) {
        EventTimePickerDialog(
            initialTime = endTime,
            onDismiss = { showEndTimePicker = false },
            onConfirm = { time ->
                endTime = time
                showEndTimePicker = false
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
            title = { Text("Delete Event?") },
            text = {
                Text("Are you sure you want to delete \"${event.title}\"? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete(event.id)
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
    isAllDay: Boolean,
    startTime: LocalTime?,
    endTime: LocalTime?,
    location: String?,
    description: String?,
    timeFormatter: DateTimeFormatter
) {
    // Title
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Time
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Default.AccessTime,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = if (isAllDay) {
                "All day"
            } else {
                "${startTime?.format(timeFormatter) ?: ""} - ${endTime?.format(timeFormatter) ?: ""}"
            },
            style = MaterialTheme.typography.bodyLarge
        )
    }

    // Location (if present)
    if (!location.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = location,
                style = MaterialTheme.typography.bodyLarge
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditModeContent(
    title: String,
    onTitleChange: (String) -> Unit,
    isAllDay: Boolean,
    onAllDayChange: (Boolean) -> Unit,
    startTime: LocalTime,
    endTime: LocalTime,
    onStartTimeClick: () -> Unit,
    onEndTimeClick: () -> Unit,
    location: String,
    onLocationChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    timeFormatter: DateTimeFormatter,
    focusManager: androidx.compose.ui.focus.FocusManager
) {
    // Title field
    OutlinedTextField(
        value = title,
        onValueChange = onTitleChange,
        label = { Text("Event title") },
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

    // All day switch
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "All-day",
            style = MaterialTheme.typography.bodyLarge
        )
        Switch(
            checked = isAllDay,
            onCheckedChange = onAllDayChange
        )
    }

    // Time pickers (only if not all-day)
    if (!isAllDay) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Start time
            OutlinedCard(
                onClick = onStartTimeClick,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        Text(
                            text = "Start",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = startTime.format(timeFormatter),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            // End time
            OutlinedCard(
                onClick = onEndTimeClick,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        Text(
                            text = "End",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = endTime.format(timeFormatter),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Location field
    OutlinedTextField(
        value = location,
        onValueChange = onLocationChange,
        label = { Text("Location") },
        placeholder = { Text("Add location") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null
            )
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) }
        )
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Description field
    OutlinedTextField(
        value = description,
        onValueChange = onDescriptionChange,
        label = { Text("Description") },
        placeholder = { Text("Add description") },
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
 * Time picker dialog for event editing
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventTimePickerDialog(
    initialTime: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = false
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(LocalTime.of(timePickerState.hour, timePickerState.minute))
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        text = {
            TimePicker(state = timePickerState)
        }
    )
}
