package com.homedashboard.app.handwriting

import android.util.Log
import android.view.View
import com.homedashboard.app.settings.DisplayDetection
import java.lang.ref.WeakReference

/**
 * Manages e-ink display refresh and direct framebuffer rendering on Boox devices.
 *
 * Two layers of optimization:
 *
 * 1. **Refresh mode control** — Sets the view to HAND_WRITING_REPAINT_MODE during
 *    pen input so Compose Canvas redraws use a fast e-ink waveform.
 *
 * 2. **Direct framebuffer rendering** — Calls EpdController.moveTo/addStrokePoint/
 *    finishStroke to draw pen strokes directly to the e-ink framebuffer, bypassing
 *    the entire Android rendering pipeline (Compose recomposition, Canvas draw,
 *    buffer swap). This is the same approach Boox's native note apps use.
 *
 * No-ops safely on non-Boox devices. All EpdController calls use reflection since
 * the SDK is only available on Boox firmware.
 */
object EpdRefreshManager {

    private const val TAG = "EpdRefreshManager"

    val isBoox = DisplayDetection.isBooxDevice()

    /** Weak reference to the view currently in fast mode */
    private var activeViewRef: WeakReference<View>? = null

    // --- Reflection cache: refresh mode control ---
    private var refreshResolved = false
    private var updateModeClass: Class<*>? = null
    private var handwritingMode: Any? = null
    private var gcMode: Any? = null
    private var setViewDefaultUpdateModeMethod: java.lang.reflect.Method? = null
    private var resetViewUpdateModeMethod: java.lang.reflect.Method? = null
    private var repaintEveryThingMethod: java.lang.reflect.Method? = null

    // --- Reflection cache: direct framebuffer drawing ---
    private var drawResolved = false
    var directDrawAvailable = false
        private set
    private var moveToMethod: java.lang.reflect.Method? = null
    private var addStrokePointMethod: java.lang.reflect.Method? = null
    private var finishStrokeMethod: java.lang.reflect.Method? = null

    private fun resolveRefresh() {
        if (refreshResolved) return
        refreshResolved = true
        try {
            val epdController = Class.forName("com.onyx.android.sdk.api.device.epd.EpdController")
            updateModeClass = Class.forName("com.onyx.android.sdk.api.device.epd.UpdateMode")

            handwritingMode = java.lang.Enum.valueOf(
                @Suppress("UNCHECKED_CAST")
                (updateModeClass as Class<out Enum<*>>),
                "HAND_WRITING_REPAINT_MODE"
            )
            gcMode = java.lang.Enum.valueOf(
                @Suppress("UNCHECKED_CAST")
                (updateModeClass as Class<out Enum<*>>),
                "GC"
            )

            setViewDefaultUpdateModeMethod = epdController.getMethod(
                "setViewDefaultUpdateMode", View::class.java, updateModeClass
            )
            resetViewUpdateModeMethod = epdController.getMethod(
                "resetViewUpdateMode", View::class.java
            )
            repaintEveryThingMethod = epdController.getMethod(
                "repaintEveryThing", updateModeClass
            )

            Log.d(TAG, "Refresh mode API resolved successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve refresh API: ${e.message}")
        }
    }

    private fun resolveDraw() {
        if (drawResolved) return
        drawResolved = true
        try {
            val epdController = Class.forName("com.onyx.android.sdk.api.device.epd.EpdController")

            // moveTo(View, float x, float y, float strokeWidth)
            moveToMethod = epdController.getMethod(
                "moveTo",
                View::class.java,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType
            )

            // addStrokePoint(float strokeWidth, float x, float y, float pressure, float size, float timestamp)
            addStrokePointMethod = epdController.getMethod(
                "addStrokePoint",
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType
            )

            // finishStroke(float strokeWidth, float x, float y, float pressure, float size, float timestamp)
            finishStrokeMethod = epdController.getMethod(
                "finishStroke",
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType
            )

            directDrawAvailable = true
            Log.d(TAG, "Direct framebuffer drawing API resolved successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Direct drawing API not available: ${e.message}")
            directDrawAvailable = false
        }
    }

    /**
     * Enable fast handwriting refresh mode on the given view. Call on pen ACTION_DOWN.
     */
    fun enableFastMode(view: View) {
        if (!isBoox) return
        if (activeViewRef?.get() === view) return

        resolveRefresh()
        val method = setViewDefaultUpdateModeMethod ?: return
        val mode = handwritingMode ?: return

        try {
            method.invoke(null, view, mode)
            activeViewRef = WeakReference(view)
            Log.d(TAG, "Enabled HAND_WRITING_REPAINT_MODE")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enable fast mode: ${e.message}")
        }
    }

    /**
     * Disable fast refresh mode and restore normal display quality.
     * Call on pen ACTION_UP / ACTION_CANCEL.
     */
    fun disableFastMode(view: View) {
        if (!isBoox) return
        val activeView = activeViewRef?.get()
        if (activeView !== view) return

        resolveRefresh()

        try {
            resetViewUpdateModeMethod?.invoke(null, view)
            repaintEveryThingMethod?.invoke(null, gcMode)
            activeViewRef = null
            Log.d(TAG, "Disabled fast mode, applied GC cleanup")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to disable fast mode: ${e.message}")
        }
    }

    // --- Direct framebuffer drawing ---

    /**
     * Start a stroke at (x, y) by writing directly to the e-ink framebuffer.
     * Bypasses Android's rendering pipeline for near-instant visual feedback.
     */
    fun drawMoveTo(view: View, x: Float, y: Float, strokeWidth: Float) {
        if (!isBoox) return
        resolveDraw()
        val method = moveToMethod ?: return

        try {
            method.invoke(null, view, x, y, strokeWidth)
        } catch (e: Exception) {
            Log.w(TAG, "moveTo failed: ${e.message}")
        }
    }

    /**
     * Add a point to the current stroke on the e-ink framebuffer.
     */
    fun drawStrokePoint(
        strokeWidth: Float, x: Float, y: Float,
        pressure: Float = 1.0f, size: Float = 1.0f, timestamp: Float = 0f
    ) {
        if (!isBoox) return
        val method = addStrokePointMethod ?: return

        try {
            method.invoke(null, strokeWidth, x, y, pressure, size, timestamp)
        } catch (e: Exception) {
            Log.w(TAG, "addStrokePoint failed: ${e.message}")
        }
    }

    /**
     * Finish the current stroke on the e-ink framebuffer.
     */
    fun drawFinishStroke(
        strokeWidth: Float, x: Float, y: Float,
        pressure: Float = 1.0f, size: Float = 1.0f, timestamp: Float = 0f
    ) {
        if (!isBoox) return
        val method = finishStrokeMethod ?: return

        try {
            method.invoke(null, strokeWidth, x, y, pressure, size, timestamp)
        } catch (e: Exception) {
            Log.w(TAG, "finishStroke failed: ${e.message}")
        }
    }
}
