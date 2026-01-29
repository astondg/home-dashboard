package com.homedashboard.app.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homedashboard.app.calendar.CalendarEventUi
import com.homedashboard.app.handwriting.HandwritingRecognizer
import com.homedashboard.app.handwriting.InlineDayWritingArea
import com.homedashboard.app.handwriting.NaturalLanguageParser
import com.homedashboard.app.handwriting.ParsedEvent
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Header layout options for day cells
 */
enum class DayHeaderLayout {
    /** Day name above date number (vertical stack) */
    VERTICAL,
    /** Date number and day name side by side (horizontal, saves vertical space) */
    HORIZONTAL
}

/**
 * A single day cell that can be used in various layouts.
 * Supports direct handwriting input for adding events.
 */
@Composable
fun DayCell(
    date: LocalDate,
    events: List<CalendarEventUi>,
    modifier: Modifier = Modifier,
    isToday: Boolean = false,
    isCompact: Boolean = false,
    showDayName: Boolean = true,
    headerLayout: DayHeaderLayout = DayHeaderLayout.HORIZONTAL,
    showWriteHint: Boolean = true,
    showAddButton: Boolean = true,
    // Inline handwriting (direct writing in cell)
    recognizer: HandwritingRecognizer? = null,
    parser: NaturalLanguageParser? = null,
    onInlineEventCreated: ((ParsedEvent) -> Unit)? = null,
    // Legacy callbacks
    onAddClick: (() -> Unit)? = null,
    onWriteClick: (() -> Unit)? = null,  // Opens handwriting input dialog (fallback)
    onHandwritingInput: ((String) -> Unit)? = null,  // Legacy: direct text input
    onEventClick: ((CalendarEventUi) -> Unit)? = null,
    onCellClick: (() -> Unit)? = null
) {
    val backgroundColor = when {
        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    val borderColor = when {
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    val borderWidth = if (isToday) 2.dp else 1.dp

    Column(
        modifier = modifier
            .border(
                width = borderWidth,
                color = borderColor,
                shape = MaterialTheme.shapes.small
            )
            .background(
                color = backgroundColor,
                shape = MaterialTheme.shapes.small
            )
            .clickable(enabled = onCellClick != null) { onCellClick?.invoke() }
    ) {
        // Day header with optional + button
        DayCellHeader(
            date = date,
            isToday = isToday,
            isCompact = isCompact,
            showDayName = showDayName,
            layout = headerLayout,
            showAddButton = showAddButton,
            onAddClick = onAddClick
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Events list with stylus writing overlay
        // The overlay captures stylus input while letting finger taps through to events
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Layer 1: Events list (always shown, receives finger taps)
            if (events.isEmpty()) {
                // Empty state - show hint or empty placeholder
                if (showWriteHint && (onWriteClick != null || onHandwritingInput != null) &&
                    (recognizer == null || parser == null || onInlineEventCreated == null)) {
                    // Fallback to tap-to-write hint (no stylus writing available)
                    WriteHint(
                        modifier = Modifier.fillMaxSize(),
                        isCompact = isCompact,
                        onClick = onWriteClick
                    )
                }
                // If stylus writing is enabled, the overlay will show the "Write here" hint
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(if (isCompact) 2.dp else 4.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isCompact) 2.dp else 4.dp)
                ) {
                    items(events) { event ->
                        EventChip(
                            event = event,
                            isCompact = isCompact,
                            onClick = { onEventClick?.invoke(event) }
                        )
                    }
                }
            }

            // Layer 2: Stylus writing overlay (on top, but transparent to finger touches)
            // This allows users to write with stylus while tapping events with finger
            if (recognizer != null && parser != null && onInlineEventCreated != null) {
                InlineDayWritingArea(
                    date = date,
                    recognizer = recognizer,
                    parser = parser,
                    modifier = Modifier.fillMaxSize(),
                    isCompact = isCompact,
                    onEventCreated = onInlineEventCreated,
                    stylusOnly = true,  // Only capture stylus, let finger taps through
                    onFingerTap = if (events.isEmpty()) onWriteClick else null  // Open dialog on empty cell tap
                )
            }
        }
    }
}

