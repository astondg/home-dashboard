package com.homedashboard.app.handwriting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Inline writing area that can be embedded directly in a day cell.
 *
 * Key feature: Stylus-only input mode (default)
 * - Stylus/pen input is captured for handwriting
 * - Finger touches pass through to content below (events can be tapped)
 *
 * Auto-recognizes text after a pause in writing.
 */
@Composable
fun InlineDayWritingArea(
    date: LocalDate,
    recognizer: HandwritingRecognizer,
    parser: NaturalLanguageParser,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    strokeColor: Color = MaterialTheme.colorScheme.onSurface,
    onEventCreated: (ParsedEvent) -> Unit,
    autoRecognizeDelayMs: Long = 1500L,
    /**
     * When true (default), only stylus input is captured for writing.
     * Finger touches pass through to elements below (like event items).
     * Set to false to accept both stylus and finger input.
     */
    stylusOnly: Boolean = true,
    /**
     * Called when a finger tap occurs and stylusOnly is true.
     * Useful for handling taps on the empty area when no events exist.
     */
    onFingerTap: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()

    // Track all strokes for drawing
    val strokes = remember { mutableStateListOf<StrokeData>() }
    var currentStroke by remember { mutableStateOf<StrokeData?>(null) }

    // ML Kit ink builder
    val inkBuilder = remember { InkBuilder() }

    // Recognition state
    var isRecognizing by remember { mutableStateOf(false) }
    var recognizedText by remember { mutableStateOf<String?>(null) }
    var parsedEvent by remember { mutableStateOf<ParsedEvent?>(null) }
    var showConfirmation by remember { mutableStateOf(false) }

    // Auto-recognize timer
    var autoRecognizeJob by remember { mutableStateOf<Job?>(null) }

    val strokeWidthPx = with(LocalDensity.current) { (if (isCompact) 2.dp else 3.dp).toPx() }

    val hasStrokes = strokes.isNotEmpty() || currentStroke != null

    // Function to trigger recognition
    fun triggerRecognition() {
        if (inkBuilder.hasStrokes() && !isRecognizing) {
            isRecognizing = true
            scope.launch {
                val ink = inkBuilder.build()
                when (val result = recognizer.recognize(ink)) {
                    is RecognitionResult.Success -> {
                        recognizedText = result.bestMatch
                        parsedEvent = parser.parse(result.bestMatch, date)
                        showConfirmation = true
                    }
                    is RecognitionResult.Error -> {
                        // Clear and let user try again
                        strokes.clear()
                        inkBuilder.clear()
                    }
                }
                isRecognizing = false
            }
        }
    }

    // Function to clear everything
    fun clearAll() {
        strokes.clear()
        currentStroke = null
        inkBuilder.clear()
        recognizedText = null
        parsedEvent = null
        showConfirmation = false
        autoRecognizeJob?.cancel()
    }

    // Function to confirm and create event
    fun confirmEvent() {
        parsedEvent?.let { event ->
            onEventCreated(event)
            clearAll()
        }
    }

    Box(modifier = modifier) {
        // Drawing canvas - captures stylus input, passes through finger touches
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(stylusOnly) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue

                            // Check if this is stylus input
                            val isStylus = change.type == PointerType.Stylus

                            // In stylusOnly mode, ignore non-stylus input (let it pass through)
                            if (stylusOnly && !isStylus) {
                                // Don't consume - let finger taps pass through to elements below
                                // But track if it was a quick tap for onFingerTap callback
                                if (!change.pressed && change.previousPressed) {
                                    // Finger was lifted - this might be a tap
                                    // Only trigger if no strokes (user isn't in middle of writing)
                                    if (!hasStrokes && onFingerTap != null) {
                                        onFingerTap()
                                    }
                                }
                                continue // Don't process as handwriting
                            }

                            when {
                                change.pressed && currentStroke == null -> {
                                    // Start new stroke
                                    autoRecognizeJob?.cancel()
                                    showConfirmation = false

                                    val startTime = System.currentTimeMillis()
                                    val newStroke = StrokeData(
                                        points = mutableListOf(change.position),
                                        color = strokeColor,
                                        width = strokeWidthPx
                                    )
                                    currentStroke = newStroke
                                    inkBuilder.addPoint(
                                        change.position.x,
                                        change.position.y,
                                        startTime
                                    )
                                    change.consume()
                                }
                                change.pressed && currentStroke != null -> {
                                    // Continue stroke
                                    val currentTime = System.currentTimeMillis()
                                    currentStroke?.points?.add(change.position)
                                    inkBuilder.addPoint(
                                        change.position.x,
                                        change.position.y,
                                        currentTime
                                    )
                                    change.consume()
                                }
                                !change.pressed && currentStroke != null -> {
                                    // End stroke
                                    currentStroke?.let { stroke ->
                                        strokes.add(stroke)
                                    }
                                    currentStroke = null
                                    inkBuilder.finishStroke()

                                    // Start auto-recognize timer
                                    autoRecognizeJob?.cancel()
                                    autoRecognizeJob = scope.launch {
                                        delay(autoRecognizeDelayMs)
                                        triggerRecognition()
                                    }
                                    change.consume()
                                }
                            }
                        }
                    }
                }
        ) {
            // Draw completed strokes
            strokes.forEach { stroke ->
                if (stroke.points.size > 1) {
                    val path = Path().apply {
                        moveTo(stroke.points[0].x, stroke.points[0].y)
                        for (i in 1 until stroke.points.size) {
                            lineTo(stroke.points[i].x, stroke.points[i].y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = stroke.color,
                        style = Stroke(
                            width = stroke.width,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }

            // Draw current stroke
            currentStroke?.let { stroke ->
                if (stroke.points.size > 1) {
                    val path = Path().apply {
                        moveTo(stroke.points[0].x, stroke.points[0].y)
                        for (i in 1 until stroke.points.size) {
                            lineTo(stroke.points[i].x, stroke.points[i].y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = stroke.color,
                        style = Stroke(
                            width = stroke.width,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }

        // Empty state hint (only show when no strokes)
        AnimatedVisibility(
            visible = !hasStrokes && !showConfirmation,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Write to add event",
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    modifier = Modifier.size(if (isCompact) 14.dp else 20.dp)
                )
                if (!isCompact) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Write here",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Recognition indicator
        if (isRecognizing) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                strokeWidth = 2.dp
            )
        }

        // Clear button (show when there are strokes but not confirming)
        AnimatedVisibility(
            visible = hasStrokes && !showConfirmation && !isRecognizing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
        ) {
            Surface(
                onClick = { clearAll() },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.size(if (isCompact) 20.dp else 24.dp)
            ) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = "Clear",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(if (isCompact) 3.dp else 4.dp)
                        .fillMaxSize()
                )
            }
        }

        // Confirmation overlay
        AnimatedVisibility(
            visible = showConfirmation && parsedEvent != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isCompact) 4.dp else 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Show parsed event summary
                    parsedEvent?.let { event ->
                        Text(
                            text = event.title,
                            style = if (isCompact) {
                                MaterialTheme.typography.labelMedium
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )

                        if (!event.isAllDay && event.startTime != null) {
                            Text(
                                text = "${event.startTime.hour}:${event.startTime.minute.toString().padStart(2, '0')}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        event.location?.let { loc ->
                            Text(
                                text = "@ $loc",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 8.dp))

                    // Action buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 8.dp)
                    ) {
                        // Cancel/Redo button
                        Surface(
                            onClick = { clearAll() },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(if (isCompact) 28.dp else 36.dp)
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Cancel",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(if (isCompact) 6.dp else 8.dp)
                            )
                        }

                        // Confirm button
                        Surface(
                            onClick = { confirmEvent() },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(if (isCompact) 28.dp else 36.dp)
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Create event",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(if (isCompact) 6.dp else 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

