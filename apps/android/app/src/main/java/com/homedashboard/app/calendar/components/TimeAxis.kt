package com.homedashboard.app.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.homedashboard.app.calendar.CalendarEventUi
import com.homedashboard.app.ui.theme.LocalIsEInk

/**
 * Vertical column of hour labels with faint horizontal tick marks.
 */
@Composable
fun TimeAxisColumn(
    startHour: Int,
    endHour: Int,
    modifier: Modifier = Modifier,
    showFullLabels: Boolean = false
) {
    val isEInk = LocalIsEInk.current
    val tickColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = if (isEInk) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxHeight()
            .drawBehind {
                val totalHours = endHour - startHour
                if (totalHours <= 0) return@drawBehind
                val hourHeight = size.height / totalHours
                for (i in 1 until totalHours) {
                    val y = i * hourHeight
                    drawLine(
                        color = tickColor,
                        start = Offset(size.width * 0.6f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 0.5.dp.toPx()
                    )
                }
            }
    ) {
        val totalHours = endHour - startHour
        if (totalHours <= 0) return@Column

        for (hour in startHour until endHour) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopEnd
            ) {
                val label = if (showFullLabels) {
                    when {
                        hour == 0 -> "12 AM"
                        hour < 12 -> "$hour AM"
                        hour == 12 -> "12 PM"
                        else -> "${hour - 12} PM"
                    }
                } else {
                    when {
                        hour == 0 -> "12a"
                        hour < 12 -> "${hour}a"
                        hour == 12 -> "12p"
                        else -> "${hour - 12}p"
                    }
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor,
                    modifier = Modifier.padding(end = 4.dp, top = 1.dp)
                )
            }
        }
    }
}

/**
 * Custom layout that positions event blocks at Y offsets proportional to startMinutes,
 * with height proportional to duration.
 */
@Composable
fun TimePositionedEvents(
    events: List<CalendarEventUi>,
    startHour: Int,
    endHour: Int,
    modifier: Modifier = Modifier,
    onEventClick: (CalendarEventUi) -> Unit = {}
) {
    val isEInk = LocalIsEInk.current
    val hourLineColor = MaterialTheme.colorScheme.outlineVariant

    val startMinute = startHour * 60
    val endMinute = endHour * 60
    val totalMinutes = endMinute - startMinute

    // Filter to timed events within range
    val timedEvents = events.filter { it.startMinutes != null && !it.isAllDay }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                // Draw faint horizontal lines at each hour
                val totalHours = endHour - startHour
                if (totalHours <= 0) return@drawBehind
                val hourHeight = size.height / totalHours
                for (i in 1 until totalHours) {
                    val y = i * hourHeight
                    drawLine(
                        color = hourLineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 0.5.dp.toPx()
                    )
                }
            }
    ) {
        if (totalMinutes <= 0) return@Box

        Layout(
            content = {
                timedEvents.forEach { event ->
                    TimeEventBlock(
                        event = event,
                        isEInk = isEInk,
                        onClick = { onEventClick(event) }
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val minEventHeight = 24.dp.roundToPx()

            val placeables = measurables.mapIndexed { index, measurable ->
                val event = timedEvents[index]
                val eventStart = (event.startMinutes ?: 0).coerceIn(startMinute, endMinute)
                val eventEnd = (event.endMinutes ?: (eventStart + 30)).coerceIn(eventStart, endMinute)
                val eventHeight = ((eventEnd - eventStart).toFloat() / totalMinutes * height)
                    .toInt()
                    .coerceAtLeast(minEventHeight)

                measurable.measure(
                    Constraints.fixed(
                        width = (width * 0.95f).toInt().coerceAtLeast(0),
                        height = eventHeight
                    )
                )
            }

            layout(width, height) {
                placeables.forEachIndexed { index, placeable ->
                    val event = timedEvents[index]
                    val eventStart = (event.startMinutes ?: 0).coerceIn(startMinute, endMinute)
                    val y = ((eventStart - startMinute).toFloat() / totalMinutes * height).toInt()
                    placeable.placeRelative(x = 0, y = y)
                }
            }
        }
    }
}

/**
 * A single event block for time-positioned display.
 */
@Composable
private fun TimeEventBlock(
    event: CalendarEventUi,
    isEInk: Boolean,
    onClick: () -> Unit
) {
    val chipColor = Color(event.color)
    val backgroundColor = if (isEInk) Color.Transparent else chipColor.copy(alpha = 0.15f)
    val accentColor = if (isEInk) Color.Black else chipColor
    val shape = if (isEInk) RectangleShape else MaterialTheme.shapes.extraSmall
    val borderMod = if (isEInk) {
        Modifier.background(Color.Transparent, shape)
            .drawBehind {
                // 1dp black border
                drawRect(
                    color = Color.Black,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                )
            }
    } else {
        Modifier.background(backgroundColor, shape)
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .then(borderMod)
            .clickable(onClick = onClick)
            .drawBehind {
                // Left accent bar
                drawRect(
                    color = accentColor,
                    topLeft = Offset.Zero,
                    size = Size(
                        width = 4.dp.toPx(),
                        height = size.height
                    )
                )
            }
            .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = event.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (event.startTime != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = event.startTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/**
 * Compact row of all-day event chips shown above the time area.
 */
@Composable
fun AllDayEventsRow(
    events: List<CalendarEventUi>,
    modifier: Modifier = Modifier,
    onEventClick: (CalendarEventUi) -> Unit = {}
) {
    if (events.isEmpty()) return

    val isEInk = LocalIsEInk.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        events.take(3).forEach { event ->
            val chipColor = Color(event.color)
            val bgColor = if (isEInk) Color.Transparent else chipColor.copy(alpha = 0.15f)

            Surface(
                modifier = Modifier.clickable { onEventClick(event) },
                shape = MaterialTheme.shapes.extraSmall,
                color = bgColor,
                border = if (isEInk) {
                    androidx.compose.foundation.BorderStroke(1.dp, Color.Black)
                } else null
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        if (events.size > 3) {
            Text(
                text = "+${events.size - 3}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    }
}

/**
 * Compute the hour range for a set of events across multiple days.
 * Returns a pair of (startHour, endHour) with a minimum 8-hour window,
 * defaulting to 7-21 if no timed events exist.
 */
fun computeHourRange(
    eventsMap: Map<*, List<CalendarEventUi>>,
    defaultStart: Int = 7,
    defaultEnd: Int = 21,
    minWindow: Int = 8
): Pair<Int, Int> {
    val timedEvents = eventsMap.values.flatten().filter { it.startMinutes != null && !it.isAllDay }
    if (timedEvents.isEmpty()) return defaultStart to defaultEnd

    val earliestMinute = timedEvents.minOf { it.startMinutes!! }
    val latestMinute = timedEvents.maxOf { it.endMinutes ?: (it.startMinutes!! + 60) }

    var startHour = (earliestMinute / 60).coerceAtMost(defaultStart)
    var endHour = ((latestMinute + 59) / 60).coerceAtLeast(defaultEnd)

    // Ensure minimum window
    if (endHour - startHour < minWindow) {
        val midpoint = (startHour + endHour) / 2
        startHour = (midpoint - minWindow / 2).coerceAtLeast(0)
        endHour = (startHour + minWindow).coerceAtMost(24)
    }

    return startHour to endHour
}
