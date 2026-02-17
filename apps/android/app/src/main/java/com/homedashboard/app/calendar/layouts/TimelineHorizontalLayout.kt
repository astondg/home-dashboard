package com.homedashboard.app.calendar.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.homedashboard.app.calendar.CalendarEventUi
import com.homedashboard.app.calendar.components.*
import com.homedashboard.app.data.weather.DailyWeather
import com.homedashboard.app.handwriting.HandwritingRecognizer
import com.homedashboard.app.handwriting.InlineDayWritingArea
import com.homedashboard.app.handwriting.NaturalLanguageParser
import com.homedashboard.app.handwriting.ParsedEvent
import com.homedashboard.app.ui.theme.LocalDimensions
import com.homedashboard.app.ui.theme.LocalIsEInk
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Option C: Horizontal Timeline Layout with time axis
 *
 * Layout structure:
 * ┌──────┬────────┬────────┬────────┬────────┬────────┬────────┬────────┐
 * │ Time │ Thu 29 │ Fri 30 │ Sat 31 │ Sun 1  │ Mon 2  │ Tue 3  │ Wed 4  │
 * │      │ TODAY  │        │        │        │        │        │        │
 * │ 7a   │████████│        │        │        │████████│████████│        │
 * │ 8a   │████████│        │        │        │        │        │        │
 * │ ...  │        │        │        │        │        │        │        │
 * ├──────┴────────┴────────┴────────┴────────┴────────┴────────┴────────┤
 * │  📋 TASKS                     │  ✏️ QUICK ADD                       │
 * └───────────────────────────────┴─────────────────────────────────────┘
 */
@Composable
fun TimelineHorizontalLayout(
    days: List<LocalDate>,
    eventsMap: Map<LocalDate, List<CalendarEventUi>>,
    tasks: List<TaskUi>,
    modifier: Modifier = Modifier,
    weatherByDate: Map<LocalDate, DailyWeather> = emptyMap(),
    showWriteHints: Boolean = true,
    showTasks: Boolean = true,
    showQuickAdd: Boolean = true,
    // Inline handwriting support
    recognizer: HandwritingRecognizer? = null,
    parser: NaturalLanguageParser? = null,
    onInlineEventCreated: ((ParsedEvent) -> Unit)? = null,
    onTaskTextRecognized: ((String) -> Unit)? = null,
    onHandwritingUsed: (() -> Unit)? = null,
    // Callbacks
    onAddEventClick: (LocalDate) -> Unit = {},
    onWriteClick: (LocalDate) -> Unit = {},
    onAddTaskClick: () -> Unit = {},
    onHandwritingInput: (LocalDate, String) -> Unit = { _, _ -> },
    onEventClick: (CalendarEventUi) -> Unit = {},
    onTaskToggle: (TaskUi) -> Unit = {},
    onTaskClick: (TaskUi) -> Unit = {},
    onQuickAddInput: (String) -> Unit = {}
) {
    val dims = LocalDimensions.current
    val isEInk = LocalIsEInk.current
    val today = LocalDate.now()

    // Compute shared hour range across all days
    val (startHour, endHour) = remember(eventsMap) {
        computeHourRange(eventsMap)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp)
    ) {
        // Main calendar area - time axis + 7 day columns
        Row(
            modifier = Modifier
                .weight(if (showTasks || showQuickAdd) 0.8f else 1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Time axis column
            Column(
                modifier = Modifier
                    .width(30.dp)
                    .fillMaxHeight()
            ) {
                // Empty header space to align with day headers
                Spacer(modifier = Modifier.height(60.dp))

                // Time labels
                TimeAxisColumn(
                    startHour = startHour,
                    endHour = endHour,
                    showFullLabels = false,
                    modifier = Modifier.weight(1f)
                )
            }

            // Day columns
            days.forEach { date ->
                val isToday = date == today
                val events = eventsMap[date] ?: emptyList()
                val allDayEvents = events.filter { it.isAllDay }
                val timedEvents = events.filter { !it.isAllDay }

                val borderColor = if (isToday) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
                val borderWidth = if (isToday) dims.cellBorderWidthToday else dims.cellBorderWidth

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .border(
                            width = borderWidth,
                            color = borderColor,
                            shape = MaterialTheme.shapes.small
                        ),
                    color = if (isToday && !isEInk) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    } else {
                        Color.Transparent
                    },
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Compact day header
                        TimelineDayHeader(
                            date = date,
                            isToday = isToday,
                            weather = weatherByDate[date]
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // All-day events
                        if (allDayEvents.isNotEmpty()) {
                            AllDayEventsRow(
                                events = allDayEvents,
                                onEventClick = onEventClick
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.5.dp
                            )
                        }

                        // Time-positioned events area
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            TimePositionedEvents(
                                events = timedEvents,
                                startHour = startHour,
                                endHour = endHour,
                                onEventClick = onEventClick
                            )

                            // Stylus writing overlay
                            if (recognizer != null && parser != null && onInlineEventCreated != null) {
                                InlineDayWritingArea(
                                    date = date,
                                    recognizer = recognizer,
                                    parser = parser,
                                    modifier = Modifier.fillMaxSize(),
                                    isCompact = true,
                                    onEventCreated = onInlineEventCreated,
                                    onHandwritingUsed = onHandwritingUsed,
                                    stylusOnly = true,
                                    onFingerTap = null
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom bar: Tasks + Quick Add
        if (showTasks || showQuickAdd) {
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .weight(0.2f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Tasks section
                if (showTasks) {
                    Surface(
                        modifier = Modifier
                            .weight(if (showQuickAdd) 0.6f else 1f)
                            .fillMaxHeight()
                            .border(
                                width = dims.cellBorderWidth,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = MaterialTheme.shapes.small
                            ),
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small
                    ) {
                        TaskList(
                            tasks = tasks,
                            title = "Tasks",
                            isCompact = false,
                            columns = 4,
                            showCompleted = false,
                            showAddButton = true,
                            showWriteHint = showWriteHints,
                            recognizer = recognizer,
                            onTaskTextRecognized = onTaskTextRecognized,
                            onHandwritingUsed = onHandwritingUsed,
                            onTaskToggle = onTaskToggle,
                            onTaskClick = onTaskClick,
                            onAddTask = onAddTaskClick
                        )
                    }
                }

                // Quick Add section
                if (showQuickAdd) {
                    Surface(
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxHeight()
                            .border(
                                width = dims.cellBorderWidth,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = MaterialTheme.shapes.small
                            ),
                        color = if (isEInk) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        QuickAddArea(
                            modifier = Modifier.fillMaxSize(),
                            placeholder = "Write to add...",
                            onInput = onQuickAddInput
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact day header for timeline columns showing day name, date, and weather.
 */
@Composable
private fun TimelineDayHeader(
    date: LocalDate,
    isToday: Boolean,
    weather: DailyWeather? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            style = MaterialTheme.typography.titleMedium,
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

        if (weather != null) {
            Text(
                text = "${weather.maxTemp}\u00B0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