@Composable
private fun DayCellHeader(
    date: LocalDate,
    isToday: Boolean,
    isCompact: Boolean,
    showDayName: Boolean,
    layout: DayHeaderLayout,
    showAddButton: Boolean,
    onAddClick: (() -> Unit)?
) {
    val primaryColor = if (isToday) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val secondaryColor = if (isToday) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    when (layout) {
        DayHeaderLayout.VERTICAL -> {
            // Original vertical layout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isCompact) 4.dp else 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (showDayName) {
                        Text(
                            text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            style = if (isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                            color = secondaryColor
                        )
                    }
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = if (isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = primaryColor
                    )
                }

                if (showAddButton && onAddClick != null) {
                    IconButton(
                        onClick = onAddClick,
                        modifier = Modifier.size(if (isCompact) 24.dp else 32.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add event",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(if (isCompact) 16.dp else 20.dp)
                        )
                    }
                }
            }
        }

        DayHeaderLayout.HORIZONTAL -> {
            // Horizontal layout: "29 Thu" with + button on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isCompact) 6.dp else 10.dp, vertical = if (isCompact) 4.dp else 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Date and day name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Date number (prominent)
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = if (isCompact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                        color = primaryColor
                    )

                    // Day name (secondary)
                    if (showDayName) {
                        Text(
                            text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            style = if (isCompact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                            color = secondaryColor
                        )
                    }
                }

                // Add button
                if (showAddButton && onAddClick != null) {
                    IconButton(
                        onClick = onAddClick,
                        modifier = Modifier.size(if (isCompact) 28.dp else 32.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add event",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(if (isCompact) 18.dp else 20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WriteHint(
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "Write to add event",
                tint = MaterialTheme.colorScheme.outline.copy(alpha = if (onClick != null) 0.6f else 0.4f),
                modifier = Modifier.size(if (isCompact) 16.dp else 24.dp)
            )
            if (!isCompact) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (onClick != null) "Tap to write" else "Write here",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = if (onClick != null) 0.6f else 0.4f)
                )
            }
        }
    }
}

/**
 * Compact event chip for displaying in day cells
 */
@Composable
fun EventChip(
    event: CalendarEventUi,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val chipColor = Color(event.color)
    val backgroundColor = chipColor.copy(alpha = 0.2f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        color = backgroundColor,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (isCompact) 4.dp else 6.dp,
                vertical = if (isCompact) 2.dp else 4.dp
            )
        ) {
            if (!event.isAllDay && event.startTime != null) {
                Text(
                    text = event.startTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = event.title,
                style = if (isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                maxLines = if (isCompact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * A minimal day cell for timeline/horizontal layouts
 * Shows just date header with events stacked below
 */
@Composable
fun TimelineDayCell(
    date: LocalDate,
    events: List<CalendarEventUi>,
    modifier: Modifier = Modifier,
    isToday: Boolean = false,
    showAddButton: Boolean = true,
    // Inline handwriting (direct writing in cell)
    recognizer: HandwritingRecognizer? = null,
    parser: NaturalLanguageParser? = null,
    onInlineEventCreated: ((ParsedEvent) -> Unit)? = null,
    // Legacy callbacks
    onAddClick: (() -> Unit)? = null,
    onWriteClick: (() -> Unit)? = null,
    onHandwritingInput: ((String) -> Unit)? = null,
    onEventClick: ((CalendarEventUi) -> Unit)? = null
) {
    val backgroundColor = when {
        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        else -> Color.Transparent
    }

    Column(
        modifier = modifier
            .background(backgroundColor)
            .padding(4.dp)
    ) {
        // Compact header with optional + button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent
                ) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            if (showAddButton && onAddClick != null) {
                IconButton(
                    onClick = onAddClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add event",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Events with stylus writing overlay
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Layer 1: Events (receives finger taps)
            if (events.isEmpty()) {
                // Show hint only if no stylus writing available
                if (recognizer == null || parser == null || onInlineEventCreated == null) {
                    WriteHint(
                        modifier = Modifier.fillMaxSize(),
                        isCompact = true,
                        onClick = onWriteClick
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(events) { event ->
                        EventChip(
                            event = event,
                            isCompact = true,
                            onClick = { onEventClick?.invoke(event) }
                        )
                    }
                }
            }

            // Layer 2: Stylus writing overlay
            if (recognizer != null && parser != null && onInlineEventCreated != null) {
                InlineDayWritingArea(
                    date = date,
                    recognizer = recognizer,
                    parser = parser,
                    modifier = Modifier.fillMaxSize(),
                    isCompact = true,
                    onEventCreated = onInlineEventCreated,
                    stylusOnly = true,
                    onFingerTap = if (events.isEmpty()) onWriteClick else null
                )
            }
        }
    }
}
