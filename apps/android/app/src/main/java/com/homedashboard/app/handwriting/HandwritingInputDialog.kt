package com.homedashboard.app.handwriting

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Full-screen dialog for handwriting input.
 * Shows a large writing area and parses the recognized text into event details.
 */
@Composable
fun HandwritingInputDialog(
    date: LocalDate,
    recognizer: HandwritingRecognizer,
    parser: NaturalLanguageParser,
    onDismiss: () -> Unit,
    onEventCreated: (ParsedEvent) -> Unit
) {
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")
    var recognizedText by remember { mutableStateOf<String?>(null) }
    var parsedEvent by remember { mutableStateOf<ParsedEvent?>(null) }
    var showConfirmation by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.7f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Write Event",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = date.format(dateFormatter),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                HorizontalDivider()

                if (showConfirmation && parsedEvent != null) {
                    // Show confirmation of parsed event
                    ParsedEventConfirmation(
                        parsedEvent = parsedEvent!!,
                        onConfirm = {
                            onEventCreated(parsedEvent!!)
                        },
                        onEdit = {
                            showConfirmation = false
                            recognizedText = null
                            parsedEvent = null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(16.dp)
                    )
                } else {
                    // Show handwriting canvas
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(16.dp)
                    ) {
                        HandwritingCanvas(
                            modifier = Modifier.fillMaxSize(),
                            onRecognitionResult = { text ->
                                recognizedText = text
                                parsedEvent = parser.parse(text, date)
                                showConfirmation = true
                            },
                            onDismiss = onDismiss,
                            recognizer = recognizer,
                            hintText = "Write your event (e.g., \"Soccer 3pm\" or \"Dinner at 7\")"
                        )
                    }
                }

                // Tips section (only when not in confirmation)
                if (!showConfirmation) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Tips for best results:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• Write clearly with consistent sizing\n" +
                                        "• Include time like \"3pm\" or \"2:30\"\n" +
                                        "• Add location with \"at\" (e.g., \"at Gym\")",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shows the parsed event for user confirmation before creating.
 */
@Composable
private fun ParsedEventConfirmation(
    parsedEvent: ParsedEvent,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Is this correct?",
            style = MaterialTheme.typography.titleMedium
        )

        // Parsed event details
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Title
                Text(
                    text = parsedEvent.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                // Time
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Time:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (parsedEvent.isAllDay) {
                            "All day"
                        } else {
                            parsedEvent.startTime?.let { start ->
                                val end = parsedEvent.endTime
                                "${start.hour}:${start.minute.toString().padStart(2, '0')}" +
                                        (end?.let { " - ${it.hour}:${it.minute.toString().padStart(2, '0')}" } ?: "")
                            } ?: "Not specified"
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // Location (if present)
                parsedEvent.location?.let { location ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Location:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = location,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                // Original text
                Text(
                    text = "You wrote: \"${parsedEvent.originalText}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            OutlinedButton(onClick = onEdit) {
                Text("Write Again")
            }
            Button(onClick = onConfirm) {
                Text("Create Event")
            }
        }
    }
}

/**
 * Compact handwriting button that can be placed in a day cell.
 * Opens the full handwriting dialog when clicked.
 */
@Composable
fun HandwritingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = if (isCompact) 4.dp else 8.dp,
            vertical = if (isCompact) 2.dp else 4.dp
        )
    ) {
        Text(
            text = if (isCompact) "✏️" else "✏️ Write",
            style = if (isCompact) {
                MaterialTheme.typography.labelSmall
            } else {
                MaterialTheme.typography.labelMedium
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}
