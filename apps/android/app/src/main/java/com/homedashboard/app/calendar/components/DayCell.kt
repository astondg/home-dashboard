package com.homedashboard.app.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homedashboard.app.calendar.CalendarEventUi
import com.homedashboard.app.data.model.CalendarProvider
import com.homedashboard.app.data.weather.DailyWeather
import com.homedashboard.app.data.weather.WeatherIcon
import com.homedashboard.app.handwriting.HandwritingRecognizer
import com.homedashboard.app.handwriting.InlineDayWritingArea
import com.homedashboard.app.handwriting.NaturalLanguageParser
import com.homedashboard.app.handwriting.ParsedEvent
import com.homedashboard.app.ui.theme.LocalDimensions
import com.homedashboard.app.ui.theme.LocalIsEInk
import com.homedashboard.app.ui.theme.LocalIsWallCalendar
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
    maxVisibleEvents: Int = 4,
    weather: DailyWeather? = null,
    // Inline handwriting (direct writing in cell)
    recognizer: HandwritingRecognizer? = null,
    parser: NaturalLanguageParser? = null,
    onInlineEventCreated: ((ParsedEvent) -> Unit)? = null,
    onHandwritingUsed: (() -> Unit)? = null,
    // Legacy callbacks
    onAddClick: (() -> Unit)? = null,
    onWriteClick: (() -> Unit)? = null,
    onHandwritingInput: ((String) -> Unit)? = null,
    onEventClick: ((CalendarEventUi) -> Unit)? = null,
    onCellClick: (() -> Unit)? = null
) {
    val dims = LocalDimensions.current
    val isEInk = LocalIsEInk.current

    // Show provider badge only when events come from multiple sources
    val showProviderBadge = remember(events) {
        events.map { it.providerType }.distinct().size > 1
    }

    val backgroundColor = when {
        isToday && isEInk -> Color.Transparent
        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    val borderColor = when {
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    val borderWidth = if (isToday) dims.cellBorderWidthToday else dims.cellBorderWidth

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
            onAddClick = onAddClick,
            weather = weather
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Events list with stylus writing overlay
        var showFullDayDialog by remember { mutableStateOf(false) }
        val hasOverflow = events.size > maxVisibleEvents
        val visibleEvents = if (hasOverflow) events.take(maxVisibleEvents - 1) else events
        val overflowCount = if (hasOverflow) events.size - visibleEvents.size else 0

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Layer 1: Events list (always shown, receives finger taps)
            if (events.isEmpty()) {
                if (showWriteHint) {
                    WriteHint(
                        modifier = Modifier.fillMaxSize(),
                        isCompact = isCompact,
                        onClick = if (recognizer == null) onWriteClick else null
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(if (isCompact) 2.dp else 4.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isCompact) 2.dp else 4.dp)
                ) {
                    items(visibleEvents) { event ->
                        EventChip(
                            event = event,
                            isCompact = isCompact,
                            showProviderBadge = showProviderBadge,
                            onClick = { onEventClick?.invoke(event) }
                        )
                    }
                    if (hasOverflow) {
                        item {
                            Text(
                                text = "+$overflowCount more",
                                style = if (isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showFullDayDialog = true }
                                    .padding(
                                        horizontal = if (isCompact) 8.dp else 12.dp,
                                        vertical = if (isCompact) 2.dp else 4.dp
                                    )
                            )
                        }
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
                    isCompact = isCompact,
                    onEventCreated = onInlineEventCreated,
                    onHandwritingUsed = onHandwritingUsed,
                    stylusOnly = true,
                    onFingerTap = null
                )
            }
        }

        // Full day events dialog
        if (showFullDayDialog) {
            FullDayEventsDialog(
                date = date,
                events = events,
                onDismiss = { showFullDayDialog = false },
                onEventClick = { event ->
                    showFullDayDialog = false
                    onEventClick?.invoke(event)
                }
            )
        }
    }
}

/**
 * Dialog showing all events for a day (used when there's overflow)
 */
@Composable
private fun FullDayEventsDialog(
    date: LocalDate,
    events: List<CalendarEventUi>,
    onDismiss: () -> Unit,
    onEventClick: (CalendarEventUi) -> Unit
) {
    val isEInk = LocalIsEInk.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}, ${date.dayOfMonth}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(events) { event ->
                    EventChip(
                        event = event,
                        isCompact = false,
                        onClick = { onEventClick(event) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun DayCellHeader(
    date: LocalDate,
    isToday: Boolean,
    isCompact: Boolean,
    showDayName: Boolean,
    layout: DayHeaderLayout,
    showAddButton: Boolean,
    onAddClick: (() -> Unit)?,
    weather: DailyWeather? = null
) {
    val dims = LocalDimensions.current
    val isEInk = LocalIsEInk.current

    val headerBg = Color.Transparent

    val primaryColor = when {
        isToday -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface
    }

    val secondaryColor = when {
        isToday -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val addButtonTint = MaterialTheme.colorScheme.onSurfaceVariant

    // Today gets a thick left border accent drawn via drawWithContent
    val todayAccentModifier = if (isToday) {
        Modifier.drawWithContent {
            drawContent()
            drawRect(
                color = Color.Black,
                topLeft = androidx.compose.ui.geometry.Offset.Zero,
                size = androidx.compose.ui.geometry.Size(
                    width = 4.dp.toPx(),
                    height = size.height
                )
            )
        }
    } else {
        Modifier
    }

    when (layout) {
        DayHeaderLayout.VERTICAL -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(todayAccentModifier)
                    .background(headerBg)
                    .padding(
                        horizontal = if (isCompact) 4.dp else dims.cellHeaderPaddingHorizontal,
                        vertical = if (isCompact) 4.dp else dims.cellHeaderPaddingVertical
                    ),
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
                            style = if (isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleMedium,
                            color = secondaryColor
                        )
                    }
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = if (isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineLarge,
                        fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Normal,
                        color = primaryColor
                    )
                }

                if (showAddButton && onAddClick != null) {
                    IconButton(
                        onClick = onAddClick,
                        modifier = Modifier.size(if (isCompact) 24.dp else dims.buttonSizeSmall)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add event",
                            tint = addButtonTint,
                            modifier = Modifier.size(if (isCompact) 16.dp else dims.buttonIconSize)
                        )
                    }
                }
            }
        }

        DayHeaderLayout.HORIZONTAL -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(todayAccentModifier)
                    .background(headerBg)
                    .padding(
                        horizontal = if (isCompact) 6.dp else dims.cellHeaderPaddingHorizontal,
                        vertical = if (isCompact) 4.dp else dims.cellHeaderPaddingVertical
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Date and day name (left)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Date number (prominent)
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = if (isCompact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineLarge,
                        fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = primaryColor
                    )

                    // Day name (secondary)
                    if (showDayName) {
                        Text(
                            text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            style = if (isCompact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium,
                            color = secondaryColor
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Weather (right, before + button)
                if (weather != null) {
                    Icon(
                        imageVector = weatherIconForDayCell(weather.icon),
                        contentDescription = weather.icon.name,
                        modifier = Modifier.size(if (isCompact) 22.dp else 28.dp),
                        tint = secondaryColor
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${weather.maxTemp}\u00B0",
                        style = if (isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                        color = secondaryColor
                    )
                    Spacer(modifier = Modifier.width(if (isCompact) 4.dp else 8.dp))
                }

                // Add button (right)
                if (showAddButton && onAddClick != null) {
                    IconButton(
                        onClick = onAddClick,
                        modifier = Modifier.size(if (isCompact) 28.dp else dims.buttonSizeSmall)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add event",
                            tint = addButtonTint,
                            modifier = Modifier.size(if (isCompact) 18.dp else dims.buttonIconSize)
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
    val isEInk = LocalIsEInk.current
    val isWallCalendar = LocalIsWallCalendar.current
    val hintColor = if (isEInk) {
        Color(0xFFA0A0A0)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    }

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
                tint = hintColor,
                modifier = Modifier.size(if (isCompact) 24.dp else if (isWallCalendar) 36.dp else 28.dp)
            )
            Spacer(modifier = Modifier.height(if (isCompact) 2.dp else 4.dp))
            Text(
                text = "write events here",
                style = if (isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
                color = hintColor
            )
        }
    }
}

/**
 * Event chip for displaying in day cells.
 * Horizontal layout: title on the left, time on the right, full width.
 * On e-ink: left accent border + bottom divider, no background shading.
 * On standard: tinted background with color accent.
 */
@Composable
fun EventChip(
    event: CalendarEventUi,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    showProviderBadge: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val dims = LocalDimensions.current
    val isEInk = LocalIsEInk.current
    val isWallCalendar = LocalIsWallCalendar.current
    val chipColor = Color(event.color)

    // E-ink: transparent background (divider separates); standard: tinted background
    val backgroundColor = if (isEInk) Color.Transparent else chipColor.copy(alpha = 0.15f)
    val accentColor = if (isEInk) Color.Black else chipColor

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                .background(
                    color = backgroundColor,
                    shape = if (isEInk) androidx.compose.ui.graphics.RectangleShape else MaterialTheme.shapes.extraSmall
                )
                .then(
                    Modifier.drawWithContent {
                        drawContent()
                        // Left accent bar
                        drawRect(
                            color = accentColor,
                            topLeft = androidx.compose.ui.geometry.Offset.Zero,
                            size = androidx.compose.ui.geometry.Size(
                                width = if (isWallCalendar) 4.dp.toPx() else 3.dp.toPx(),
                                height = size.height
                            )
                        )
                    }
                )
                .padding(
                    start = if (isCompact) 8.dp else (dims.chipPaddingHorizontal + 4.dp),
                    end = if (isCompact) 4.dp else dims.chipPaddingHorizontal,
                    top = if (isCompact) 3.dp else dims.chipPaddingVertical,
                    bottom = if (isCompact) 3.dp else dims.chipPaddingVertical
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Provider badge (only when multiple sources)
            if (showProviderBadge && event.providerType != CalendarProvider.LOCAL) {
                val badgeText = when (event.providerType) {
                    CalendarProvider.GOOGLE -> "G"
                    CalendarProvider.ICLOUD -> "iC"
                    CalendarProvider.MICROSOFT -> "O"
                    CalendarProvider.CALDAV -> "C"
                    else -> ""
                }
                if (badgeText.isNotEmpty()) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }

            // Title (left, takes remaining space)
            Text(
                text = event.title,
                style = if (isCompact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.headlineSmall,
                maxLines = if (isCompact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            // Time (right)
            if (!event.isAllDay && event.startTime != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = event.startTime,
                    style = if (isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        // Bottom divider on e-ink to separate events (replaces background shading)
        if (isEInk) {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
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
    showWriteHint: Boolean = true,
    maxVisibleEvents: Int = 3,
    weather: DailyWeather? = null,
    // Inline handwriting (direct writing in cell)
    recognizer: HandwritingRecognizer? = null,
    parser: NaturalLanguageParser? = null,
    onInlineEventCreated: ((ParsedEvent) -> Unit)? = null,
    onHandwritingUsed: (() -> Unit)? = null,
    // Legacy callbacks
    onAddClick: (() -> Unit)? = null,
    onWriteClick: (() -> Unit)? = null,
    onHandwritingInput: ((String) -> Unit)? = null,
    onEventClick: ((CalendarEventUi) -> Unit)? = null
) {
    val dims = LocalDimensions.current
    val isEInk = LocalIsEInk.current

    // Show provider badge only when events come from multiple sources
    val showProviderBadge = remember(events) {
        events.map { it.providerType }.distinct().size > 1
    }

    val backgroundColor = when {
        isToday && isEInk -> Color.Transparent
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

                // Weather in timeline header
                if (weather != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = weatherIconForDayCell(weather.icon),
                            contentDescription = weather.icon.name,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${weather.maxTemp}\u00B0",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (showAddButton && onAddClick != null) {
                IconButton(
                    onClick = onAddClick,
                    modifier = Modifier.size(dims.buttonSizeSmall)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add event",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(dims.buttonIconSize)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Events with stylus writing overlay
        var showFullDayDialog by remember { mutableStateOf(false) }
        val hasOverflow = events.size > maxVisibleEvents
        val visibleEvents = if (hasOverflow) events.take(maxVisibleEvents - 1) else events
        val overflowCount = if (hasOverflow) events.size - visibleEvents.size else 0

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Layer 1: Events (receives finger taps)
            if (events.isEmpty()) {
                if (showWriteHint) {
                    WriteHint(
                        modifier = Modifier.fillMaxSize(),
                        isCompact = true,
                        onClick = if (recognizer == null) onWriteClick else null
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(visibleEvents) { event ->
                        EventChip(
                            event = event,
                            isCompact = true,
                            showProviderBadge = showProviderBadge,
                            onClick = { onEventClick?.invoke(event) }
                        )
                    }
                    if (hasOverflow) {
                        item {
                            Text(
                                text = "+$overflowCount more",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showFullDayDialog = true }
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
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
                    onHandwritingUsed = onHandwritingUsed,
                    stylusOnly = true,
                    onFingerTap = null
                )
            }
        }

        // Full day events dialog (TimelineDayCell)
        if (showFullDayDialog) {
            FullDayEventsDialog(
                date = date,
                events = events,
                onDismiss = { showFullDayDialog = false },
                onEventClick = { event ->
                    showFullDayDialog = false
                    onEventClick?.invoke(event)
                }
            )
        }
    }
}

/**
 * Map WeatherIcon to Material Icon vector for day cell headers.
 */
private fun weatherIconForDayCell(icon: WeatherIcon): androidx.compose.ui.graphics.vector.ImageVector {
    return when (icon) {
        WeatherIcon.SUNNY -> Icons.Default.WbSunny
        WeatherIcon.PARTLY_CLOUDY -> Icons.Default.WbCloudy
        WeatherIcon.CLOUDY -> Icons.Default.Cloud
        WeatherIcon.FOGGY -> Icons.Default.Cloud
        WeatherIcon.DRIZZLE -> Icons.Default.Grain
        WeatherIcon.RAIN -> Icons.Default.Grain
        WeatherIcon.SNOW -> Icons.Default.AcUnit
        WeatherIcon.THUNDERSTORM -> Icons.Default.Thunderstorm
    }
}
