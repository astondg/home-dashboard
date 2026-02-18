package com.homedashboard.app

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.homedashboard.app.auth.GoogleAuthManager
import com.homedashboard.app.auth.ICloudAuthManager
import com.homedashboard.app.auth.TokenStorage
import com.homedashboard.app.calendar.CalendarScreen
import com.homedashboard.app.calendar.dialogs.AddEventDialog
import com.homedashboard.app.calendar.dialogs.AddTaskDialog
import com.homedashboard.app.calendar.dialogs.EventDetailDialog
import com.homedashboard.app.calendar.dialogs.TaskDetailDialog
import com.homedashboard.app.data.local.AppDatabase
import com.homedashboard.app.data.remote.GoogleCalendarService
import com.homedashboard.app.data.remote.GoogleEventMapper
import com.homedashboard.app.data.remote.caldav.CalDavEventMapper
import com.homedashboard.app.data.remote.caldav.CalDavService
import com.homedashboard.app.handwriting.EntityExtractionParser
import com.homedashboard.app.handwriting.HandwritingRecognizer
import com.homedashboard.app.settings.CalendarSettings
import com.homedashboard.app.settings.DisplayDetection
import com.homedashboard.app.settings.SettingsRepository
import com.homedashboard.app.sync.ICloudSyncProvider
import com.homedashboard.app.sync.SyncManager
import com.homedashboard.app.sync.SyncWorker
import com.homedashboard.app.ui.theme.HomeDashboardTheme
import com.homedashboard.app.viewmodel.CalendarViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewModel: CalendarViewModel
    private lateinit var handwritingRecognizer: HandwritingRecognizer
    private lateinit var entityExtractionParser: EntityExtractionParser

    // Google Calendar sync components
    private lateinit var tokenStorage: TokenStorage
    private lateinit var authManager: GoogleAuthManager
    private lateinit var syncManager: SyncManager

    // iCloud Calendar sync components
    private lateinit var iCloudAuthManager: ICloudAuthManager
    private lateinit var iCloudSyncProvider: ICloudSyncProvider

    // Activity result launcher for Google Sign-In
    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "Sign-in result: ${result.resultCode}")
        viewModel.handleSignInResult(result.data) {
            // Schedule background sync after successful sign-in
            SyncWorker.schedulePeriodicSync(applicationContext)
            Log.d(TAG, "Background sync scheduled after sign-in")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display
        enableEdgeToEdge()

        // Enable fullscreen immersive mode (hides status bar and nav bar)
        enableFullscreen()

        // Initialize repositories and database
        settingsRepository = SettingsRepository(applicationContext)

        // Track latest settings for the night dimming timer
        var latestSettings = CalendarSettings()

        // Observe always-on display and night dimming settings
        lifecycleScope.launch {
            settingsRepository.settings.collect { settings ->
                latestSettings = settings
                // Always-on display toggle
                if (settings.alwaysOnDisplay) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                // Apply brightness immediately when settings change
                applyNightDimming(settings)
            }
        }

        // Periodic night dimming check (time changes even when settings don't)
        lifecycleScope.launch {
            while (true) {
                delay(60_000L)
                applyNightDimming(latestSettings)
            }
        }
        val database = AppDatabase.getDatabase(applicationContext)

        // Initialize Google Calendar sync components
        tokenStorage = TokenStorage(applicationContext)
        authManager = GoogleAuthManager(applicationContext, tokenStorage)

        // Build OkHttp client for API calls
        val okHttpClient = buildOkHttpClient()
        val googleService = GoogleCalendarService(okHttpClient, authManager)
        val eventMapper = GoogleEventMapper()

        // Initialize iCloud Calendar sync components
        iCloudAuthManager = ICloudAuthManager(tokenStorage, okHttpClient)
        val calDavService = CalDavService(okHttpClient, iCloudAuthManager)
        val calDavEventMapper = CalDavEventMapper()

        iCloudSyncProvider = ICloudSyncProvider(
            calendarDao = database.calendarDao(),
            calDavService = calDavService,
            eventMapper = calDavEventMapper,
            tokenStorage = tokenStorage,
            authManager = iCloudAuthManager
        )

        syncManager = SyncManager(
            calendarDao = database.calendarDao(),
            googleService = googleService,
            eventMapper = eventMapper,
            tokenStorage = tokenStorage,
            iCloudSyncProvider = iCloudSyncProvider
        )

        // Initialize ViewModel with auth and sync support
        viewModel = ViewModelProvider(
            this,
            CalendarViewModel.Factory(
                calendarDao = database.calendarDao(),
                taskDao = database.taskDao(),
                authManager = authManager,
                iCloudAuthManager = iCloudAuthManager,
                syncManager = syncManager,
                settingsRepository = settingsRepository
            )
        )[CalendarViewModel::class.java]

        // Check auth state and schedule background sync if authenticated
        lifecycleScope.launch {
            authManager.checkAuthState()
            iCloudAuthManager.checkAuthState()

            // Schedule background sync if any provider is authenticated
            val hasGoogle = tokenStorage.hasTokens()
            val hasICloud = tokenStorage.hasICloudCredentials()
            if (hasGoogle || hasICloud) {
                SyncWorker.schedulePeriodicSync(applicationContext)
                Log.d(TAG, "Background sync scheduled (Google: $hasGoogle, iCloud: $hasICloud)")
            }
        }

        // Initialize handwriting recognizer and entity extraction parser
        handwritingRecognizer = HandwritingRecognizer(applicationContext)
        entityExtractionParser = EntityExtractionParser()
        lifecycleScope.launch {
            val success = handwritingRecognizer.initialize()
            Log.d(TAG, "HandwritingRecognizer initialized: success=$success, isReady=${handwritingRecognizer.isReady()}")
            val entitySuccess = entityExtractionParser.initialize()
            Log.d(TAG, "EntityExtractionParser initialized: success=$entitySuccess")
        }

        setContent {
            // Collect settings as state
            val settings by settingsRepository.settings.collectAsState(
                initial = CalendarSettings()
            )

            // Collect ViewModel state
            val eventsByDate by viewModel.eventsByDate.collectAsState()
            val tasks by viewModel.tasks.collectAsState()
            val showAddEventForDate by viewModel.showAddEventDialog.collectAsState()
            val showAddTask by viewModel.showAddTaskDialog.collectAsState()
            val showEventDetail by viewModel.showEventDetailDialog.collectAsState()
            val showTaskDetail by viewModel.showTaskDetailDialog.collectAsState()

            // Collect Google Calendar auth and sync state
            val authState by viewModel.authState.collectAsState()
            val syncState by viewModel.syncState.collectAsState()

            // Collect iCloud Calendar auth state
            val iCloudAuthState by viewModel.iCloudAuthState.collectAsState()
            val iCloudConnectError by viewModel.iCloudConnectError.collectAsState()

            // Collect calendar selection state
            val allCalendars by viewModel.calendars.collectAsState()
            val calendarSettings by viewModel.calendarSettings.collectAsState()

            // Collect weather data
            val weatherByDate by viewModel.weatherByDate.collectAsState()
            val rainForecast by viewModel.rainForecast.collectAsState()

            // Track if settings screen should be shown
            var showSettings by remember { mutableStateOf(false) }

            // Compute effective e-ink mode: user setting OR auto-detect
            val effectiveEInk = settings.eInkRefreshMode ||
                DisplayDetection.isEinkDisplay(applicationContext)

            HomeDashboardTheme(
                eInkMode = effectiveEInk,
                wallCalendarMode = settings.wallCalendarMode
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showSettings) {
                        SettingsScreen(
                            settings = settings,
                            onBack = { showSettings = false },
                            onLayoutChange = { layoutType ->
                                lifecycleScope.launch {
                                    settingsRepository.updateLayoutType(layoutType)
                                }
                            },
                            onShowTasksChange = { show ->
                                lifecycleScope.launch {
                                    settingsRepository.updateShowTasks(show)
                                }
                            },
                            onShowQuickAddChange = { show ->
                                lifecycleScope.launch {
                                    settingsRepository.updateShowQuickAdd(show)
                                }
                            },
                            onWallCalendarModeChange = { enabled ->
                                lifecycleScope.launch {
                                    settingsRepository.updateWallCalendarMode(enabled)
                                }
                            },
                            onEInkModeChange = { enabled ->
                                lifecycleScope.launch {
                                    settingsRepository.updateEInkRefreshMode(enabled)
                                }
                            },
                            isEInkDetected = DisplayDetection.isEinkDisplay(applicationContext),
                            // Weather
                            onShowWeatherChange = { enabled ->
                                viewModel.updateShowWeather(enabled)
                            },
                            onWeatherTempUnitChange = { unit ->
                                viewModel.updateWeatherTempUnit(unit)
                            },
                            onWeatherLocationSet = { lat, lon, name ->
                                viewModel.updateWeatherLocation(
                                    mode = com.homedashboard.app.data.weather.WeatherLocationMode.MANUAL,
                                    lat = lat,
                                    lon = lon,
                                    name = name
                                )
                            },
                            // Always-on display
                            hasFrontlight = DisplayDetection.hasFrontlight(applicationContext),
                            onAlwaysOnDisplayChange = { enabled ->
                                lifecycleScope.launch {
                                    settingsRepository.updateAlwaysOnDisplay(enabled)
                                }
                            },
                            onNightDimEnabledChange = { enabled ->
                                lifecycleScope.launch {
                                    settingsRepository.updateNightDimEnabled(enabled)
                                }
                            },
                            onNightDimStartHourChange = { hour ->
                                lifecycleScope.launch {
                                    settingsRepository.updateNightDimStartHour(hour)
                                }
                            },
                            onNightDimEndHourChange = { hour ->
                                lifecycleScope.launch {
                                    settingsRepository.updateNightDimEndHour(hour)
                                }
                            },
                            onNightDimBrightnessChange = { brightness ->
                                lifecycleScope.launch {
                                    settingsRepository.updateNightDimBrightness(brightness)
                                }
                            },
                            // Google Calendar sync
                            authState = authState,
                            syncState = syncState,
                            onGoogleSignIn = {
                                viewModel.getGoogleSignInIntent()?.let { intent ->
                                    signInLauncher.launch(intent)
                                }
                            },
                            onGoogleSignOut = {
                                viewModel.signOut()
                                // Cancel background sync when signed out
                                SyncWorker.cancelSync(applicationContext)
                            },
                            onSyncNow = {
                                viewModel.syncNow()
                            },
                            // iCloud Calendar sync
                            iCloudAuthState = iCloudAuthState,
                            onICloudConnect = { email, password ->
                                viewModel.connectICloud(email, password)
                            },
                            onICloudDisconnect = {
                                viewModel.disconnectICloud()
                            },
                            iCloudConnectError = iCloudConnectError,
                            // Calendar selection
                            calendars = allCalendars,
                            defaultCalendarId = calendarSettings.defaultCalendarId,
                            onCalendarVisibilityChange = { calendarId, visible ->
                                viewModel.setCalendarVisible(calendarId, visible)
                            },
                            onDefaultCalendarChange = { calendarId ->
                                viewModel.setDefaultCalendarId(calendarId)
                            }
                        )
                    } else {
                        CalendarScreen(
                            settings = settings,
                            eventsMap = eventsByDate,
                            tasks = tasks,
                            weatherByDate = weatherByDate,
                            rainForecast = rainForecast,
                            // Inline handwriting - write directly in day cells
                            recognizer = handwritingRecognizer,
                            parser = entityExtractionParser,
                            onInlineEventCreated = { parsedEvent ->
                                viewModel.createEventFromParsedInput(
                                    title = parsedEvent.title,
                                    date = parsedEvent.date,
                                    startTime = parsedEvent.startTime,
                                    endTime = parsedEvent.endTime,
                                    isAllDay = parsedEvent.isAllDay,
                                    location = parsedEvent.location
                                )
                            },
                            onHandwritingUsed = {
                                viewModel.markHandwritingUsed()
                            },
                            onSettingsClick = {
                                showSettings = true
                            },
                            onLayoutChange = { layoutType ->
                                lifecycleScope.launch {
                                    settingsRepository.updateLayoutType(layoutType)
                                }
                            },
                            onDayClick = { date ->
                                // Open add event dialog for this date
                                viewModel.openAddEventDialog(date)
                            },
                            onAddEventClick = { date ->
                                viewModel.openAddEventDialog(date)
                            },
                            onWriteClick = { /* no-op: inline writing handles this */ },
                            onAddTaskClick = {
                                viewModel.openAddTaskDialog()
                            },
                            onTaskTextRecognized = { text ->
                                viewModel.addTask(title = text)
                            },
                            onHandwritingInput = { date, text ->
                                // Quick add event from handwriting (legacy)
                                viewModel.addEvent(
                                    title = text,
                                    date = date,
                                    startTime = null,
                                    endTime = null,
                                    isAllDay = true
                                )
                            },
                            onEventClick = { event ->
                                viewModel.openEventDetail(event)
                            },
                            onTaskToggle = { task ->
                                viewModel.toggleTaskCompletion(task)
                            },
                            onTaskClick = { task ->
                                viewModel.openTaskDetail(task)
                            },
                            onQuickAddInput = { text ->
                                // Quick add task from input
                                viewModel.addTask(title = text)
                            },
                            onResetData = if (BuildConfig.DEBUG) {
                                { viewModel.resetLocalData() }
                            } else null
                        )
                    }

                    // Add Event Dialog
                    showAddEventForDate?.let { date ->
                        AddEventDialog(
                            date = date,
                            onDismiss = { viewModel.closeAddEventDialog() },
                            onConfirm = { title, eventDate, startTime, endTime, isAllDay, description, location ->
                                viewModel.addEvent(
                                    title = title,
                                    date = eventDate,
                                    startTime = startTime,
                                    endTime = endTime,
                                    isAllDay = isAllDay,
                                    description = description,
                                    location = location
                                )
                            }
                        )
                    }

                    // Add Task Dialog
                    if (showAddTask) {
                        AddTaskDialog(
                            onDismiss = { viewModel.closeAddTaskDialog() },
                            onConfirm = { title, dueDate, priority, description ->
                                viewModel.addTask(
                                    title = title,
                                    dueDate = dueDate,
                                    priority = priority,
                                    description = description
                                )
                            }
                        )
                    }

                    // Event Detail Dialog (view/edit/delete)
                    showEventDetail?.let { eventDetails ->
                        EventDetailDialog(
                            event = eventDetails,
                            onDismiss = { viewModel.closeEventDetail() },
                            onUpdate = { eventId, title, date, startTime, endTime, isAllDay, description, location ->
                                viewModel.updateEvent(
                                    eventId = eventId,
                                    title = title,
                                    date = date,
                                    startTime = startTime,
                                    endTime = endTime,
                                    isAllDay = isAllDay,
                                    description = description,
                                    location = location
                                )
                            },
                            onDelete = { eventId ->
                                viewModel.deleteEvent(eventId)
                            }
                        )
                    }

                    // Task Detail Dialog (view/edit/delete)
                    showTaskDetail?.let { taskDetails ->
                        TaskDetailDialog(
                            task = taskDetails,
                            onDismiss = { viewModel.closeTaskDetail() },
                            onUpdate = { taskId, title, dueDate, priority, description ->
                                viewModel.updateTask(
                                    taskId = taskId,
                                    title = title,
                                    dueDate = dueDate,
                                    priority = priority,
                                    description = description
                                )
                            },
                            onToggleCompletion = { taskId ->
                                viewModel.toggleTaskCompletionById(taskId)
                            },
                            onDelete = { taskId ->
                                viewModel.deleteTask(taskId)
                            }
                        )
                    }
                }
            }
        }
    }

    /**
     * Apply night dimming by adjusting window brightness based on current time and settings.
     */
    private fun applyNightDimming(settings: CalendarSettings) {
        if (!settings.nightDimEnabled) {
            // Reset to system default brightness
            val lp = window.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = lp
            return
        }

        val currentHour = java.time.LocalTime.now().hour
        val isDimTime = if (settings.nightDimStartHour > settings.nightDimEndHour) {
            // Wraps midnight, e.g. 22:00 - 07:00
            currentHour >= settings.nightDimStartHour || currentHour < settings.nightDimEndHour
        } else {
            // Same day, e.g. 20:00 - 23:00
            currentHour >= settings.nightDimStartHour && currentHour < settings.nightDimEndHour
        }

        val lp = window.attributes
        lp.screenBrightness = if (isDimTime) {
            settings.nightDimBrightness
        } else {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        window.attributes = lp
    }

    private fun enableFullscreen() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        // Hide both status bar and navigation bar
        windowInsetsController.apply {
            hide(WindowInsetsCompat.Type.statusBars())
            hide(WindowInsetsCompat.Type.navigationBars())

            // Use BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE so bars can be
            // temporarily revealed with a swipe, then auto-hide again
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Draw behind system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Re-enable fullscreen when window regains focus
        if (hasFocus) {
            enableFullscreen()
        }
    }

    /**
     * Build OkHttp client for Google Calendar API calls.
     */
    private fun buildOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
