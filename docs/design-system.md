# Design System — Home Dashboard

## Design Principles

1. **E-ink first, standard second** — design for the hardest constraint (monochrome, slow refresh, wall-viewing distance), then enhance for standard displays
2. **Readability at distance** — this is a wall calendar; text must be legible from 1-2 meters away
3. **High contrast** — especially critical on e-ink where grays render poorly
4. **Large touch targets** — minimum 48dp for interactive elements (Android accessibility guideline), prefer larger for wall use
5. **Minimal animations** — disabled entirely on e-ink (causes flickering), subtle on standard

## Display Targets

### Boox NoteMax (Primary)
- Screen: 13.3" e-ink, 3200x2400 px, ~284 DPI
- Orientation: Landscape (wall-mounted)
- Color: Monochrome (16 shades of gray, but design for pure black/white)
- Refresh: Full refresh ~300ms, partial ~100ms — avoid animations
- OS: Android 13

### Standard Android (Secondary)
- Various screen sizes and densities
- Full color
- Normal refresh rates — animations OK
- Phones and tablets

## Typography

### E-Ink Guidelines
- **Body text:** 18sp minimum, `FontWeight.Medium` (500) — regular weight appears too thin on e-ink
- **Headers/day numbers:** 24-32sp, `FontWeight.Bold` (700)
- **Labels/captions:** 16sp minimum — never go below 14sp on e-ink
- **Line height:** 1.4-1.6x font size
- **Font family:** System default (Roboto) at heavier weights, or a serif font for body text

### Standard Display Guidelines
- Can use standard Material 3 type scale
- Regular font weights are fine
- Slightly smaller sizes acceptable on phones

### Current Type Scale (to be updated)

| Role | Current | Target (e-ink) | Target (standard) |
|------|---------|----------------|-------------------|
| Day number | headlineMedium (28sp) | 32sp bold | 28sp medium |
| Day name | labelMedium | 18sp medium | 14sp |
| Event title | bodySmall/labelSmall | 18sp medium | 14sp |
| Event time | labelSmall | 16sp | 12sp |
| "Write here" hint | ? | 16sp, medium contrast | 14sp, light |
| Dialog title | titleLarge (22sp) | 28sp bold | 22sp |
| Dialog body/fields | bodyLarge (16sp) | 20sp | 16sp |
| Task title | bodyMedium | 18sp medium | 14sp |
| Header "February 2026" | ? | 28sp bold | 22sp |

## Color

### E-Ink Palette
```
Primary:     #000000 (pure black)
On Primary:  #FFFFFF (pure white)
Background:  #FFFFFF (pure white)
Surface:     #FFFFFF (pure white)
On Surface:  #000000 (pure black)
Border:      #000000 (pure black)
Hint text:   #404040 (dark gray — NOT light gray)
```

Rules:
- No alpha transparency on e-ink (renders as muddy gray)
- No gradients
- No elevation/shadows (render as gray blobs)
- Use border thickness and font weight for visual hierarchy
- Use pattern fills (`CalendarPatterns.kt`) to differentiate events

### Standard Display Palette
- Material 3 light/dark color schemes
- Event colors via `CalendarEventUi.color` with 20% alpha background chips
- Normal elevation and shadows

## Spacing & Sizing

### Touch Targets
- **Minimum:** 48dp (Android accessibility)
- **Preferred for wall calendar:** 56-64dp for primary actions
- **"+" buttons:** Currently 28-32dp — needs to be at least 48dp
- **Event chips:** Currently 2-4dp padding — needs more for tappability

### Layout Spacing
- **Grid cell gap:** 4dp (current) — consider 2dp for more content space, or use borders instead
- **Cell content padding:** 8-12dp
- **Dialog padding:** 24dp (current) — increase for e-ink readability

### Cell Layout (Grid 3x3)
- Header: day number + day name + add button in a row
- Events: scrollable list filling remaining space
- Writing overlay: covers events area, stylus-only input

## Component Guidelines

### Day Cell
- **Border:** 2dp on e-ink (currently 1dp default), solid black
- **Today highlight:** Bold border (3dp), filled header background
- **Header:** Large day number, abbreviated day name, visible "+" button
- **Events:** Each event clearly separated, readable text, visible time

### Event Chips
- **E-ink:** Black text on white, with left border indicator or pattern fill
- **Standard:** Colored background (20% alpha) with colored text
- **Padding:** At least 8dp horizontal, 6dp vertical
- **Text:** Event title clearly readable, time visible

### Handwriting Confirmation Overlay
- **Current problem:** Tiny popup with small text and tiny X/checkmark buttons
- **Target:** Full-width overlay within the day cell, large text showing recognized event, large confirm/cancel buttons (48dp+)

### Dialogs (Add Event, Event Detail, etc.)
- **Width:** 90% screen (current) — good
- **Text sizes:** Increase all fields for e-ink readability
- **Buttons:** Large, high contrast, clearly labeled
- **Spacing:** Generous padding between fields

### Task List
- **Checkbox:** Large (at least 32dp)
- **Task text:** 18sp on e-ink
- **Completed tasks:** Strikethrough, dimmed (but still readable on e-ink)

## Responsive Behavior

The app detects device type via `DisplayDetection.kt` and adjusts:

| Property | E-Ink | Standard |
|----------|-------|----------|
| Animations | Disabled | Enabled |
| Shadows/elevation | None | Material defaults |
| Font weight | Medium/Bold | Normal/Medium |
| Font size | Larger | Material defaults |
| Colors | Black/white only | Full Material palette |
| Borders | Thick, black | Subtle, themed |
| Touch targets | 56dp+ | 48dp |
| Event differentiation | Patterns | Colors |

## Implementation Notes

### Theme Architecture
- Three color schemes in `Theme.kt`: dark, light, e-ink
- E-ink mode toggled via `CalendarSettings.eInkRefreshMode`
- Auto-detection in `DisplayDetection.kt` (checks for Boox manufacturer)
- Custom `CompositionLocal` values can extend MaterialTheme for app-specific tokens

### Styling Approach
- Material 3 as the base component library
- Modifier chains for layout/styling (Android's equivalent of Tailwind utility classes)
- No third-party styling frameworks needed
- Build thin wrapper components that read theme + e-ink config and adapt
