package com.homedashboard.app.calendar.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homedashboard.app.calendar.CalendarEventUi
import com.homedashboard.app.calendar.components.*
import com.homedashboard.app.handwriting.HandwritingRecognizer
import com.homedashboard.app.handwriting.NaturalLanguageParser
import com.homedashboard.app.handwriting.ParsedEvent
import com.homedashboard.app.ui.theme.LocalDimensions
import com.homedashboard.app.ui.theme.LocalIsEInk
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Option D: Today Focus Dashboard Layout
 *
 * Layout structure:
 * ┌──────────────────────────────┬────────────────────────────┐
 * │        ★ TODAY               │     THIS WEEK              │
 * │        Thursday, Jan 29      │  ┌──┬──┬──┬──┬──┬──┬──┐   │
 * │                              │  │29│30│31│ 1│ 2│ 3│ 4│   │
 * │   9:00  Team Meeting         │  │▪▪│▪ │  │  │▪ │▪ │  │   │
 * │   ─────────────────          │  └──┴──┴──┴──┴──┴──┴──┘   │
 * │  11:00  Focus time           │                            │
 * │   ─────────────────          │   TOMORROW: Fri 30         │
 * │   2:00  Client Call          │   ▪ Dentist 10am           │
 * │                              │                            │
 * │                              │   UPCOMING                 │
 * │                              │   ▪ Mon 2: Project due     │
 * ├──────────────────────────────┼────────────────────────────┤
 * │   📋 TASKS                   │   ✏️ QUICK ADD             │
 * └──────────────────────────────┴────────────────────────────┘
 */
