# UI Next Steps — E-Ink Readability Pass 2

## Context
After deploying the first UI overhaul (dual typography, centralized dims, color fixes), the app is more readable but events and tasks are still too small for wall-mounted reading distance. The event chips need a design rethink — shaded backgrounds look washed out on e-ink. Tasks panel has lots of wasted horizontal space.

## 1. Redesign Event Chips — Black on White with Bottom Border

Current: horizontal Row with shaded background + left accent bar. Still too small and low-contrast on e-ink.

**New design:**
- Remove background shading entirely on e-ink — pure white/transparent background
- Separate events with a bottom `HorizontalDivider` (1-2dp, dark gray) instead of background color
- Keep the left accent bar (4dp black on e-ink) as the only color indicator
- **Bump text sizes**: title should use `bodyLarge` (20sp e-ink / 16sp standard), time should use `bodyMedium` (18sp e-ink / 14sp standard)
- Increase vertical padding: `chipPaddingVertical` from 8dp → 10-12dp on e-ink
- On standard Android: keep the tinted background approach but with the same larger text

**File:** `DayCell.kt` — `EventChip` composable (~line 330)

## 2. Two-Column Task Layout

Current: TaskList is a single LazyColumn spanning 2 grid columns. With larger text, a single column wastes the horizontal space.

**New design:**
- Split incomplete tasks into two columns using a `LazyVerticalGrid` with 2 columns, or manually split the list into two `LazyColumn`s side by side
- Each task item: checkbox (40dp e-ink) + task title in `bodyLarge` (20sp e-ink)
- Due dates in `bodyMedium` (18sp e-ink) — currently `labelMedium`
- Bottom border separator between tasks (same pattern as events)
- Completed tasks can stay single-column at the bottom or be hidden

**File:** `TaskList.kt` — `TaskList` and `TaskItem` composables

## 3. Hide "Write Here" Hints When Stylus Writing is Available

Current: "Write here" hints show in every empty cell AND the InlineDayWritingArea shows its own "Write here" on top. Double hints, wasted space.

**Change:**
- In `DayCell.kt`: the fallback `WriteHint` already only shows when `recognizer == null`. But the `InlineDayWritingArea` also shows its own hint. When the cell has events, both layers coexist.
- Consider hiding the InlineDayWritingArea hint entirely (it's overlaid on top of events and rarely useful). The user knows they can write — no need for a persistent hint.
- Or: only show the hint when the cell is completely empty AND there are no events.

**Files:**
- `InlineDayWritingArea.kt` — the `AnimatedVisibility` block for the hint (~line 190). Consider removing or making it only show when `events.isEmpty()` (would need to pass that as a parameter).
- `InlineTaskWritingArea.kt` — same pattern

## 4. Event Chip on Standard Android

Keep the colored tinted background for standard displays but apply the same text size increases. The bottom-border approach is specifically for e-ink where background shading doesn't render well.

## Files to Modify

| File | Changes |
|------|---------|
| `DayCell.kt` | EventChip: remove bg on e-ink, add bottom divider, bump text to bodyLarge/bodyMedium |
| `TaskList.kt` | Two-column layout for tasks, bump text sizes, bottom border separators |
| `Dimensions.kt` | Possibly increase `chipPaddingVertical` for e-ink |
| `InlineDayWritingArea.kt` | Hide or reduce "Write here" hint |
| `InlineTaskWritingArea.kt` | Hide or reduce "Write here" hint |

## Build & Test

```bash
cd apps/android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew installDebug
adb logcat -s WritingDebug:D MainActivity:D
```

Verify on Boox NoteMax: events readable from 1.5m, tasks fill the 2-column space, today cell inverted header visible, inline task writing works with stylus.
