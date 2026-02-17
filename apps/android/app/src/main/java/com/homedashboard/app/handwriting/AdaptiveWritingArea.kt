package com.homedashboard.app.handwriting

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke as InkStroke
import androidx.input.motionprediction.MotionEventPredictor
import com.homedashboard.app.settings.DisplayDetection

/**
 * Adaptive writing area that selects the best rendering backend:
 *
 * - **Standard Android devices:** Uses [InkApiWritingArea] with `InProgressStrokesView` for
 *   front-buffer OpenGL rendering (~4ms latency vs ~40-60ms with Compose Canvas).
 * - **Boox e-ink devices:** Uses [ComposeCanvasWritingArea] (Compose Canvas path), since
 *   front-buffer GL rendering doesn't benefit e-ink displays.
 *
 * Both backends feed the same [DrawingEventListener] for ML Kit recognition. Callers
 * see no difference — the public API is identical.
 *
 * @param strokes The list of completed strokes (used for UI logic like `hasStrokes` checks)
 * @param currentStroke The stroke currently being drawn (Compose Canvas backend only)
 * @param zoneId Unique identifier for this writing zone
 * @param config Drawing configuration (stroke color, width, stylus-only mode)
 * @param listener Callback for drawing events (feeds InkBuilder for ML Kit)
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
    if (DisplayDetection.isBooxDevice()) {
        // Boox e-ink: Compose Canvas (front-buffer GL doesn't help e-ink refresh)
        ComposeCanvasWritingArea(
            strokes = strokes,
            currentStroke = currentStroke,
            modifier = modifier,
            config = config,
            listener = listener,
            onSizeChanged = onSizeChanged,
            onFingerTap = onFingerTap
        )
    } else {
        // Standard Android: Low-latency front-buffer rendering via androidx.ink
        InkApiWritingArea(
            strokes = strokes,
            currentStroke = currentStroke,
            modifier = modifier,
            config = config,
            listener = listener,
            onSizeChanged = onSizeChanged,
            onFingerTap = onFingerTap
        )
    }
}

/**
 * Low-latency writing area using `androidx.ink` (`InProgressStrokesView`).
 *
 * Rendering architecture:
 * - **Wet ink** (in-progress strokes): `InProgressStrokesView` renders via front-buffer OpenGL
 *   (~4ms latency, bypasses double-buffering and vsync).
 * - **Dry ink** (finished strokes): `CanvasStrokeRenderer` draws on a Compose Canvas underneath.
 *
 * Touch events are intercepted via `OnTouchListener` on the `InProgressStrokesView`, filtered
 * for stylus (when `config.stylusOnly`), and forwarded to both:
 * 1. `InProgressStrokesView` (for rendering)
 * 2. `DrawingEventListener` (for ML Kit recognition via callers' InkBuilder)
 *
 * The caller's `strokes` list is monitored for size decreases (indicating a clear operation),
 * which triggers clearing the internal dry stroke set.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
internal fun InkApiWritingArea(
    strokes: List<StrokeData>,
    @Suppress("UNUSED_PARAMETER") currentStroke: StrokeData?,
    modifier: Modifier = Modifier,
    config: DrawingConfig = DrawingConfig(),
    listener: DrawingEventListener? = null,
    onSizeChanged: ((IntSize) -> Unit)? = null,
    onFingerTap: (() -> Unit)? = null
) {
    // Keep references to composable params that the touch listener reads.
    // rememberUpdatedState ensures the lambda always reads the latest values
    // even though it was created once in the AndroidView factory.
    val listenerState = rememberUpdatedState(listener)
    val onFingerTapState = rememberUpdatedState(onFingerTap)
    val configState = rememberUpdatedState(config)

    // Create ink Brush from DrawingConfig
    val brush = remember(config.strokeColor, config.strokeWidth) {
        Brush.createWithColorIntArgb(
            family = StockBrushes.pressurePen(),
            colorIntArgb = config.strokeColor.toArgb(),
            size = config.strokeWidth,
            epsilon = 0.1f
        )
    }
    val brushState = rememberUpdatedState(brush)

    // Internal set of finished strokes rendered as "dry" ink
    val finishedStrokes = remember { mutableStateListOf<InkStroke>() }
    val canvasStrokeRenderer = remember { CanvasStrokeRenderer.create() }
    val identityMatrix = remember { android.graphics.Matrix() }

    // Track caller's stroke count to detect clears
    var lastStrokeCount by remember { mutableIntStateOf(0) }
    if (strokes.size < lastStrokeCount) {
        finishedStrokes.clear()
    }
    lastStrokeCount = strokes.size

    // Current in-progress stroke ID (read/written from touch listener)
    val currentStrokeIdRef = remember { mutableStateOf<InProgressStrokeId?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size -> onSizeChanged?.invoke(size) }
    ) {
        // Layer 1 (bottom): Dry strokes rendered on Compose Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val nativeCanvas = drawContext.canvas.nativeCanvas
            finishedStrokes.forEach { stroke ->
                canvasStrokeRenderer.draw(nativeCanvas, stroke, identityMatrix)
            }
        }

        // Layer 2 (top): InProgressStrokesView — wet ink rendering + touch handling
        AndroidView(
            factory = { context ->
                InProgressStrokesView(context).apply {
                    val predictor = MotionEventPredictor.newInstance(this)

                    addFinishedStrokesListener(object : InProgressStrokesFinishedListener {
                        override fun onStrokesFinished(strokes: Map<InProgressStrokeId, InkStroke>) {
                            finishedStrokes.addAll(strokes.values)
                            removeFinishedStrokes(strokes.keys)
                        }
                    })

                    setOnTouchListener { v, event ->
                        val cfg = configState.value
                        val isStylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS ||
                            event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER

                        if (cfg.stylusOnly && !isStylus) {
                            if (event.action == MotionEvent.ACTION_UP) {
                                onFingerTapState.value?.invoke()
                            }
                            return@setOnTouchListener false
                        }

                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                v.requestUnbufferedDispatch(event)
                                predictor.record(event)
                                val pointerId = event.getPointerId(event.actionIndex)
                                currentStrokeIdRef.value = startStroke(
                                    event, pointerId, brushState.value,
                                    identityMatrix, identityMatrix
                                )
                                listenerState.value?.onStrokeStart(
                                    event.x, event.y, event.eventTime
                                )
                                true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val id = currentStrokeIdRef.value
                                    ?: return@setOnTouchListener false
                                predictor.record(event)
                                val predicted = predictor.predict()
                                try {
                                    addToStroke(event, event.getPointerId(0), id, predicted)
                                } finally {
                                    predicted?.recycle()
                                }

                                // Forward all points to listener for ML Kit recognition
                                val lsnr = listenerState.value
                                for (i in 0 until event.historySize) {
                                    lsnr?.onStrokeMove(
                                        event.getHistoricalX(i),
                                        event.getHistoricalY(i),
                                        event.getHistoricalEventTime(i)
                                    )
                                }
                                lsnr?.onStrokeMove(event.x, event.y, event.eventTime)
                                true
                            }
                            MotionEvent.ACTION_UP -> {
                                val id = currentStrokeIdRef.value
                                    ?: return@setOnTouchListener false
                                val pointerId = event.getPointerId(event.actionIndex)
                                finishStroke(event, pointerId, id)
                                currentStrokeIdRef.value = null
                                listenerState.value?.onStrokeEnd()
                                true
                            }
                            MotionEvent.ACTION_CANCEL -> {
                                val id = currentStrokeIdRef.value
                                    ?: return@setOnTouchListener false
                                cancelStroke(id, event)
                                currentStrokeIdRef.value = null
                                listenerState.value?.onStrokeEnd()
                                true
                            }
                            else -> false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
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
