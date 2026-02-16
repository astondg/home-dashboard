package com.homedashboard.app.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
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
import com.homedashboard.app.handwriting.HandwritingRecognizer
import com.homedashboard.app.handwriting.NaturalLanguageParser
import com.homedashboard.app.handwriting.ParsedEvent
import com.homedashboard.app.BuildConfig
import com.homedashboard.app.settings.CalendarLayoutType
import com.homedashboard.app.settings.CalendarSettings
import com.homedashboard.app.settings.DisplayDetection
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
    onTaskTextRecognized: ((String) -> Unit)? = null,  // Inline handwriting in task list
    onHandwritingInput: (LocalDate, String) -> Unit = { _, _ -> },
    onEventClick: (CalendarEventUi) -> Unit = {},
    onTaskToggle: (TaskUi) -> Unit = {},
    onTaskClick: (TaskUi) -> Unit = {},
    onQuickAddInput: (String) -> Unit = {},
    onResetData: (() -> Unit)? = null
) {
    var startDate by remember { mutableStateOf(LocalDate.now()) }

    // Generate 7 days starting from startDate
    val days = remember(startDate) {
        (0 until 7).map { startDate.plusDays(it.toLong()) }
    }

    // Boox pen setup is now at the Activity level (MainActivity).
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
                onLayoutChange = onLayoutChange,
                onResetData = onResetData
            )

            // Glanceable info strip: current time + next event + weather
            GlanceableInfoStrip(
                eventsMap = eventsMap,
                use24HourFormat = settings.use24HourFormat,
                todayWeather = if (settings.showWeather) weatherByDate[LocalDate.now()] else null
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
                            recognizer = recognizer,
                            parser = parser,
                            onInlineEventCreated = onInlineEventCreated,
                            onTaskTextRecognized = onTaskTextRecognized,
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
                            showTasks = settings.showTasks,
                            showQuickAdd = settings.showQuickAdd,
                            recognizer = recognizer,
                            parser = parser,
                            onInlineEventCreated = onInlineEventCreated,
                            onTaskTextRecognized = onTaskTextRecognized,
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
    onResetData: (() -> Unit)? = null
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
            // Navigation
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

            // Actions
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
            title = { Text("Reset All Data") },
            text = { Text("This will delete all events, calendars, and tasks. This cannot be undone.") },
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
 * Info strip showing current time and next upcoming event.
 * Visible across all layout views.
 */
@Composable
private fun GlanceableInfoStrip(
    eventsMap: Map<LocalDate, List<CalendarEventUi>>,
    use24HourFormat: Boolean,
    todayWeather: com.homedashboard.app.data.weather.DailyWeather? = null
) {
    val dims = LocalDimensions.current
    val isEInk = LocalIsEInk.current

    // Update current time every minute
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            delay(60_000L)
        }
    }

    val timeFormatter = if (use24HourFormat) {
        DateTimeFormatter.ofPattern("H:mm")
    } else {
        DateTimeFormatter.ofPattern("h:mm a")
    }

    // Find next upcoming event
    val today = LocalDate.now()
    val todayEvents = eventsMap[today] ?: emptyList()
    val tomorrow = today.plusDays(1)
    val tomorrowEvents = eventsMap[tomorrow] ?: emptyList()

    // Find next event: first non-all-day event after current time today, or first tomorrow
    val nextEvent = todayEvents
        .filter { !it.isAllDay && it.startTime != null }
        .sortedBy { it.startTime }
        .firstOrNull { event ->
            // Parse the time string back for comparison
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

    val nextEventLabel = if (nextEvent != null) {
        "Next: ${nextEvent.title} at ${nextEvent.startTime}"
    } else {
        // Check tomorrow
        val tomorrowFirst = tomorrowEvents
            .filter { !it.isAllDay && it.startTime != null }
            .sortedBy { it.startTime }
            .firstOrNull()
        if (tomorrowFirst != null) {
            "Tomorrow: ${tomorrowFirst.title} at ${tomorrowFirst.startTime}"
        } else {
            "No upcoming events"
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (isEInk) 0.dp else 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Current time
            Text(
                text = currentTime.format(timeFormatter),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // Weather (if available)
            if (todayWeather != null) {
                Row(
                    modifier = Modifier.padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = weatherIconVector(todayWeather.icon),
                        contentDescription = todayWeather.icon.name,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${todayWeather.maxTemp}\u00B0",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Next event
            Text(
                text = nextEventLabel,
                style = MaterialTheme.typography.titleMedium,
                color = if (nextEvent != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(start = 24.dp)
            )
        }

        // Bottom border on e-ink
        if (isEInk) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp
            )
        }
    }
}

/**
 * Map WeatherIcon enum to Material Icon vectors.
 */
private fun weatherIconVector(icon: com.homedashboard.app.data.weather.WeatherIcon): androidx.compose.ui.graphics.vector.ImageVector {
    return when (icon) {
        com.homedashboard.app.data.weather.WeatherIcon.SUNNY -> Icons.Default.WbSunny
        com.homedashboard.app.data.weather.WeatherIcon.PARTLY_CLOUDY -> Icons.Default.WbCloudy
        com.homedashboard.app.data.weather.WeatherIcon.CLOUDY -> Icons.Default.Cloud
        com.homedashboard.app.data.weather.WeatherIcon.FOGGY -> Icons.Default.Cloud
        com.homedashboard.app.data.weather.WeatherIcon.DRIZZLE -> Icons.Default.Grain
        com.homedashboard.app.data.weather.WeatherIcon.RAIN -> Icons.Default.Grain
        com.homedashboard.app.data.weather.WeatherIcon.SNOW -> Icons.Default.AcUnit
        com.homedashboard.app.data.weather.WeatherIcon.THUNDERSTORM -> Icons.Default.Thunderstorm
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
    val providerType: com.homedashboard.app.data.model.CalendarProvider = com.homedashboard.app.data.model.CalendarProvider.LOCAL
)
