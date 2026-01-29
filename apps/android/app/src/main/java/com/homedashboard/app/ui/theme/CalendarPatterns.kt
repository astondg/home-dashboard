package com.homedashboard.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/**
 * Pattern types for differentiating calendars in monochrome mode.
 * These patterns provide visual distinction without relying on color.
 */
enum class CalendarPattern {
    /** Solid fill (default) */
    SOLID,

    /** Diagonal lines from top-left to bottom-right */
    DIAGONAL_LINES,

    /** Diagonal lines from top-right to bottom-left */
    DIAGONAL_LINES_REVERSE,

    /** Horizontal lines */
    HORIZONTAL_LINES,

    /** Vertical lines */
    VERTICAL_LINES,

    /** Crosshatch pattern */
    CROSSHATCH,

    /** Dotted pattern */
    DOTS,

    /** Small squares/checkerboard */
    CHECKERBOARD
}

/**
 * Mapping of calendar colors to patterns for monochrome mode.
 * When color is not available, we differentiate calendars by pattern.
 */
object CalendarPatternMapper {

    // Predefined patterns for common calendar types
    private val defaultPatterns = listOf(
        CalendarPattern.SOLID,
        CalendarPattern.DIAGONAL_LINES,
        CalendarPattern.HORIZONTAL_LINES,
        CalendarPattern.DOTS,
        CalendarPattern.DIAGONAL_LINES_REVERSE,
        CalendarPattern.VERTICAL_LINES,
        CalendarPattern.CROSSHATCH,
        CalendarPattern.CHECKERBOARD
    )

    /**
     * Get a pattern for a calendar based on its index or color hash
     */
    fun getPatternForCalendar(calendarId: String): CalendarPattern {
        val hash = calendarId.hashCode().let { if (it < 0) -it else it }
        return defaultPatterns[hash % defaultPatterns.size]
    }

    /**
     * Get a pattern based on index (for ordered calendar lists)
     */
    fun getPatternByIndex(index: Int): CalendarPattern {
        return defaultPatterns[index % defaultPatterns.size]
    }
}

/**
 * Draw a pattern background for event chips in monochrome mode
 */
fun DrawScope.drawPattern(
    pattern: CalendarPattern,
    color: Color = Color.Black,
    spacing: Float = 8f
) {
    when (pattern) {
        CalendarPattern.SOLID -> {
            // No pattern needed, handled by background modifier
        }

        CalendarPattern.DIAGONAL_LINES -> {
            drawDiagonalLines(color, spacing, false)
        }

        CalendarPattern.DIAGONAL_LINES_REVERSE -> {
            drawDiagonalLines(color, spacing, true)
        }

        CalendarPattern.HORIZONTAL_LINES -> {
            drawHorizontalLines(color, spacing)
        }

        CalendarPattern.VERTICAL_LINES -> {
            drawVerticalLines(color, spacing)
        }

        CalendarPattern.CROSSHATCH -> {
            drawHorizontalLines(color, spacing)
            drawVerticalLines(color, spacing)
        }

        CalendarPattern.DOTS -> {
            drawDots(color, spacing)
        }

        CalendarPattern.CHECKERBOARD -> {
            drawCheckerboard(color, spacing)
        }
    }
}

private fun DrawScope.drawDiagonalLines(
    color: Color,
    spacing: Float,
    reverse: Boolean
) {
    val strokeWidth = 1.5f
    val step = spacing.dp.toPx()

    if (reverse) {
        // Top-right to bottom-left
        var x = 0f
        while (x < size.width + size.height) {
            drawLine(
                color = color,
                start = Offset(x, 0f),
                end = Offset(0f, x),
                strokeWidth = strokeWidth
            )
            x += step
        }
    } else {
        // Top-left to bottom-right
        var x = -size.height
        while (x < size.width) {
            drawLine(
                color = color,
                start = Offset(x, 0f),
                end = Offset(x + size.height, size.height),
                strokeWidth = strokeWidth
            )
            x += step
        }
    }
}

private fun DrawScope.drawHorizontalLines(color: Color, spacing: Float) {
    val strokeWidth = 1.5f
    val step = spacing.dp.toPx()
    var y = step / 2

    while (y < size.height) {
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = strokeWidth
        )
        y += step
    }
}

private fun DrawScope.drawVerticalLines(color: Color, spacing: Float) {
    val strokeWidth = 1.5f
    val step = spacing.dp.toPx()
    var x = step / 2

    while (x < size.width) {
        drawLine(
            color = color,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = strokeWidth
        )
        x += step
    }
}

private fun DrawScope.drawDots(color: Color, spacing: Float) {
    val radius = 1.5f
    val step = spacing.dp.toPx()

    var y = step / 2
    while (y < size.height) {
        var x = step / 2
        while (x < size.width) {
            drawCircle(
                color = color,
                radius = radius,
                center = Offset(x, y)
            )
            x += step
        }
        y += step
    }
}

private fun DrawScope.drawCheckerboard(color: Color, spacing: Float) {
    val step = spacing.dp.toPx()

    var y = 0f
    var rowOffset = false
    while (y < size.height) {
        var x = if (rowOffset) step else 0f
        while (x < size.width) {
            drawRect(
                color = color,
                topLeft = Offset(x, y),
                size = androidx.compose.ui.geometry.Size(step / 2, step / 2)
            )
            x += step
        }
        y += step / 2
        rowOffset = !rowOffset
    }
}

/**
 * Modifier extension to apply a calendar pattern as background
 */
@Composable
fun Modifier.calendarPatternBackground(
    pattern: CalendarPattern,
    backgroundColor: Color = Color.White,
    patternColor: Color = Color.Black.copy(alpha = 0.3f)
): Modifier {
    return this
        .background(backgroundColor)
        .drawBehind {
            if (pattern != CalendarPattern.SOLID) {
                drawPattern(pattern, patternColor)
            }
        }
}
