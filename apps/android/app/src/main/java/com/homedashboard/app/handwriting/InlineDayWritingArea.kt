package com.homedashboard.app.handwriting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.homedashboard.app.ui.theme.LocalDimensions
import com.homedashboard.app.ui.theme.LocalIsEInk
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

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
    onHandwritingUsed: (() -> Unit)? = null,
    autoRecognizeDelayMs: Long = 1500L,
    zoneId: String = "day-${date}",
    stylusOnly: Boolean = true,
    onFingerTap: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val dims = LocalDimensions.current
    val isEInk = LocalIsEInk.current

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

    // Track canvas size for recognition context
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val hasStrokes = strokes.isNotEmpty() || currentStroke != null

    // Function to trigger recognition
    fun triggerRecognition() {
        if (inkBuilder.hasStrokes() && !isRecognizing) {
            isRecognizing = true
            scope.launch {
                val ink = inkBuilder.build()
                when (val result = recognizer.recognize(
                    ink,
                    writingAreaWidth = canvasSize.width.toFloat(),
                    writingAreaHeight = canvasSize.height.toFloat()
                )) {
                    is RecognitionResult.Success -> {
                        recognizedText = result.bestMatch
                        parsedEvent = parser.parse(result.bestMatch, date)
                        showConfirmation = true
                        onHandwritingUsed?.invoke()
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

    val drawingListener = remember(strokeColor, strokeWidthPx) {
        object : DrawingEventListener {
            override fun onStrokeStart(x: Float, y: Float, timestamp: Long) {
                autoRecognizeJob?.cancel()
                showConfirmation = false
                val newStroke = StrokeData(
                    points = mutableListOf(androidx.compose.ui.geometry.Offset(x, y)),
                    color = strokeColor,
                    width = strokeWidthPx
                )
                currentStroke = newStroke
                inkBuilder.addPoint(x, y, timestamp)
            }

            override fun onStrokeMove(x: Float, y: Float, timestamp: Long) {
                currentStroke?.points?.add(androidx.compose.ui.geometry.Offset(x, y))
                inkBuilder.addPoint(x, y, timestamp)
            }

            override fun onStrokeEnd() {
                currentStroke?.let { stroke -> strokes.add(stroke) }
                currentStroke = null
                inkBuilder.finishStroke()
                autoRecognizeJob?.cancel()
                autoRecognizeJob = scope.launch {
                    delay(autoRecognizeDelayMs)
                    triggerRecognition()
                }
            }

            override fun onDrawingCleared() {
                clearAll()
            }
        }
    }

    Box(modifier = modifier) {
        // Adaptive drawing surface - uses Boox SDK on Boox devices, Compose Canvas elsewhere
        AdaptiveWritingArea(
            strokes = strokes,
            currentStroke = currentStroke,
            modifier = Modifier.fillMaxSize(),
            zoneId = zoneId,
            config = DrawingConfig(
                strokeColor = strokeColor,
                strokeWidth = strokeWidthPx,
                stylusOnly = stylusOnly
            ),
            listener = drawingListener,
            onSizeChanged = { canvasSize = it },
            onFingerTap = onFingerTap
        )

        // Hint removed — the user knows they can write with a stylus.
        // The DayCell WriteHint (shown when no events and no recognizer) handles empty state.

        // Recognition indicator
        if (isRecognizing) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(if (isCompact) 16.dp else 24.dp)
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
                color = if (isEInk) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                },
                modifier = Modifier.size(if (isCompact) 20.dp else 32.dp)
            ) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = "Clear",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(if (isCompact) 3.dp else 6.dp)
                        .fillMaxSize()
                )
            }
        }

        // Confirmation overlay — quick-edit form
        AnimatedVisibility(
            visible = showConfirmation && parsedEvent != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            parsedEvent?.let { event ->
                // Editable state — keyed on parsedEvent so they reset when a new recognition arrives
                var editTitle by remember(event) { mutableStateOf(event.title) }
                var editHour by remember(event) { mutableStateOf(event.startTime?.hour ?: 9) }
                var editMinute by remember(event) { mutableStateOf(event.startTime?.minute ?: 0) }
                var editIsAllDay by remember(event) { mutableStateOf(event.isAllDay) }

                fun saveEvent() {
                    val edited = event.copy(
                        title = editTitle.trim(),
                        startTime = if (editIsAllDay) null else LocalTime.of(editHour, editMinute),
                        endTime = if (editIsAllDay) null else event.endTime,
                        isAllDay = editIsAllDay
                    )
                    onEventCreated(edited)
                    clearAll()
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (isEInk) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(dims.confirmPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Editable title
                        BasicTextField(
                            value = editTitle,
                            onValueChange = { editTitle = it },
                            singleLine = true,
                            textStyle = (if (isCompact) {
                                MaterialTheme.typography.bodyMedium
                            } else {
                                MaterialTheme.typography.headlineSmall
                            }).copy(
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    innerTextField()
                                    HorizontalDivider(
                                        modifier = Modifier
                                            .fillMaxWidth(0.6f)
                                            .padding(top = 2.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(if (isCompact) 6.dp else 12.dp))

                        // Inline time picker (always visible unless all-day)
                        if (!editIsAllDay) {
                            @OptIn(ExperimentalMaterial3Api::class)
                            run {
                                val timePickerState = rememberTimePickerState(
                                    initialHour = editHour,
                                    initialMinute = editMinute,
                                    is24Hour = true
                                )
                                // Sync state back to edit fields
                                LaunchedEffect(timePickerState.hour, timePickerState.minute) {
                                    editHour = timePickerState.hour
                                    editMinute = timePickerState.minute
                                }
                                TimeInput(state = timePickerState)
                            }
                        }

                        // All-day toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { editIsAllDay = !editIsAllDay }
                                .padding(vertical = if (isCompact) 2.dp else 4.dp)
                        ) {
                            Checkbox(
                                checked = editIsAllDay,
                                onCheckedChange = { editIsAllDay = it },
                                modifier = Modifier.size(if (isCompact) 24.dp else 40.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "All day",
                                style = if (isCompact) {
                                    MaterialTheme.typography.bodySmall
                                } else {
                                    MaterialTheme.typography.titleMedium
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(if (isCompact) 6.dp else 12.dp))

                        // Action buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 16.dp)
                        ) {
                            // Cancel/Redo button
                            Surface(
                                onClick = { clearAll() },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(if (isCompact) 36.dp else dims.confirmButtonSize)
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Cancel",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(if (isCompact) 8.dp else 12.dp)
                                )
                            }

                            // Save button
                            Surface(
                                onClick = { saveEvent() },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(if (isCompact) 36.dp else dims.confirmButtonSize)
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Create event",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(if (isCompact) 8.dp else 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
