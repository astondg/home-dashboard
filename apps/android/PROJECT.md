# HomeDashboard - E-Ink Wall Calendar App

A wall-mounted digital calendar application optimized for e-ink tablets (Boox devices).

## Project Overview

**Target Device:** Boox Note Air5 C (and similar e-ink tablets)
**Platform:** Android (minSdk 33 / Android 13)
**Tech Stack:** Kotlin, Jetpack Compose, Room, DataStore

### Key Features (Planned)
- Multiple switchable calendar layouts optimized for 4:3 e-ink displays
- Direct handwriting input into day cells to create events
- Task tracking alongside calendar
- Fullscreen immersive mode for wall-mounted use
- Pattern-based differentiation for monochrome displays
- Multi-provider calendar sync (Google, Microsoft, iCloud)

---

## Development Phases

### ✅ Phase 0: Environment Setup (Complete)

**Objective:** Establish development environment and validate hardware choice

- [x] Android Studio with Kotlin plugin configured
- [x] Project setup with Gradle 8.10.2, AGP 8.7.3, Kotlin 2.0.21
- [x] Room database setup (AppDatabase, CalendarDao, TaskDao, Converters)
- [x] Basic app running with e-ink considerations

---

### ✅ Phase 1: Core Calendar UI (Complete)

**Objective:** Build the primary calendar interface optimized for e-ink

#### Infrastructure
- [x] CalendarEvent data model
- [x] Task data model with priority levels
- [x] Settings infrastructure with DataStore (CalendarSettings, SettingsRepository)
- [x] Display detection for e-ink vs color (DisplayDetection.kt)
- [x] Fullscreen immersive mode in MainActivity
- [x] Keep screen on flag for wall-mounted use

#### UI Components
- [x] DayCell component with horizontal header layout ("29 Thu" format)
- [x] + button for adding events
- [x] Event list display
- [x] Today highlighting with star indicator
- [x] TaskList component with checkboxes and + button

#### Layouts
- [x] **Grid 3x3 Layout** - 3 rows × 3 columns, 7 day cells + 2-column tasks panel
- [x] **Timeline Horizontal Layout** - Scrollable horizontal day view with tasks panel
- [x] **Dashboard Today Focus Layout** - Large today panel with upcoming events

#### Screens & Data Layer
- [x] CalendarScreen with layout switching
- [x] SettingsScreen (basic structure)
- [x] CalendarViewModel with StateFlow
- [x] ViewModelProvider.Factory for dependency injection

#### Event & Task Creation
- [x] Add Event Dialog (title, location, description, all-day toggle, time picker)
- [x] Add Task Dialog (title, notes, due date, priority)
- [x] Soft delete functionality

---

### ✅ Phase 2: Handwriting Input (Complete)

**Objective:** Enable handwriting-to-text event creation

#### ML Kit Integration
- [x] HandwritingRecognizer class with model download and recognition
- [x] InkBuilder for capturing stylus strokes
- [x] HandwritingCanvas composable for drawing input
- [x] HandwritingInputDialog for full-screen writing experience

#### Natural Language Parsing
- [x] Extracts event title, time, and location from text
- [x] Supports formats: "Soccer 3pm", "dinner 7pm at Hotel", "Meeting at 2:30"
- [x] Time parsing for 12-hour and 24-hour formats

#### Inline Writing in Day Cells
- [x] InlineDayWritingArea composable for writing directly in day cells
- [x] No tap required - just start writing with stylus
- [x] Auto-recognition after 1.5 second pause in writing
- [x] Confirmation overlay shows parsed event before creation
- [x] Clear button to retry writing

#### Dual Input Mode (Stylus + Finger)
- [x] Writing overlay uses `PointerType.Stylus` to differentiate pen from finger
- [x] Stylus input captured for handwriting recognition
- [x] Finger taps pass through to events below (not consumed)
- [x] `stylusOnly` parameter (default: true) controls behavior

---

### ✅ Phase 3: Event & Task Management (Complete)

**Objective:** Allow viewing and editing existing events and tasks

#### Event Management
- [x] Event detail view - tap event to see full details
- [x] Edit Event Dialog - modify existing events (same dialog, edit mode)
- [x] Delete confirmation for events

#### Task Management
- [x] Task detail view - tap task to see full details
- [x] Edit Task Dialog - modify existing tasks (same dialog, edit mode)
- [x] Delete confirmation for tasks
- [x] Toggle completion from detail view

#### New Files Created
- `EventDetailDialog.kt` - Combined view/edit dialog for events
- `TaskDetailDialog.kt` - Combined view/edit dialog for tasks
- `EventDetails` data class - Full event data for viewing/editing
- `TaskDetails` data class - Full task data for viewing/editing

