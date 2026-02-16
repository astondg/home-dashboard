package com.homedashboard.app.handwriting

import android.app.Activity
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
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

private const val TAG = "BooxDrawingSurface"

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

    if (activity == null) {
        Log.e(TAG, "BooxDrawingSurface: context is not an Activity, cannot create surface")
        return
    }

    DisposableEffect(Unit) {
        val mainHandler = Handler(Looper.getMainLooper())

        Log.d(TAG, "Creating SurfaceView and adding to Activity window")

        val surfaceView = SurfaceView(activity).apply {
            setZOrderOnTop(true)
            holder.setFormat(android.graphics.PixelFormat.TRANSPARENT)
        }

        val rawInputCallback = object : RawInputCallback() {
            override fun onBeginRawDrawing(p0: Boolean, p1: TouchPoint) {
                Log.d(TAG, "onBeginRawDrawing (${p1.x}, ${p1.y}) timestamp=${p1.timestamp}")
                mainHandler.post {
                    router.onPenDown(p1.x, p1.y, p1.timestamp)
                }
            }

            override fun onEndRawDrawing(p0: Boolean, p1: TouchPoint) {
                Log.d(TAG, "onEndRawDrawing")
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
        Log.d(TAG, "TouchHelper created: $touchHelper")

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
                if (v.width == 0 || v.height == 0) {
                    Log.d(TAG, "onLayoutChange: view has zero dimensions, waiting...")
                    return
                }

                val limit = Rect()
                surfaceView.getLocalVisibleRect(limit)
                Log.d(TAG, "onLayoutChange: view=${v.width}x${v.height}, limit=$limit, opening raw drawing")

                // Official SDK order: setLimitRect -> openRawDrawing -> setRawDrawingEnabled
                touchHelper.setStrokeWidth(config.strokeWidth)
                    .setLimitRect(limit, ArrayList<Rect>())
                    .openRawDrawing()

                touchHelper.setRawDrawingRenderEnabled(true)
                touchHelper.setRawDrawingEnabled(true)
                rawDrawingOpened = true

                Log.d(TAG, "Raw drawing opened and enabled successfully")
            }
        })

        // Surface callbacks for cleanup only
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                Log.d(TAG, "Surface created (raw drawing will open on layout change)")
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                Log.d(TAG, "Surface changed: ${width}x${height}")
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                Log.d(TAG, "Surface destroyed")
            }
        })

        // Step 3: Add SurfaceView directly to the Activity's window
        activity.addContentView(
            surfaceView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        Log.d(TAG, "SurfaceView added to Activity window")

        onDispose {
            Log.d(TAG, "Disposing BooxDrawingSurface")
            if (rawDrawingOpened) {
                touchHelper.setRawDrawingEnabled(false)
                touchHelper.closeRawDrawing()
            }
            router.touchHelper = null
            (surfaceView.parent as? ViewGroup)?.removeView(surfaceView)
        }
    }
}
