# Boox Onyx Pen SDK Investigation

## Status: Blocked (Feb 2026)

The Boox Pen SDK's `TouchHelper` raw drawing mode does not work in our Activity-based
Jetpack Compose setup on the NoteMax device. The `onBeginRawDrawing` callback never fires,
even though pen hover detection (`onPenActive`) works correctly.

## Current Approach

We use standard Android stylus input via Compose Canvas (`pointerInput` with
`PointerType.Stylus`). This works reliably on all devices but has higher latency on
e-ink displays due to the standard refresh pipeline.

**Future optimization**: Use Boox `EpdController` API to switch to A2 (fast binary)
refresh mode during handwriting for lower latency. This doesn't require `TouchHelper`.

## What Works

- `TouchHelper.create(surfaceView, 2, callback)` — 3-arg create with penUpRefreshMode=2
- `onPenActive` callback — fires reliably when the pen hovers near the screen
- `openRawDrawing()` + `setRawInputReaderEnable(true)` — no errors thrown
- Standard Android `MotionEvent` with `TOOL_TYPE_STYLUS` — captured correctly

## What Doesn't Work

- `onBeginRawDrawing` — never fires regardless of setup
- `onRawDrawingTouchPointMoveReceived` — never fires
- The SDK's raw input interception never activates; standard Android touch events
  continue reaching the view system unintercepted

## Approaches Tried

1. **SurfaceView via `addContentView`** (on top of Compose) — no raw drawing events
2. **SurfaceView at index 0 in content FrameLayout** (behind Compose) — no raw drawing events
3. **Transparent overlay** (`setZOrderOnTop(true)` + `PixelFormat.TRANSPARENT`) — no raw drawing events
4. **Opaque SurfaceView** (no transparency) — no raw drawing events (black screen)
5. **`setRawInputReaderEnable(true)`** (from boox-rapid-draw project) — no effect
6. **`setStrokeStyle(STROKE_STYLE_PENCIL)`** — no effect
7. **`setRawDrawingEnabled(true)` in `onPenActive`** — no effect
8. **`dispatchTouchEvent` override** routing stylus events to SurfaceView — no effect
9. **SurfaceView `setOnTouchListener { true }`** to consume events — no effect
10. **Different `openRawDrawing` / `setLimitRect` ordering** — no effect
11. **Making SurfaceView focusable and requesting focus** — no effect
12. **2-arg vs 3-arg `TouchHelper.create()`** — 3-arg enables `onPenActive`, 2-arg gives nothing

## Root Cause Hypothesis

The working [boox-rapid-draw](https://github.com/sergeylappo/boox-rapid-draw) project uses
a **system overlay service** (`TYPE_APPLICATION_OVERLAY`) with the `SYSTEM_ALERT_WINDOW`
permission. This creates a fundamentally different window type than an Activity's content view.

The Boox SDK's raw drawing input interception may require the SurfaceView to be in a
**system overlay window**, not a regular Activity window. The SDK likely hooks into the
input pipeline at the window manager level, and this hook only works for specific window types.

### Evidence

- `boox-rapid-draw` uses `OverlayShowingService` (a foreground Service) that creates a
  window with `TYPE_APPLICATION_OVERLAY`
- It requires "Allow display over other apps" permission (Settings > Apps > Special access)
- The official `OnyxAndroidDemo` uses a simple Activity with a SurfaceView as the sole
  content — no Compose, no overlays, no other views on top

### Why We Haven't Tried This

- `SYSTEM_ALERT_WINDOW` is a special permission that can't be granted at install time
  on most devices
- Google Play Store restricts apps requesting this permission
- The architecture change (Activity -> Service + overlay) is significant
- It may not be compatible with Jetpack Compose-based UI

## Future Investigation

If low-latency pen rendering becomes critical:

1. **~~Try `EpdController` first~~** — **IMPLEMENTED** in `EpdRefreshManager.kt`.
   Uses `setViewDefaultUpdateMode(view, UpdateMode.HAND_WRITING_REPAINT_MODE)` on pen down
   and `resetViewUpdateMode(view)` + `repaintEveryThing(UpdateMode.GC)` on pen up.
   Switches the writing area to fast handwriting refresh during drawing, then restores
   normal refresh after. API methods verified against the official
   [OnyxAndroidDemo](https://github.com/onyx-intl/OnyxAndroidDemo) repository.

   **Note (Feb 2026):** Initial implementation used fabricated method names
   (`enableA2ForSpecificView`, `disableA2ForSpecificView`, `applyGCOnce`) that don't
   exist in the SDK. Reflection calls silently failed. Fixed to use real API methods.
   However, refresh mode alone doesn't significantly reduce latency — it only controls
   the e-ink waveform speed, not the rendering pipeline overhead.

   **Direct framebuffer rendering (Feb 2026):** Added `EpdController.moveTo()` /
   `addStrokePoint()` / `finishStroke()` calls in `ComposeCanvasWritingArea`. The methods
   resolve successfully via reflection but appear to be no-ops without an active
   TouchHelper raw drawing pipeline — no visible output on their own.

   **Canvas redraw throttling (Feb 2026):** The key optimization that actually worked.
   Throttle Compose Canvas redraws to ~12fps (83ms interval) on Boox to match e-ink
   refresh capability. Without throttling, 60+ redraws/sec pile up and the display is
   always behind. With throttling, points accumulate between redraws and appear in smooth
   chunks. Combined with `HAND_WRITING_REPAINT_MODE`, this brings latency to a usable
   level — not as fast as the native Boox notes app (which uses firmware-level raw
   drawing), but functional for handwriting recognition input.

2. **System overlay approach** — Create a foreground Service with `TYPE_APPLICATION_OVERLAY`
   window containing the SurfaceView, similar to boox-rapid-draw. Requires
   `SYSTEM_ALERT_WINDOW` permission.

3. **Contact Boox support** — Another developer reported the same issue on BOOX GO 10.3
   (SDK callbacks not firing). This may be a firmware-level issue on newer devices.

4. **Check for newer SDK versions** — Monitor `repo.boox.com/repository/maven-public/`
   for updates to `onyxsdk-pen` (currently 1.4.11) and `onyxsdk-device` (currently 1.2.30).

## SDK Dependencies

```kotlin
implementation("com.onyx.android.sdk:onyxsdk-pen:1.4.11")
implementation("com.onyx.android.sdk:onyxsdk-device:1.2.30")
```

Maven repository: `https://repo.boox.com/repository/maven-public/`

## Device Info

- Device: Boox NoteMax
- Android: 13 (API 33)
- Display: 3200x2400
- Manufacturer reports as "onyx" (detected by `DisplayDetection.isBooxDevice()`)