#### ViewModel Additions
- `updateEvent()` - Update existing event in database
- `updateTask()` - Update existing task in database
- `toggleTaskCompletionById()` - Toggle task completion from detail view
- Enhanced `openEventDetail()` / `openTaskDetail()` to fetch full details from DB

---

### ✅ Phase 4: Google Calendar Integration (Complete)

**Objective:** Bidirectional sync with Google Calendar

#### Implementation Complete
- [x] Google Sign-In flow with OAuth 2.0
- [x] Encrypted token storage (EncryptedSharedPreferences)
- [x] Google Calendar API client using OkHttp REST
- [x] Bidirectional sync: download → merge → upload
- [x] Incremental sync with syncToken support
- [x] Last-write-wins conflict resolution
- [x] Background sync with WorkManager (15-minute intervals)
- [x] Settings UI for sign-in/sign-out and sync status

#### New Files Created
- `auth/TokenStorage.kt` - Encrypted token persistence
- `auth/GoogleAuthManager.kt` - Sign-In flow & token management
- `data/remote/GoogleCalendarModels.kt` - API DTOs
- `data/remote/GoogleCalendarService.kt` - REST client
- `data/remote/GoogleEventMapper.kt` - DTO ↔ CalendarEvent mapping
- `data/repository/CalendarRepository.kt` - Repository interface
- `sync/SyncState.kt` - Sync status data classes
- `sync/SyncManager.kt` - Orchestrates sync flow
- `sync/SyncWorker.kt` - WorkManager background sync

#### Pre-requisite: Google Cloud Console Setup Required
To test sync, register the app in Google Cloud Console:
1. Create project → Enable Google Calendar API
2. Configure OAuth consent screen (External, add test users)
3. Create OAuth 2.0 Client ID (Android, package: com.homedashboard.app)
4. Add SHA-1 fingerprint from debug keystore:
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android
   ```

---

### ✅ Phase 5: iCloud Integration (Complete)

**Objective:** Add iCloud Calendar sync via CalDAV

#### iCloud (CalDAV) Implementation
- [x] CalDAV client with HTTP Basic Auth
- [x] XML parsing for PROPFIND and REPORT responses
- [x] iCalendar parsing with ical4j library
- [x] Server discovery via .well-known/caldav
- [x] Incremental sync with sync-token support
- [x] Credential entry UI for app-specific passwords
- [x] Multi-provider sync (Google + iCloud together)

#### New Files Created
- `auth/ICloudAuthManager.kt` - HTTP Basic Auth & server discovery
- `data/remote/caldav/CalDavModels.kt` - CalDAV data models
- `data/remote/caldav/CalDavXmlParser.kt` - XML parsing for CalDAV
- `data/remote/caldav/CalDavService.kt` - WebDAV/CalDAV HTTP client
- `data/remote/caldav/CalDavEventMapper.kt` - iCalendar ↔ CalendarEvent mapping
- `sync/ICloudSyncProvider.kt` - iCloud sync orchestration
- `settings/ICloudLoginDialog.kt` - Login dialog for app-specific password

#### Pre-requisite: iCloud App-Specific Password
To connect iCloud Calendar:
1. Go to appleid.apple.com
2. Sign in with Apple ID
3. Go to Sign-In and Security → App-Specific Passwords
4. Generate a new password
5. Enter Apple ID email + app-specific password in app settings

---

### 🚧 Phase 5b: Microsoft Integration (Optional/Future)

**Objective:** Add Microsoft 365 calendar support

#### Microsoft 365
- [ ] Register app in Azure Portal
- [ ] Implement MSAL authentication
- [ ] Build Graph API client for calendar operations

---

### 📋 Phase 6: Polish & Testing

**Objective:** Production-ready quality

- [ ] Comprehensive UI/UX review and refinement
- [ ] Error handling and offline resilience
- [ ] Performance optimization (startup time, sync speed, memory usage)
- [ ] Accessibility review
- [ ] Security audit (credential storage, API key handling)
- [ ] User testing with family members
- [ ] E-ink optimizations (reduce animations, high contrast mode, partial refresh hints)

---

### 📋 Phase 7: Optional Enhancements

- [ ] Theme selection (light/dark/auto)
- [ ] First day of week setting
- [ ] Number of days to show setting
- [ ] Multiple calendar color coding
- [ ] Weather widget
- [ ] Kiosk mode / screen lock
- [ ] Widget for home screen
- [ ] Import/export events

---

## File Structure

```
app/src/main/java/com/homedashboard/app/
├── MainActivity.kt                 # Entry point, fullscreen mode, ViewModel setup
├── SettingsScreen.kt              # Settings UI with Google auth
├── auth/
│   ├── GoogleAuthManager.kt       # Google Sign-In & token management
│   ├── ICloudAuthManager.kt       # iCloud HTTP Basic Auth & server discovery
│   └── TokenStorage.kt            # Encrypted token persistence
├── calendar/
│   ├── CalendarScreen.kt          # Main calendar view with layout switching
│   ├── components/
│   │   ├── DayCell.kt             # Individual day cell component
│   │   └── TaskList.kt            # Task list component
│   ├── dialogs/
│   │   ├── AddEventDialog.kt      # Dialog for creating events
│   │   ├── AddTaskDialog.kt       # Dialog for creating tasks
│   │   ├── EventDetailDialog.kt   # View/edit dialog for events
│   │   └── TaskDetailDialog.kt    # View/edit dialog for tasks
│   └── layouts/
│       ├── Grid3x3Layout.kt       # 3x3 grid layout
│       ├── TimelineHorizontalLayout.kt  # Horizontal timeline
│       └── DashboardTodayFocusLayout.kt # Dashboard with today focus
├── data/
│   ├── model/
│   │   ├── CalendarEvent.kt       # Event data class
│   │   └── Task.kt                # Task data class
│   ├── local/
│   │   ├── AppDatabase.kt         # Room database
│   │   ├── CalendarDao.kt         # Event data access object
│   │   ├── TaskDao.kt             # Task data access object
│   │   └── Converters.kt          # Type converters
│   ├── remote/
│   │   ├── GoogleCalendarModels.kt    # Google API DTOs
│   │   ├── GoogleCalendarService.kt   # REST client for Google Calendar
│   │   ├── GoogleEventMapper.kt       # DTO ↔ CalendarEvent mapping
│   │   └── caldav/
│   │       ├── CalDavModels.kt        # CalDAV data models
│   │       ├── CalDavXmlParser.kt     # XML parsing for CalDAV responses
│   │       ├── CalDavService.kt       # WebDAV/CalDAV HTTP client
│   │       └── CalDavEventMapper.kt   # iCalendar ↔ CalendarEvent mapping
│   └── repository/
│       └── CalendarRepository.kt      # Repository interface
├── settings/
│   ├── CalendarSettings.kt        # Settings data class & enums
│   ├── SettingsRepository.kt      # Settings persistence
│   ├── DisplayDetection.kt        # E-ink display detection
│   └── ICloudLoginDialog.kt       # iCloud credential entry dialog
├── sync/
│   ├── SyncManager.kt             # Orchestrates multi-provider sync flow
│   ├── SyncState.kt               # Sync status data classes
│   ├── SyncWorker.kt              # WorkManager background sync
│   └── ICloudSyncProvider.kt      # iCloud-specific sync orchestration
├── handwriting/
│   ├── HandwritingRecognizer.kt   # ML Kit Digital Ink integration
│   ├── HandwritingCanvas.kt       # Composable for drawing input
│   ├── HandwritingInputDialog.kt  # Full dialog for handwriting
│   ├── InlineDayWritingArea.kt    # Direct writing in day cells
│   └── NaturalLanguageParser.kt   # Parse text to event details
├── viewmodel/
│   └── CalendarViewModel.kt       # State management for events/tasks/sync
└── ui/theme/
    ├── Theme.kt                   # Material theme
    ├── Type.kt                    # Typography
    └── CalendarPatterns.kt        # Pattern fills for monochrome
