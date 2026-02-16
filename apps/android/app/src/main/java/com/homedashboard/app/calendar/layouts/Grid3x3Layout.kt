package com.homedashboard.app.calendar.layouts

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homedashboard.app.calendar.CalendarEventUi
import com.homedashboard.app.calendar.components.*
import com.homedashboard.app.data.weather.DailyWeather
import com.homedashboard.app.handwriting.HandwritingRecognizer
import com.homedashboard.app.handwriting.NaturalLanguageParser
import com.homedashboard.app.handwriting.ParsedEvent
import com.homedashboard.app.ui.theme.LocalDimensions
import java.time.LocalDate

/**
 * Option B: 3x3 Grid Layout with 2-column wide tasks panel
 *
 * Layout structure:
 * ┌─────────────────┬─────────────────┬─────────────────┐
 * │  29 Thu ★       │  30 Fri         │  31 Sat         │
 * ├─────────────────┼─────────────────┼─────────────────┤
 * │  1 Sun          │  2 Mon          │  3 Tue          │
 * ├─────────────────┼─────────────────────────────────────┤
 * │  4 Wed          │        📋 TASKS (2 columns)        │
 * └─────────────────┴─────────────────────────────────────┘
 */
@Composable
fun Grid3x3Layout(
    days: List<LocalDate>,
    eventsMap: Map<LocalDate, List<CalendarEventUi>>,
    tasks: List<TaskUi>,
    modifier: Modifier = Modifier,
    weatherByDate: Map<LocalDate, DailyWeather> = emptyMap(),
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
    onTaskToggle: (TaskUi) -> Unit = {},
    onTaskClick: (TaskUi) -> Unit = {},
    onQuickAddInput: (String) -> Unit = {}
) {
    val dims = LocalDimensions.current
    val today = LocalDate.now()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Row 1: Days 1-3 (Today, Tomorrow, Day 3)
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            days.take(3).forEach { date ->
                DayCell(
                    date = date,
                    events = eventsMap[date] ?: emptyList(),
                    isToday = date == today,
                    isCompact = false,
                    showDayName = true,
                    headerLayout = DayHeaderLayout.HORIZONTAL,
                    showAddButton = true,
                    weather = weatherByDate[date],
                    modifier = Modifier.weight(1f),
                    recognizer = recognizer,
                    parser = parser,
                    onInlineEventCreated = onInlineEventCreated,
                    onAddClick = { onAddEventClick(date) },
                    onWriteClick = { onWriteClick(date) },
                    onHandwritingInput = { text -> onHandwritingInput(date, text) },
                    onEventClick = onEventClick
                )
            }
        }

        // Row 2: Days 4-6
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            days.drop(3).take(3).forEach { date ->
                DayCell(
                    date = date,
                    events = eventsMap[date] ?: emptyList(),
                    isToday = date == today,
                    isCompact = false,
                    showDayName = true,
                    headerLayout = DayHeaderLayout.HORIZONTAL,
                    showAddButton = true,
                    weather = weatherByDate[date],
                    modifier = Modifier.weight(1f),
                    recognizer = recognizer,
                    parser = parser,
                    onInlineEventCreated = onInlineEventCreated,
                    onAddClick = { onAddEventClick(date) },
                    onWriteClick = { onWriteClick(date) },
                    onHandwritingInput = { text -> onHandwritingInput(date, text) },
                    onEventClick = onEventClick
                )
            }
        }

        // Row 3: Day 7 (1 column) + Tasks (2 columns)
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Day 7
            if (days.size >= 7) {
                DayCell(
                    date = days[6],
                    events = eventsMap[days[6]] ?: emptyList(),
                    isToday = days[6] == today,
                    isCompact = false,
                    showDayName = true,
                    headerLayout = DayHeaderLayout.HORIZONTAL,
                    showAddButton = true,
                    weather = weatherByDate[days[6]],
                    modifier = Modifier.weight(1f),
                    recognizer = recognizer,
                    parser = parser,
                    onInlineEventCreated = onInlineEventCreated,
                    onAddClick = { onAddEventClick(days[6]) },
                    onWriteClick = { onWriteClick(days[6]) },
                    onHandwritingInput = { text -> onHandwritingInput(days[6], text) },
                    onEventClick = onEventClick
                )
            }

            // Tasks - spans 2 columns
            Surface(
                modifier = Modifier
                    .weight(2f)  // 2 columns wide
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
                    showCompleted = true,
                    showAddButton = true,
                    recognizer = recognizer,
                    onTaskTextRecognized = onTaskTextRecognized,
                    onTaskToggle = onTaskToggle,
                    onTaskClick = onTaskClick,
                    onAddTask = onAddTaskClick
                )
            }
        }
    }
}
