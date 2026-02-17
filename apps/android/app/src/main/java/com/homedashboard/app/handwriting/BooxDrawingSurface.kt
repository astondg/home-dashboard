package com.homedashboard.app.handwriting

import android.app.Activity
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPointList

/**
 * Single full-screen Boox drawing surface that routes pen events through [BooxPenRouter].
 *
 * Adds a SurfaceView directly to the Activity's window because the Boox SDK requires
 * the SurfaceView to be a direct child of the Activity's view hierarchy.
 *
 * Key initialization order (per official Onyx SDK examples):
 * 1. TouchHelper.create(surfaceView, callback)
 * 2. Wait for onLayoutChange (view must have real dimensions)
 * 3. setLimitRect(limit, excludes) — configure drawable region
 * 4. openRawDrawing() — initialize the pen input pipeline
 * 5. setRawDrawingEnabled(true) — activate pen capture (AFTER open)
 */
@Composable
fun BooxDrawingSurface(
    router: BooxPenRouter,
    modifier: Modifier = Modifier,
    config: DrawingConfig = DrawingConfig()
) {
    val context = LocalContext.current
    val activity = context as? Activity

    if (activity == null) return

    DisposableEffect(Unit) {
        val mainHandler = Handler(Looper.getMainLooper())

        val surfaceView = SurfaceView(activity).apply {
            setZOrderOnTop(true)
            holder.setFormat(android.graphics.PixelFormat.TRANSPARENT)
        }

        val rawInputCallback = object : RawInputCallback() {
            override fun onBeginRawDrawing(p0: Boolean, p1: TouchPoint) {
                mainHandler.post {
                    router.onPenDown(p1.x, p1.y, p1.timestamp)
                }
            }

            override fun onEndRawDrawing(p0: Boolean, p1: TouchPoint) {
                mainHandler.post {
                    router.onPenUp()
                }
            }

            override fun onRawDrawingTouchPointMoveReceived(touchPoint: TouchPoint) {
                mainHandler.post {
                    router.onPenMove(touchPoint.x, touchPoint.y, touchPoint.timestamp)
                }
            }

            override fun onRawDrawingTouchPointListReceived(touchPointList: TouchPointList) {
                val points = touchPointList.getPoints() ?: return
                mainHandler.post {
                    for (point in points) {
                        router.onPenMove(point.x, point.y, point.timestamp)
                    }
                }
            }

            override fun onBeginRawErasing(p0: Boolean, p1: TouchPoint) {}
            override fun onEndRawErasing(p0: Boolean, p1: TouchPoint) {}
            override fun onRawErasingTouchPointMoveReceived(p0: TouchPoint) {}
            override fun onRawErasingTouchPointListReceived(p0: TouchPointList) {}
        }

        // Step 1: Create TouchHelper
        val touchHelper = TouchHelper.create(surfaceView, rawInputCallback)

        touchHelper.setStrokeWidth(config.strokeWidth)
        touchHelper.setStrokeColor(config.strokeColor.toArgb())
        router.touchHelper = touchHelper

        // Step 2: Open raw drawing AFTER layout (NOT in surfaceCreated)
        // The Boox SDK requires the view to have real dimensions before openRawDrawing().
        var rawDrawingOpened = false
        surfaceView.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View, left: Int, top: Int, right: Int, bottom: Int,
                oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
            ) {
                if (v.width == 0 || v.height == 0) return

                val limit = Rect()
                surfaceView.getLocalVisibleRect(limit)

                // Official SDK order: setLimitRect -> openRawDrawing -> setRawDrawingEnabled
                touchHelper.setStrokeWidth(config.strokeWidth)
                    .setLimitRect(limit, ArrayList<Rect>())
                    .openRawDrawing()

                touchHelper.setRawDrawingRenderEnabled(true)
                touchHelper.setRawDrawingEnabled(true)
                rawDrawingOpened = true
            }
        })

        // Surface callbacks for cleanup only
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {}
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {}
        })

        // Step 3: Add SurfaceView directly to the Activity's window
        activity.addContentView(
            surfaceView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        onDispose {
            if (rawDrawingOpened) {
                touchHelper.setRawDrawingEnabled(false)
                touchHelper.closeRawDrawing()
            }
            router.touchHelper = null
            (surfaceView.parent as? ViewGroup)?.removeView(surfaceView)
        }
    }
}
