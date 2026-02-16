package com.homedashboard.app.handwriting

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * CompositionLocal providing the shared [BooxPenRouter] for Boox devices.
 * Null on non-Boox devices or outside the CalendarScreen scope.
 */
val LocalBooxPenRouter = staticCompositionLocalOf<BooxPenRouter?> { null }

/**
 * Listener interface for drawing events from any backend (Compose Canvas or Boox SDK).
 * Both backends feed the same InkBuilder for ML Kit recognition.
 */
interface DrawingEventListener {
    fun onStrokeStart(x: Float, y: Float, timestamp: Long)
    fun onStrokeMove(x: Float, y: Float, timestamp: Long)
    fun onStrokeEnd()
    fun onDrawingCleared()
}

/**
 * Configuration for the drawing surface.
 */
data class DrawingConfig(
    val strokeColor: Color = Color.Black,
    val strokeWidth: Float = 5f,
    val stylusOnly: Boolean = true
)
