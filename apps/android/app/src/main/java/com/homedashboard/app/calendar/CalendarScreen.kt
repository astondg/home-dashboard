package com.homedashboard.app.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.homedashboard.app.calendar.components.TaskUi
import com.homedashboard.app.calendar.layouts.*
import com.homedashboard.app.data.weather.RainForecast
import com.homedashboard.app.handwriting.HandwritingRecognizer
import com.homedashboard.app.handwriting.NaturalLanguageParser
import com.homedashboard.app.handwriting.ParsedEvent
import com.homedashboard.app.BuildConfig
import com.homedashboard.app.settings.CalendarLayoutType
import com.homedashboard.app.settings.CalendarSettings
import com.homedashboard.app.ui.theme.LocalDimensions
import com.homedashboard.app.ui.theme.LocalIsEInk
import java.time.LocalDate
import java.time.LocalTime
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
    weatherByDate: Map<LocalDate, com.homedashboard.app.data.weather.DailyWeather> = emptyMap(),
    rainForecast: RainForecast? = null,
    // Inline handwriting support
    recognizer: HandwritingRecognizer? = null,
    parser: NaturalLanguageParser? = null,
    onInlineEventCreated: ((ParsedEvent) -> Unit)? = null,
    onHandwritingUsed: (() -> Unit)? = null,
    // Callbacks
    onSettingsClick: () -> Unit = {},
    onLayoutChange: (CalendarLayoutType) -> Unit = {},
    onDayClick: (LocalDate) -> Unit = {},
    onAddEventClick: (LocalDate) -> Unit = {},  // + button on day cells
    onWriteClick: (LocalDate) -> Unit = {},  // Tap to write in day cells (fallback)
    onAddTaskClick: () -> Unit = {},  // + button on task list
    onTaskTextRecognized: ((String) -> Unit)? = null,  // Inline handwriting in task list
    onHandwritingInput: (LocalDate, String) -> Unit = { _, _ -> },
    onEventClick: (CalendarEventUi) -> Unit = {},
    onTaskToggle: (TaskUi) -> Unit = {},
    onTaskClick: (TaskUi) -> Unit = {},
    onQuickAddInput: (String) -> Unit = {},
    onResetData: (() -> Unit)? = null
) {
    var startDate by remember { mutableStateOf(LocalDate.now()) }

    // Auto-advance the rolling window when the date changes (e.g. overnight)
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L) // Check every minute
            val now = LocalDate.now()
            // If startDate is in the past and the user hasn't manually navigated away,
            // advance to keep today as the first day
            if (startDate.isBefore(now) && startDate.isAfter(now.minusDays(8))) {
                startDate = now
            }
        }
    }

    // Generate 7 days starting from startDate
    val days = remember(startDate) {
        (0 until 7).map { startDate.plusDays(it.toLong()) }
    }

    // Hide write hints after user has written their first event
    val showWriteHints = !settings.hasUsedHandwriting

    // Boox pen setup is now at the Activity level (MainActivity).
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
            // Header with integrated info (rain indicator + next event)
            CalendarHeader(
                startDate = startDate,
                endDate = startDate.plusDays(6),
                currentLayout = settings.layoutType,
                onPreviousWeek = { startDate = startDate.minusDays(7) },
                onNextWeek = { startDate = startDate.plusDays(7) },
                onTodayClick = { startDate = LocalDate.now() },
                onSettingsClick = onSettingsClick,
                onLayoutChange = onLayoutChange,
                onResetData = onResetData,
                eventsMap = eventsMap,
                use24HourFormat = settings.use24HourFormat,
                rainForecast = if (settings.showWeather) rainForecast else null,
                showWeather = settings.showWeather
            )

            // Calendar content with optional Boox overlay
            Box(modifier = Modifier.fillMaxSize()) {
                // Calendar content - switches based on layout type
                when (settings.layoutType) {
                    CalendarLayoutType.GRID_3X3 -> {
                        Grid3x3Layout(
                            days = days,
                            eventsMap = eventsMap,
                            tasks = tasks,
                            modifier = Modifier.fillMaxSize(),
                            weatherByDate = if (settings.showWeather) weatherByDate else emptyMap(),
                            showWriteHints = showWriteHints,
                            recognizer = recognizer,
                            parser = parser,
                            onInlineEventCreated = onInlineEventCreated,
                            onTaskTextRecognized = onTaskTextRecognized,
                            onHandwritingUsed = onHandwritingUsed,
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
                            weatherByDate = if (settings.showWeather) weatherByDate else emptyMap(),
                            showWriteHints = showWriteHints,
                            showTasks = settings.showTasks,
                            showQuickAdd = settings.showQuickAdd,
                            recognizer = recognizer,
                            parser = parser,
                            onInlineEventCreated = onInlineEventCreated,
                            onTaskTextRecognized = onTaskTextRecognized,
                            onHandwritingUsed = onHandwritingUsed,
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
                            onTaskTextRecognized = onTaskTextRecognized,
                            onHandwritingUsed = onHandwritingUsed,
                            showWriteHints = showWriteHints,
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

                // Boox pen overlay is now managed at the Activity level (MainActivity)
                // for proper SDK initialization timing.
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
    onLayoutChange: (CalendarLayoutType) -> Unit,
    onResetData: (() -> Unit)? = null,
    eventsMap: Map<LocalDate, List<CalendarEventUi>> = emptyMap(),
    use24HourFormat: Boolean = false,
    rainForecast: RainForecast? = null,
    showWeather: Boolean = false
) {
    val dims = LocalDimensions.current
    var showLayoutMenu by remember { mutableStateOf(false) }
    var showResetConfirmation by remember { mutableStateOf(false) }

    val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    val displayText = if (startDate.month == endDate.month) {
        startDate.format(monthYearFormatter)
    } else if (startDate.year == endDate.year) {
        "${startDate.format(DateTimeFormatter.ofPattern("MMM"))} - ${endDate.format(monthYearFormatter)}"
    } else {
        "${startDate.format(DateTimeFormatter.ofPattern("MMM yyyy"))} - ${endDate.format(monthYearFormatter)}"
    }

    val isEInk = LocalIsEInk.current

    // Compute next event label for info section
    val today = LocalDate.now()
    val todayEvents = eventsMap[today] ?: emptyList()

    // Update current time every minute for event comparison
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            delay(60_000L)
        }
    }

    val timeFormatter12 = DateTimeFormatter.ofPattern("h:mm a")

    val nextEvent = todayEvents
        .filter { !it.isAllDay && it.startTime != null }
        .sortedBy { it.startTime }
        .firstOrNull { event ->
            try {
                val eventTime = LocalTime.parse(event.startTime, DateTimeFormatter.ofPattern("h:mm a"))
                eventTime.isAfter(currentTime)
            } catch (_: Exception) {
                try {
                    val eventTime = LocalTime.parse(event.startTime, DateTimeFormatter.ofPattern("H:mm"))
                    eventTime.isAfter(currentTime)
                } catch (_: Exception) {
                    false
                }
            }
        }

    // Rain indicator text
    val rainLabel = if (rainForecast?.nextRainTime != null) {
        val rainTimeStr = if (use24HourFormat) {
            rainForecast.nextRainTime.format(DateTimeFormatter.ofPattern("H:mm"))
        } else {
            rainForecast.nextRainTime.format(DateTimeFormatter.ofPattern("h a")).lowercase()
        }
        "Rain ~$rainTimeStr"
    } else null

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (isEInk) 0.dp else 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Navigation (start)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onPreviousWeek,
                    modifier = Modifier.size(dims.buttonSizeSmall)
                ) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = "Previous week",
                        modifier = Modifier.size(dims.buttonIconSize)
                    )
                }

                Text(
                    text = displayText,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = onNextWeek,
                    modifier = Modifier.size(dims.buttonSizeSmall)
                ) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Next week",
                        modifier = Modifier.size(dims.buttonIconSize)
                    )
                }
            }

            // Info section (center, weighted)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rain indicator
                if (rainLabel != null) {
                    Text(
                        text = "\u2602 $rainLabel",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    if (nextEvent != null) {
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                }

                // Next event
                if (nextEvent != null) {
                    Text(
                        text = "Next: ${nextEvent.title} at ${nextEvent.startTime}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Actions (end)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onTodayClick) {
                    Text(
                        "Today",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                // Layout switcher
                Box {
                    IconButton(
                        onClick = { showLayoutMenu = true },
                        modifier = Modifier.size(dims.buttonSizeSmall)
                    ) {
                        Icon(
                            imageVector = when (currentLayout) {
                                CalendarLayoutType.GRID_3X3 -> Icons.Default.GridView
                                CalendarLayoutType.TIMELINE_HORIZONTAL -> Icons.Default.ViewWeek
                                CalendarLayoutType.DASHBOARD_TODAY_FOCUS -> Icons.Default.ViewDay
                            },
                            contentDescription = "Change layout",
                            modifier = Modifier.size(dims.buttonIconSize)
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

                // Debug-only reset data button
                if (BuildConfig.DEBUG && onResetData != null) {
                    IconButton(
                        onClick = { showResetConfirmation = true },
                        modifier = Modifier.size(dims.buttonSizeSmall)
                    ) {
                        Icon(
                            Icons.Default.DeleteForever,
                            contentDescription = "Reset data (debug)",
                            modifier = Modifier.size(dims.buttonIconSize)
                        )
                    }
                }

                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(dims.buttonSizeSmall)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(dims.buttonIconSize)
                    )
                }
            }
        }
    }

    // Reset confirmation dialog
    if (showResetConfirmation && onResetData != null) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("Reset Local Data") },
            text = { Text("This will delete locally created events and tasks. Synced calendar data will be preserved. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetData()
                        showResetConfirmation = false
                    }
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
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
    val color: Long, // ARGB color value
    val providerType: com.homedashboard.app.data.model.CalendarProvider = com.homedashboard.app.data.model.CalendarProvider.LOCAL,
    val startMinutes: Int? = null, // Minutes from midnight
    val endMinutes: Int? = null // Minutes from midnight
)