@Composable
fun DashboardTodayFocusLayout(
    days: List<LocalDate>,
    eventsMap: Map<LocalDate, List<CalendarEventUi>>,
    tasks: List<TaskUi>,
    modifier: Modifier = Modifier,
    showTasks: Boolean = true,
    showQuickAdd: Boolean = true,
    // Inline handwriting support
    recognizer: HandwritingRecognizer? = null,
    parser: NaturalLanguageParser? = null,
    onInlineEventCreated: ((ParsedEvent) -> Unit)? = null,
    onTaskTextRecognized: ((String) -> Unit)? = null,
    // Callbacks
    onAddEventClick: (LocalDate) -> Unit = {},
    onWriteClick: (LocalDate) -> Unit = {},
    onAddTaskClick: () -> Unit = {},
    onHandwritingInput: (LocalDate, String) -> Unit = { _, _ -> },
    onEventClick: (CalendarEventUi) -> Unit = {},
    onDayClick: (LocalDate) -> Unit = {},
    onTaskToggle: (TaskUi) -> Unit = {},
    onTaskClick: (TaskUi) -> Unit = {},
    onQuickAddInput: (String) -> Unit = {}
) {
    val dims = LocalDimensions.current
    val isEInk = LocalIsEInk.current
    val today = LocalDate.now()
    val todayEvents = eventsMap[today] ?: emptyList()
    val tomorrow = today.plusDays(1)
    val tomorrowEvents = eventsMap[tomorrow] ?: emptyList()

    // Get upcoming events (excluding today and tomorrow)
    val upcomingEvents = days.drop(2).flatMap { date ->
        (eventsMap[date] ?: emptyList()).map { event -> date to event }
    }.take(5) // Limit to 5 upcoming events

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp)
    ) {
        // Main content area
        Row(
            modifier = Modifier
                .weight(if (showTasks || showQuickAdd) 0.7f else 1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Left panel: Today's schedule
            Surface(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .border(
                        width = dims.cellBorderWidthToday,
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.medium
                    ),
                color = if (isEInk) Color.Transparent else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.medium
            ) {
                TodayPanel(
                    date = today,
                    events = todayEvents,
                    onEventClick = onEventClick,
                    onHandwritingInput = { text -> onHandwritingInput(today, text) }
                )
            }

            // Right panel: Week overview + Tomorrow + Upcoming
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Mini week view
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = dims.cellBorderWidth,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = MaterialTheme.shapes.small
                        ),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small
                ) {
                    MiniWeekView(
                        days = days,
                        eventsMap = eventsMap,
                        today = today,
                        onDayClick = onDayClick
                    )
                }

                // Tomorrow preview
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.4f)
                        .border(
                            width = dims.cellBorderWidth,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = MaterialTheme.shapes.small
                        ),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small
                ) {
                    TomorrowPreview(
                        date = tomorrow,
                        events = tomorrowEvents,
                        onEventClick = onEventClick,
                        onClick = { onDayClick(tomorrow) }
                    )
                }

                // Upcoming events
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.6f)
                        .border(
                            width = dims.cellBorderWidth,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = MaterialTheme.shapes.small
                        ),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small
                ) {
                    UpcomingEventsPanel(
                        events = upcomingEvents,
                        onEventClick = onEventClick,
                        onDayClick = onDayClick
                    )
                }
            }
        }

        // Bottom bar: Tasks + Quick Add
        if (showTasks || showQuickAdd) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .weight(0.3f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tasks section
                if (showTasks) {
                    Surface(
                        modifier = Modifier
                            .weight(if (showQuickAdd) 0.6f else 1f)
                            .fillMaxHeight()
                            .border(
                                width = 1.dp,
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
                            showCompleted = false,
                            showAddButton = true,
                            recognizer = recognizer,
                            onTaskTextRecognized = onTaskTextRecognized,
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
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = MaterialTheme.shapes.small
                            ),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
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

@Composable
private fun TodayPanel(
    date: LocalDate,
    events: List<CalendarEventUi>,
    onEventClick: (CalendarEventUi) -> Unit,
    onHandwritingInput: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Header
        Text(
            text = "TODAY",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        HorizontalDivider()

        Spacer(modifier = Modifier.height(8.dp))

        // Events list
        if (events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                QuickAddArea(
                    modifier = Modifier.fillMaxSize(),
                    placeholder = "No events today\nWrite to add...",
                    onInput = onHandwritingInput
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(events.sortedBy { it.startTime }) { event ->
                    TodayEventItem(
                        event = event,
                        onClick = { onEventClick(event) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayEventItem(
    event: CalendarEventUi,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Time
        Text(
            text = event.startTime ?: "All day",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )

        // Event details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Color indicator
        Box(
            modifier = Modifier
                .size(8.dp, 24.dp)
                .background(
                    color = Color(event.color),
                    shape = MaterialTheme.shapes.extraSmall
                )
        )
    }
}

@Composable
private fun MiniWeekView(
    days: List<LocalDate>,
    eventsMap: Map<LocalDate, List<CalendarEventUi>>,
    today: LocalDate,
    onDayClick: (LocalDate) -> Unit
) {
    Column(
        modifier = Modifier.padding(8.dp)
    ) {
        Text(
            text = "THIS WEEK",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            days.forEach { date ->
                val isToday = date == today
                val hasEvents = (eventsMap[date] ?: emptyList()).isNotEmpty()

                Column(
                    modifier = Modifier
                        .clickable { onDayClick(date) }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (isToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        }
                    ) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Event indicator dots
                    if (hasEvents) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = MaterialTheme.shapes.small
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TomorrowPreview(
    date: LocalDate,
    events: List<CalendarEventUi>,
    onEventClick: (CalendarEventUi) -> Unit,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Text(
            text = "TOMORROW",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = date.format(DateTimeFormatter.ofPattern("EEE, MMM d")),
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(4.dp))

        if (events.isEmpty()) {
            Text(
                text = "No events",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            events.take(3).forEach { event ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = Color(event.color),
                                shape = MaterialTheme.shapes.small
                            )
                    )

                    Text(
                        text = "${event.startTime ?: ""} ${event.title}".trim(),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (events.size > 3) {
                Text(
                    text = "+${events.size - 3} more",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun UpcomingEventsPanel(
    events: List<Pair<LocalDate, CalendarEventUi>>,
    onEventClick: (CalendarEventUi) -> Unit,
    onDayClick: (LocalDate) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        Text(
            text = "UPCOMING",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        if (events.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No upcoming events",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(events) { (date, event) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEventClick(event) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Date badge
                        Text(
                            text = date.format(DateTimeFormatter.ofPattern("EEE d")),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(52.dp)
                        )

                        // Color indicator
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    color = Color(event.color),
                                    shape = MaterialTheme.shapes.small
                                )
                        )

                        // Event title
                        Text(
                            text = event.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
