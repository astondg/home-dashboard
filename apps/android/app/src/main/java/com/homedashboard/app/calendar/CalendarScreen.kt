package com.homedashboard.app.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.homedashboard.app.calendar.components.TaskUi
import com.homedashboard.app.calendar.layouts.*
import com.homedashboard.app.handwriting.HandwritingRecognizer
import com.homedashboard.app.handwriting.NaturalLanguageParser
import com.homedashboard.app.handwriting.ParsedEvent
import com.homedashboard.app.settings.CalendarLayoutType
import com.homedashboard.app.settings.CalendarSettings
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Main calendar screen showing a 7-day rolling view with multiple layout options.
 * Optimized for e-ink displays with high contrast and minimal animations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    settings: CalendarSettings = CalendarSettings(),
    eventsMap: Map<LocalDate, List<CalendarEventUi>> = emptyMap(),
    tasks: List<TaskUi> = emptyList(),
    // Inline handwriting support
    recognizer: HandwritingRecognizer? = null,
    parser: NaturalLanguageParser? = null,
    onInlineEventCreated: ((ParsedEvent) -> Unit)? = null,
    // Callbacks
    onSettingsClick: () -> Unit = {},
    onLayoutChange: (CalendarLayoutType) -> Unit = {},
    onDayClick: (LocalDate) -> Unit = {},
    onAddEventClick: (LocalDate) -> Unit = {},  // + button on day cells
    onWriteClick: (LocalDate) -> Unit = {},  // Tap to write in day cells (fallback)
    onAddTaskClick: () -> Unit = {},  // + button on task list
    onHandwritingInput: (LocalDate, String) -> Unit = { _, _ -> },
    onEventClick: (CalendarEventUi) -> Unit = {},
    onTaskToggle: (TaskUi) -> Unit = {},
    onTaskClick: (TaskUi) -> Unit = {},
    onQuickAddInput: (String) -> Unit = {}
) {
    var startDate by remember { mutableStateOf(LocalDate.now()) }

    // Generate 7 days starting from startDate
    val days = remember(startDate) {
        (0 until 7).map { startDate.plusDays(it.toLong()) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        CalendarHeader(
            startDate = startDate,
            endDate = startDate.plusDays(6),
            currentLayout = settings.layoutType,
            onPreviousWeek = { startDate = startDate.minusDays(7) },
            onNextWeek = { startDate = startDate.plusDays(7) },
            onTodayClick = { startDate = LocalDate.now() },
            onSettingsClick = onSettingsClick,
            onLayoutChange = onLayoutChange
        )

        // Calendar content - switches based on layout type
        when (settings.layoutType) {
            CalendarLayoutType.GRID_3X3 -> {
                Grid3x3Layout(
                    days = days,
                    eventsMap = eventsMap,
                    tasks = tasks,
                    modifier = Modifier.fillMaxSize(),
                    recognizer = recognizer,
                    parser = parser,
                    onInlineEventCreated = onInlineEventCreated,
                    onAddEventClick = onAddEventClick,
                    onWriteClick = onWriteClick,
                    onAddTaskClick = onAddTaskClick,
                    onHandwritingInput = onHandwritingInput,
                    onEventClick = onEventClick,
                    onTaskToggle = onTaskToggle,
                    onTaskClick = onTaskClick,
                    onQuickAddInput = onQuickAddInput
                )
            }

            CalendarLayoutType.TIMELINE_HORIZONTAL -> {
                TimelineHorizontalLayout(
                    days = days,
                    eventsMap = eventsMap,
                    tasks = tasks,
                    modifier = Modifier.fillMaxSize(),
                    showTasks = settings.showTasks,
                    showQuickAdd = settings.showQuickAdd,
                    recognizer = recognizer,
                    parser = parser,
                    onInlineEventCreated = onInlineEventCreated,
                    onAddEventClick = onAddEventClick,
                    onWriteClick = onWriteClick,
                    onAddTaskClick = onAddTaskClick,
                    onHandwritingInput = onHandwritingInput,
                    onEventClick = onEventClick,
                    onTaskToggle = onTaskToggle,
                    onTaskClick = onTaskClick,
                    onQuickAddInput = onQuickAddInput
                )
            }

            CalendarLayoutType.DASHBOARD_TODAY_FOCUS -> {
                DashboardTodayFocusLayout(
                    days = days,
                    eventsMap = eventsMap,
                    tasks = tasks,
                    modifier = Modifier.fillMaxSize(),
                    showTasks = settings.showTasks,
                    showQuickAdd = settings.showQuickAdd,
                    recognizer = recognizer,
                    parser = parser,
                    onInlineEventCreated = onInlineEventCreated,
                    onAddEventClick = onAddEventClick,
                    onWriteClick = onWriteClick,
                    onAddTaskClick = onAddTaskClick,
                    onHandwritingInput = onHandwritingInput,
                    onEventClick = onEventClick,
                    onDayClick = onDayClick,
                    onTaskToggle = onTaskToggle,
                    onTaskClick = onTaskClick,
                    onQuickAddInput = onQuickAddInput
                )
            }
        }
    }
}

@Composable
private fun CalendarHeader(
    startDate: LocalDate,
    endDate: LocalDate,
    currentLayout: CalendarLayoutType,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onTodayClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLayoutChange: (CalendarLayoutType) -> Unit
) {
    var showLayoutMenu by remember { mutableStateOf(false) }

    val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    val displayText = if (startDate.month == endDate.month) {
        startDate.format(monthYearFormatter)
    } else if (startDate.year == endDate.year) {
        "${startDate.format(DateTimeFormatter.ofPattern("MMM"))} - ${endDate.format(monthYearFormatter)}"
    } else {
        "${startDate.format(DateTimeFormatter.ofPattern("MMM yyyy"))} - ${endDate.format(monthYearFormatter)}"
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Navigation
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onPreviousWeek) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = "Previous week"
                    )
                }

                Text(
                    text = displayText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = onNextWeek) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Next week"
                    )
                }
            }

            // Actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onTodayClick) {
                    Text("Today")
                }

                // Layout switcher
                Box {
                    IconButton(onClick = { showLayoutMenu = true }) {
                        Icon(
                            imageVector = when (currentLayout) {
                                CalendarLayoutType.GRID_3X3 -> Icons.Default.GridView
                                CalendarLayoutType.TIMELINE_HORIZONTAL -> Icons.Default.ViewWeek
                                CalendarLayoutType.DASHBOARD_TODAY_FOCUS -> Icons.Default.ViewDay
                            },
                            contentDescription = "Change layout"
                        )
                    }

                    DropdownMenu(
                        expanded = showLayoutMenu,
                        onDismissRequest = { showLayoutMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Grid (3×3)") },
                            onClick = {
                                onLayoutChange(CalendarLayoutType.GRID_3X3)
                                showLayoutMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.GridView, contentDescription = null)
                            },
                            trailingIcon = {
                                if (currentLayout == CalendarLayoutType.GRID_3X3) {
                                    Text("✓", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Timeline") },
                            onClick = {
                                onLayoutChange(CalendarLayoutType.TIMELINE_HORIZONTAL)
                                showLayoutMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.ViewWeek, contentDescription = null)
                            },
                            trailingIcon = {
                                if (currentLayout == CalendarLayoutType.TIMELINE_HORIZONTAL) {
                                    Text("✓", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Today Focus") },
                            onClick = {
                                onLayoutChange(CalendarLayoutType.DASHBOARD_TODAY_FOCUS)
                                showLayoutMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.ViewDay, contentDescription = null)
                            },
                            trailingIcon = {
                                if (currentLayout == CalendarLayoutType.DASHBOARD_TODAY_FOCUS) {
                                    Text("✓", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        )
                    }
                }

                IconButton(onClick = onSettingsClick) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings"
                    )
                }
            }
        }
    }
}

/**
 * UI model for displaying calendar events
 */
data class CalendarEventUi(
    val id: String,
    val title: String,
    val startTime: String?, // Formatted time string
    val isAllDay: Boolean,
    val color: Long // ARGB color value
)
