package com.homedashboard.app.handwriting

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.input.motionprediction.MotionEventPredictor

/**
 * Adaptive writing area that uses the Boox SDK for low-latency pen input
 * on Boox devices, and falls back to Compose Canvas on other devices.
 *
 * On Boox devices with a [BooxPenRouter] available (via [LocalBooxPenRouter]),
 * this composable registers itself as a writing zone rather than creating its
 * own SurfaceView. The single [BooxDrawingSurface] overlay routes pen events
 * to the correct zone. Completed strokes are rendered on a lightweight
 * Compose Canvas.
 *
 * @param strokes The list of completed strokes to render
 * @param currentStroke The stroke currently being drawn (Compose Canvas backend only; Boox SDK renders live strokes)
 * @param zoneId Unique identifier for this writing zone (used for Boox routing)
 * @param config Drawing configuration (stroke color, width, stylus-only mode)
 * @param listener Callback for drawing events (feeds InkBuilder)
 * @param onSizeChanged Called when the canvas size changes
 * @param onFingerTap Called when a finger tap is detected in stylus-only mode
 */
@Composable
fun AdaptiveWritingArea(
    strokes: List<StrokeData>,
    currentStroke: StrokeData?,
    modifier: Modifier = Modifier,
    zoneId: String = "",
    config: DrawingConfig = DrawingConfig(),
    listener: DrawingEventListener? = null,
    onSizeChanged: ((IntSize) -> Unit)? = null,
    onFingerTap: (() -> Unit)? = null
) {
    // Use Compose Canvas for pen input on all devices.
    // Standard Android stylus input works reliably across Boox, Samsung, and other devices.
    // TODO: Add Boox EpdController fast refresh mode for low-latency e-ink rendering.
    ComposeCanvasWritingArea(
        strokes = strokes,
        currentStroke = currentStroke,
        modifier = modifier,
        config = config,
        listener = listener,
        onSizeChanged = onSizeChanged,
        onFingerTap = onFingerTap
    )
}

/**
 * Builds a smoothed path using quadratic bezier curves.
 * Produces much smoother strokes than straight lineTo() segments.
 */
private fun buildSmoothedPath(points: List<Offset>): Path {
    return Path().apply {
        if (points.isEmpty()) return@apply
        moveTo(points[0].x, points[0].y)
        if (points.size <= 2) {
            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }
            return@apply
        }
        for (i in 1 until points.size - 1) {
            val midX = (points[i].x + points[i + 1].x) / 2f
            val midY = (points[i].y + points[i + 1].y) / 2f
            quadraticTo(points[i].x, points[i].y, midX, midY)
        }
        lineTo(points.last().x, points.last().y)
    }
}

/**
 * Standard Compose Canvas-based writing area with low-latency optimizations:
 *
 * 1. pointerInteropFilter — direct MotionEvent access (bypasses Compose event dispatch)
 * 2. requestUnbufferedDispatch — immediate event delivery, skips vsync batching (~16ms saved)
 * 3. Historical MotionEvent points — processes all intermediate samples between frames
 * 4. MotionEventPredictor — renders predicted next point to reduce perceived latency
 * 5. Path caching + bezier smoothing — cached Paths for completed strokes, quadratic curves
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ComposeCanvasWritingArea(
    strokes: List<StrokeData>,
    currentStroke: StrokeData?,
    modifier: Modifier = Modifier,
    config: DrawingConfig = DrawingConfig(),
    listener: DrawingEventListener? = null,
    onSizeChanged: ((IntSize) -> Unit)? = null,
    onFingerTap: (() -> Unit)? = null
) {
    val view = LocalView.current
    val motionPredictor = remember(view) { MotionEventPredictor.newInstance(view) }

    // Predicted point rendered as temporary extension of current stroke
    var predictedPoint by remember { mutableStateOf<Offset?>(null) }

    // Path cache for completed strokes (they never change after being added)
    val pathCache = remember { mutableMapOf<Int, Path>() }
    var lastStrokeCount by remember { mutableIntStateOf(0) }

    // Clear cache when strokes are removed (e.g. clearAll)
    if (strokes.size < lastStrokeCount) {
        pathCache.clear()
    }
    lastStrokeCount = strokes.size

    // Track drawing state outside of the filter lambda
    var isDrawing by remember { mutableStateOf(false) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size -> onSizeChanged?.invoke(size) }
            .pointerInteropFilter { motionEvent ->
                val isStylus = motionEvent.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS ||
                    motionEvent.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER

                // In stylusOnly mode, let non-stylus events pass through
                if (config.stylusOnly && !isStylus) {
                    if (motionEvent.action == MotionEvent.ACTION_UP && onFingerTap != null) {
                        onFingerTap()
                    }
                    return@pointerInteropFilter false
                }

                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Unbuffered dispatch: deliver events immediately, skip vsync batching
                        view.requestUnbufferedDispatch(motionEvent)
                        motionPredictor.record(motionEvent)
                        isDrawing = true
                        listener?.onStrokeStart(
                            motionEvent.x, motionEvent.y, motionEvent.eventTime
                        )
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!isDrawing) return@pointerInteropFilter false
                        motionPredictor.record(motionEvent)

                        // Process ALL historical points (intermediate samples between frames).
                        // On a 240Hz digitizer at 60Hz display, this recovers ~3-4 points per frame.
                        for (i in 0 until motionEvent.historySize) {
                            listener?.onStrokeMove(
                                motionEvent.getHistoricalX(i),
                                motionEvent.getHistoricalY(i),
                                motionEvent.getHistoricalEventTime(i)
                            )
                        }
                        // Then the current point
                        listener?.onStrokeMove(
                            motionEvent.x, motionEvent.y, motionEvent.eventTime
                        )

                        // Render predicted next point to reduce perceived latency
                        val predicted = motionPredictor.predict()
                        predictedPoint = if (predicted != null) {
                            Offset(predicted.x, predicted.y)
                        } else {
                            null
                        }

                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (!isDrawing) return@pointerInteropFilter false
                        isDrawing = false
                        predictedPoint = null
                        listener?.onStrokeEnd()
                        true
                    }
                    else -> false
                }
            }
    ) {
        val strokeStyle = { width: Float ->
            Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
        }

        // Draw completed strokes (cached paths, rebuilt only when new strokes are added)
        strokes.forEachIndexed { index, stroke ->
            if (stroke.points.size > 1) {
                val path = pathCache.getOrPut(index) {
                    buildSmoothedPath(stroke.points)
                }
                drawPath(path = path, color = stroke.color, style = strokeStyle(stroke.width))
            }
        }

        // Draw current stroke with bezier smoothing + predicted point extension
        currentStroke?.let { stroke ->
            if (stroke.points.size > 1) {
                val points = predictedPoint?.let { stroke.points + it } ?: stroke.points
                val path = buildSmoothedPath(points)
                drawPath(path = path, color = stroke.color, style = strokeStyle(stroke.width))
            }
        }
    }
}
