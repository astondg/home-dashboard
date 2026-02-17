package com.homedashboard.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class AppDimensions(
    val buttonSizeSmall: Dp,
    val buttonIconSize: Dp,
    val chipPaddingHorizontal: Dp,
    val chipPaddingVertical: Dp,
    val cellBorderWidth: Dp,
    val cellBorderWidthToday: Dp,
    val cellHeaderPaddingHorizontal: Dp,
    val cellHeaderPaddingVertical: Dp,
    val confirmButtonSize: Dp,
    val confirmPadding: Dp,
    val checkboxSize: Dp,
    val hintIconSize: Dp
)

val StandardDimensions = AppDimensions(
    buttonSizeSmall = 36.dp,
    buttonIconSize = 18.dp,
    chipPaddingHorizontal = 8.dp,
    chipPaddingVertical = 4.dp,
    cellBorderWidth = 1.dp,
    cellBorderWidthToday = 3.dp,
    cellHeaderPaddingHorizontal = 10.dp,
    cellHeaderPaddingVertical = 2.dp,
    confirmButtonSize = 48.dp,
    confirmPadding = 12.dp,
    checkboxSize = 32.dp,
    hintIconSize = 24.dp
)

val WallCalendarDimensions = AppDimensions(
    buttonSizeSmall = 48.dp,
    buttonIconSize = 26.dp,
    chipPaddingHorizontal = 12.dp,
    chipPaddingVertical = 4.dp,
    cellBorderWidth = 2.dp,
    cellBorderWidthToday = 4.dp,
    cellHeaderPaddingHorizontal = 12.dp,
    cellHeaderPaddingVertical = 2.dp,
    confirmButtonSize = 56.dp,
    confirmPadding = 16.dp,
    checkboxSize = 40.dp,
    hintIconSize = 28.dp
)

val LocalDimensions = staticCompositionLocalOf { StandardDimensions }
