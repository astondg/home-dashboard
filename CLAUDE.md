# Home Dashboard — Claude Development Guide

## Overview

Wall-mounted digital calendar app. Monorepo with `apps/web/` (React + Vite) and `apps/android/` (Kotlin + Jetpack Compose). Primary development is on Android.

**Target devices:** Boox NoteMax (e-ink, 3200x2400, Android 13) AND standard Android phones/tablets. Both must be equally supported — this app will be sold on Google Play Store.

## Quick Reference

```bash
# Build & install on connected device
cd apps/android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew installDebug

# View logs
adb logcat -s WritingDebug:D MainActivity:D

# Build debug APK only
./gradlew assembleDebug
```

## Architecture

### Android App (`apps/android/`)

- **Min SDK:** 33 (Android 13) | **Target SDK:** 35 | **Java:** 17
- **UI:** Jetpack Compose + Material 3
- **Database:** Room (local), DataStore (preferences)
- **Sync:** Google Calendar (OAuth + REST), iCloud (CalDAV), WorkManager background sync
- **Handwriting:** ML Kit Digital Ink (v18.1.0) → NaturalLanguageParser → event creation
- **DI:** ViewModelProvider.Factory (simple, no Hilt)

### Key Packages

```
com.homedashboard.app/
├── calendar/          # CalendarScreen, DayCell, TaskList, dialogs, layouts
├── handwriting/       # ML Kit integration, inline writing areas, NLP parser
├── data/              # Room DB, remote API clients (Google, CalDAV)
├── sync/              # SyncManager, SyncWorker, providers
├── auth/              # Google OAuth, iCloud auth, token storage
├── settings/          # CalendarSettings (DataStore), DisplayDetection
├── viewmodel/         # CalendarViewModel (StateFlow)
└── ui/theme/          # Theme.kt, Type.kt, CalendarPatterns.kt
```

### Display Modes

The app auto-detects Boox devices (`DisplayDetection.kt`) and switches between:
- **E-ink mode:** High contrast (black/white), no animations, no elevation/shadows, pattern-based event differentiation
- **Standard mode:** Material 3 colors, normal animations, elevation

Both modes use the same components — behavior is controlled via `CalendarSettings.eInkRefreshMode` and the theme system.

### Handwriting Pipeline

```
Stylus input → ComposeCanvasWritingArea (pointerInput + PointerType.Stylus)
  → InlineDayWritingArea (per day cell, auto-recognize after 1.5s pause)
  → HandwritingRecognizer (ML Kit Digital Ink)
  → NaturalLanguageParser (text → ParsedEvent)
  → Confirmation overlay → Event creation
```

## Critical Knowledge

### ML Kit Digital Ink
- **MUST** call `.setPreContext("")` on `RecognitionContext.builder()` — omitting this causes a crash (`Missing required properties: preContext`) in v18.1.0+

### Boox Pen SDK
- `TouchHelper` raw drawing does NOT work in Activity-based Compose apps — `onBeginRawDrawing` never fires. See `docs/boox-pen-sdk-investigation.md`
- SDK dependencies are kept in build.gradle.kts for future `EpdController` optimization
- Boox Maven repo: `https://repo.boox.com/repository/maven-public/` (in settings.gradle.kts)

### Styling / UI
- See `docs/design-system.md` for design requirements and guidelines
- E-ink needs: pure black/white, no gradients, no alpha transparency, heavy font weights, large touch targets
- All UI changes must work on both e-ink AND standard Android devices

## Conventions

- Use Jetpack Compose for all UI (no XML layouts)
- Use `StateFlow` for state management in ViewModels
- Use DataStore for settings/preferences, Room for data persistence
- Use Material 3 theme system — customize via `MaterialTheme` and `CompositionLocal`
- Prefer `Modifier` chains for styling (Android's equivalent of Tailwind utility classes)
- Calendar layouts: Grid3x3 (default), TimelineHorizontal, DashboardTodayFocus

## Project Roadmap

See `apps/android/PROJECT.md` for detailed phase tracking. Currently in Phase 6 (Polish & Testing).