```

---

## Tech Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Min SDK | 33 (Android 13) | Modern APIs, Boox devices run recent Android |
| UI Framework | Jetpack Compose | Declarative, modern, good for custom layouts |
| Database | Room | Standard Android persistence |
| Settings | DataStore | Modern replacement for SharedPreferences |
| DI | ViewModelProvider.Factory | Simple, add Hilt if needed |
| State Management | StateFlow | Coroutine-native, lifecycle-aware |
| Handwriting | ML Kit Digital Ink | Proven accuracy, offline capable |
| Google Sync | OkHttp REST | Smaller footprint than Google API client, more control |
| Token Storage | EncryptedSharedPreferences | Secure, Android standard |
| Background Sync | WorkManager | Battery-efficient, respects device state |
| Sync Strategy | Incremental with syncToken | Efficient, only downloads changes |
| Conflict Resolution | Last-Write-Wins | Simple, based on updatedAt timestamps |
| Calendar Sync | REST APIs + CalDAV | Google REST API, CalDAV for iCloud |
| iCloud Parser | ical4j | RFC 5545 compliant iCalendar parsing |

---

## Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Install on device/emulator
./gradlew installDebug

# Run tests
./gradlew test
```

### Emulator Setup for Boox-like Display
- Create custom hardware profile in AVD Manager
- Resolution: 2480 × 1860 (or 1860 × 1240 for Note Air)
- Density: 300 dpi
- Set to landscape orientation

---

*Last updated: January 29, 2026 - Phase 5 Complete (iCloud Integration)*
