package com.homedashboard.app.handwriting

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.onyx.android.sdk.pen.TouchHelper

/**
 * Routes pen events from a single Boox [TouchHelper] surface to the correct
 * per-cell [DrawingEventListener] by hit-testing screen-absolute coordinates
 * against registered writing zones.
 *
 * Only one zone can be active (receiving strokes) at a time. The active zone
 * is locked on pen-down and released on pen-up.
 */
class BooxPenRouter {

    companion object {
        private const val TAG = "BooxPenRouter"
        private const val LIMIT_RECT_UPDATE_DELAY_MS = 100L
    }

    /**
     * A registered writing zone with its screen-absolute bounds and event listener.
     */
    data class WritingZone(
        val id: String,
        val bounds: Rect,
        val listener: DrawingEventListener
    )

    private val zones = mutableMapOf<String, WritingZone>()
    private var activeZone: WritingZone? = null
    var touchHelper: TouchHelper? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingLimitRectUpdate: Runnable? = null

    /**
     * Register or update a writing zone.
     */
    fun registerZone(id: String, bounds: Rect, listener: DrawingEventListener) {
        zones[id] = WritingZone(id, bounds, listener)
        scheduleLimitRectUpdate()
    }

    /**
     * Update the bounds of an existing zone (e.g. after scroll or layout change).
     */
    fun updateZoneBounds(id: String, bounds: Rect) {
        zones[id]?.let {
            zones[id] = it.copy(bounds = bounds)
            scheduleLimitRectUpdate()
        }
    }

    /**
     * Unregister a zone (e.g. when a day cell leaves composition).
     */
    fun unregisterZone(id: String) {
        if (activeZone?.id == id) {
            activeZone = null
        }
        zones.remove(id)
        scheduleLimitRectUpdate()
    }

    /**
     * Called when the pen touches down. Hit-tests all zones, locks the match,
     * and forwards the event with coordinates translated to the zone's local space.
     */
    fun onPenDown(x: Float, y: Float, timestamp: Long) {
        if (activeZone != null) {
            Log.d(TAG, "onPenDown: already tracking zone ${activeZone?.id}, ignoring")
            return
        }

        val ix = x.toInt()
        val iy = y.toInt()
        val zone = zones.values.firstOrNull { it.bounds.contains(ix, iy) }
        if (zone == null) {
            Log.d(TAG, "onPenDown: no zone hit at ($ix, $iy), ${zones.size} zones registered: ${zones.values.joinToString { "${it.id}=${it.bounds}" }}")
            return
        }

        activeZone = zone
        val localX = x - zone.bounds.left
        val localY = y - zone.bounds.top
        Log.d(TAG, "Pen down in zone ${zone.id} local=($localX,$localY)")
        zone.listener.onStrokeStart(localX, localY, timestamp)
    }

    /**
     * Called while the pen moves. Routes to the locked active zone.
     */
    fun onPenMove(x: Float, y: Float, timestamp: Long) {
        val zone = activeZone ?: return
        val localX = x - zone.bounds.left
        val localY = y - zone.bounds.top
        zone.listener.onStrokeMove(localX, localY, timestamp)
    }

    /**
     * Called when the pen lifts. Ends the stroke and releases the active zone lock.
     */
    fun onPenUp() {
        val zone = activeZone ?: return
        Log.d(TAG, "Pen up in zone ${zone.id}")
        zone.listener.onStrokeEnd()
        activeZone = null
    }

    /**
     * Compute the union of all registered zone bounds and call
     * [TouchHelper.setLimitRect] so the Boox SDK only captures input
     * within relevant areas.
     */
    private fun applyLimitRects() {
        val helper = touchHelper ?: return
        if (zones.isEmpty()) {
            Log.d(TAG, "No zones registered, skipping limit rect update")
            return
        }

        // Compute the union bounding box of all zones
        val union = Rect()
        zones.values.forEach { zone ->
            union.union(zone.bounds)
        }

        Log.d(TAG, "Setting limit rect to $union (${zones.size} zones)")
        // SDK expects: setLimitRect(singleRect, excludeList)
        helper.setLimitRect(union, ArrayList())
    }

    /**
     * Debounced limit-rect update to avoid thrashing when many zones
     * register or update bounds in quick succession during composition.
     */
    private fun scheduleLimitRectUpdate() {
        pendingLimitRectUpdate?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable { applyLimitRects() }
        pendingLimitRectUpdate = runnable
        mainHandler.postDelayed(runnable, LIMIT_RECT_UPDATE_DELAY_MS)
    }

    /**
     * Clean up all zones and pending callbacks.
     */
    fun dispose() {
        pendingLimitRectUpdate?.let { mainHandler.removeCallbacks(it) }
        zones.clear()
        activeZone = null
        touchHelper = null
    }
}
