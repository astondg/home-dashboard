package com.homedashboard.app.handwriting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.homedashboard.app.ui.theme.LocalDimensions
import com.homedashboard.app.ui.theme.LocalIsEInk
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
    onHandwritingUsed: (() -> Unit)? = null,
    autoRecognizeDelayMs: Long = 1500L,
    zoneId: String = "task-list",
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

    // Drawing event listener that bridges AdaptiveWritingArea to InkBuilder
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
        // The TaskList empty state hint handles the case when no tasks exist.

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
                color = if (isEInk) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                tonalElevation = if (isEInk) 0.dp else 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(dims.confirmPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Recognized text
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = recognizedText ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2
                        )
                    }

                    // Action buttons
                    IconButton(
                        onClick = { clearAll() },
                        modifier = Modifier
                            .size(dims.confirmButtonSize)
                            .background(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Cancel",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(dims.buttonIconSize)
                        )
                    }

                    IconButton(
                        onClick = { confirmTask() },
                        modifier = Modifier
                            .size(dims.confirmButtonSize)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Add task",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(dims.buttonIconSize)
                        )
                    }
                }
            }
        }
    }
}
