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

/**
 * Inline writing area for adding tasks via handwriting.
 * Similar to InlineDayWritingArea but simplified for task input.
 *
 * Key feature: Stylus-only input mode (default)
 * - Stylus/pen input is captured for handwriting
 * - Finger touches pass through to content below (tasks can be tapped)
 *
 * Auto-recognizes text after a pause in writing.
 */
@Composable
fun InlineTaskWritingArea(
    recognizer: HandwritingRecognizer,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    strokeColor: Color = MaterialTheme.colorScheme.onSurface,
    onTaskTextRecognized: (String) -> Unit,
    autoRecognizeDelayMs: Long = 1500L,
    /**
     * When true (default), only stylus input is captured for writing.
     * Finger touches pass through to elements below (like task items).
     * Set to false to accept both stylus and finger input.
     */
    stylusOnly: Boolean = true,
    /**
     * Called when a finger tap occurs and stylusOnly is true.
     * Useful for handling taps on the empty area when no tasks exist.
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
        showConfirmation = false
        autoRecognizeJob?.cancel()
    }

    // Function to confirm and create task
    fun confirmTask() {
        recognizedText?.let { text ->
            onTaskTextRecognized(text)
            clearAll()
        }
    }

    Box(modifier = modifier) {
        // Drawing canvas with stylus input handling
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(stylusOnly) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue

                            // Check if this is stylus input
                            val isStylus = change.type == PointerType.Stylus ||
                                change.type == PointerType.Eraser

                            // If stylusOnly mode, only process stylus input
                            if (stylusOnly && !isStylus) {
                                // Don't consume - let it pass through to content below
                                // But check for tap to trigger optional callback
                                if (!change.pressed && change.previousPressed && onFingerTap != null) {
                                    // This was a finger lift - check if it was a tap
                                    val distance = (change.position - change.previousPosition).getDistance()
                                    if (distance < 20f) {
                                        onFingerTap()
                                    }
                                }
                                continue
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
                    contentDescription = "Write to add task",
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    modifier = Modifier.size(if (isCompact) 16.dp else 24.dp)
                )
                if (!isCompact) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Write here",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Recognition in progress indicator
        AnimatedVisibility(
            visible = isRecognizing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(if (isCompact) 16.dp else 24.dp),
                strokeWidth = 2.dp
            )
        }

        // Confirmation UI
        AnimatedVisibility(
            visible = showConfirmation && recognizedText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(8.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Recognized text
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = recognizedText ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2
                        )
                    }

                    // Action buttons
                    IconButton(
                        onClick = { clearAll() },
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Cancel",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = { confirmTask() },
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Add task",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
