package com.homedashboard.app.handwriting

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Draw
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
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A composable that captures stylus/touch input for handwriting recognition.
 * Displays the drawn strokes and provides controls for clearing and submitting.
 */
@Composable
fun HandwritingCanvas(
    modifier: Modifier = Modifier,
    onRecognitionResult: (String) -> Unit,
    onDismiss: () -> Unit,
    recognizer: HandwritingRecognizer,
    strokeColor: Color = MaterialTheme.colorScheme.onSurface,
    strokeWidth: Dp = 3.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    hintText: String = "Write here..."
) {
    var isRecognizing by remember { mutableStateOf(false) }
    var recognitionError by remember { mutableStateOf<String?>(null) }

    // Track all strokes for drawing
    val strokes = remember { mutableStateListOf<StrokeData>() }
    var currentStroke by remember { mutableStateOf<StrokeData?>(null) }

    // ML Kit ink builder
    val inkBuilder = remember { InkBuilder() }

    val strokeWidthPx = with(LocalDensity.current) { strokeWidth.toPx() }

    Column(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
    ) {
        // Header with hint and status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Draw,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (isRecognizing) "Recognizing..." else hintText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isRecognizing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        // Drawing canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            // Wait for first touch/stylus down
                            val down = awaitFirstDown()
                            val startTime = System.currentTimeMillis()

                            // Start a new stroke
                            val newStroke = StrokeData(
                                points = mutableListOf(down.position),
                                color = strokeColor,
                                width = strokeWidthPx
                            )
                            currentStroke = newStroke
                            inkBuilder.addPoint(
                                down.position.x,
                                down.position.y,
                                startTime
                            )

                            // Track movement
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break

                                if (change.pressed) {
                                    val currentTime = System.currentTimeMillis()
                                    currentStroke?.let { stroke ->
                                        stroke.points.add(change.position)
                                    }
                                    inkBuilder.addPoint(
                                        change.position.x,
                                        change.position.y,
                                        currentTime
                                    )
                                    change.consume()
                                }
                            } while (event.changes.any { it.pressed })

                            // Stroke finished - add to list
                            currentStroke?.let { stroke ->
                                strokes.add(stroke)
                            }
                            currentStroke = null
                            inkBuilder.finishStroke()
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

            // Empty state hint
            if (strokes.isEmpty() && currentStroke == null) {
                Text(
                    text = "Write with stylus or finger",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // Error message
        recognitionError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        // Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            // Clear button
            OutlinedButton(
                onClick = {
                    strokes.clear()
                    currentStroke = null
                    inkBuilder.clear()
                    recognitionError = null
                },
                enabled = strokes.isNotEmpty() && !isRecognizing
            ) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear")
            }

            // Cancel button
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }

            // Recognize button
            Button(
                onClick = {
                    if (inkBuilder.hasStrokes()) {
                        isRecognizing = true
                        recognitionError = null
                        // Recognition is handled by the parent via LaunchedEffect
                    }
                },
                enabled = strokes.isNotEmpty() && !isRecognizing
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Done")
            }
        }
    }

    // Handle recognition when button is pressed
    LaunchedEffect(isRecognizing) {
        if (isRecognizing && inkBuilder.hasStrokes()) {
            val ink = inkBuilder.build()
            when (val result = recognizer.recognize(ink)) {
                is RecognitionResult.Success -> {
                    onRecognitionResult(result.bestMatch)
                }
                is RecognitionResult.Error -> {
                    recognitionError = result.message
                }
            }
            isRecognizing = false
        }
    }
}

/**
 * Data class to store stroke information for drawing.
 * Internal visibility so it can be shared within the handwriting package.
 */
internal data class StrokeData(
    val points: MutableList<Offset>,
    val color: Color,
    val width: Float
)

/**
 * Compact handwriting input that can be embedded in a day cell.
 * Shows a small writing area that expands when tapped.
 */
@Composable
fun CompactHandwritingInput(
    modifier: Modifier = Modifier,
    isExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onRecognitionResult: (String) -> Unit,
    recognizer: HandwritingRecognizer
) {
    if (isExpanded) {
        HandwritingCanvas(
            modifier = modifier.height(150.dp),
            onRecognitionResult = { text ->
                onRecognitionResult(text)
                onExpandChange(false)
            },
            onDismiss = { onExpandChange(false) },
            recognizer = recognizer,
            hintText = "Write event..."
        )
    } else {
        // Collapsed state - show hint to tap
        Surface(
            onClick = { onExpandChange(true) },
            modifier = modifier,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Draw,
                    contentDescription = "Write",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Tap to write",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}
