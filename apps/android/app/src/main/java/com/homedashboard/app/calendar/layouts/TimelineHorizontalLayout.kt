package com.homedashboard.app.calendar.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.homedashboard.app.calendar.CalendarEventUi
import com.homedashboard.app.calendar.components.*
import com.homedashboard.app.handwriting.HandwritingRecognizer
import com.homedashboard.app.handwriting.NaturalLanguageParser
import com.homedashboard.app.handwriting.ParsedEvent
import java.time.LocalDate

/**
 * Option C: Horizontal Timeline Layout
 *
 * Layout structure:
 * ┌────────┬────────┬────────┬────────┬────────┬────────┬────────┐
 * │ Thu 29 │ Fri 30 │ Sat 31 │ Sun 1  │ Mon 2  │ Tue 3  │ Wed 4  │
 * │ TODAY  │        │        │        │        │        │        │
 * ├────────┼────────┼────────┼────────┼────────┼────────┼────────┤
 * │▪Event  │▪Event  │        │        │▪Event  │▪Event  │        │
 * │▪Event  │        │        │        │        │        │        │
 * │        │        │        │        │        │        │        │
 * ├────────┴────────┴────────┴────────┴────────┴────────┴────────┤
 * │  📋 TASKS                     │  ✏️ QUICK ADD                 │
 * └───────────────────────────────┴───────────────────────────────┘
 */
@Composable
fun TimelineHorizontalLayout(
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
    onTaskToggle: (TaskUi) -> Unit = {},
    onTaskClick: (TaskUi) -> Unit = {},
    onQuickAddInput: (String) -> Unit = {}
) {
    val today = LocalDate.now()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp)
    ) {
        // Main calendar area - 7 days in a row
        Row(
            modifier = Modifier
                .weight(if (showTasks || showQuickAdd) 0.7f else 1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            days.forEach { date ->
                val isToday = date == today
                val events = eventsMap[date] ?: emptyList()

                // Individual day column
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .border(
                            width = if (isToday) 2.dp else 1.dp,
                            color = if (isToday) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = MaterialTheme.shapes.small
                        ),
                    color = if (isToday) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    } else {
                        Color.Transparent
                    },
                    shape = MaterialTheme.shapes.small
                ) {
                    TimelineDayCell(
                        date = date,
                        events = events,
                        isToday = isToday,
                        showAddButton = true,
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
        }

        // Bottom bar: Tasks + Quick Add
        if (showTasks || showQuickAdd) {
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .weight(0.3f)
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
