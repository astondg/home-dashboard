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
import com.homedashboard.app.handwriting.HandwritingInputDialog
import com.homedashboard.app.handwriting.HandwritingRecognizer
import com.homedashboard.app.handwriting.NaturalLanguageParser
import com.homedashboard.app.settings.CalendarLayoutType
import com.homedashboard.app.settings.CalendarSettings
import com.homedashboard.app.settings.SettingsRepository
import com.homedashboard.app.sync.ICloudSyncProvider
import com.homedashboard.app.sync.SyncManager
import com.homedashboard.app.sync.SyncWorker
import com.homedashboard.app.ui.theme.HomeDashboardTheme
import com.homedashboard.app.viewmodel.CalendarViewModel
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
    private val naturalLanguageParser = NaturalLanguageParser()

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

        // Keep screen on (important for wall-mounted display)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize repositories and database
        settingsRepository = SettingsRepository(applicationContext)
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
                syncManager = syncManager
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

        // Initialize handwriting recognizer
        handwritingRecognizer = HandwritingRecognizer(applicationContext)
        lifecycleScope.launch {
            handwritingRecognizer.initialize()
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
            val showHandwritingForDate by viewModel.showHandwritingDialog.collectAsState()
            val showEventDetail by viewModel.showEventDetailDialog.collectAsState()
            val showTaskDetail by viewModel.showTaskDetailDialog.collectAsState()

            // Collect Google Calendar auth and sync state
            val authState by viewModel.authState.collectAsState()
            val syncState by viewModel.syncState.collectAsState()

            // Collect iCloud Calendar auth state
            val iCloudAuthState by viewModel.iCloudAuthState.collectAsState()
            val iCloudConnectError by viewModel.iCloudConnectError.collectAsState()

            // Track if settings screen should be shown
            var showSettings by remember { mutableStateOf(false) }

            HomeDashboardTheme(
                // Use e-ink mode based on settings
                eInkMode = settings.eInkRefreshMode
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
                            onEInkModeChange = { enabled ->
                                lifecycleScope.launch {
                                    settingsRepository.updateEInkRefreshMode(enabled)
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
                            iCloudConnectError = iCloudConnectError
                        )
                    } else {
                        CalendarScreen(
                            settings = settings,
                            eventsMap = eventsByDate,
                            tasks = tasks,
                            // Inline handwriting - write directly in day cells
                            recognizer = handwritingRecognizer,
                            parser = naturalLanguageParser,
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
                            onWriteClick = { date ->
                                // Fallback: open handwriting input dialog
                                viewModel.openHandwritingDialog(date)
                            },
                            onAddTaskClick = {
                                viewModel.openAddTaskDialog()
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
                            }
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

                    // Handwriting Input Dialog
                    showHandwritingForDate?.let { date ->
                        HandwritingInputDialog(
                            date = date,
                            recognizer = handwritingRecognizer,
                            parser = naturalLanguageParser,
                            onDismiss = { viewModel.closeHandwritingDialog() },
                            onEventCreated = { parsedEvent ->
                                viewModel.createEventFromParsedInput(
                                    title = parsedEvent.title,
                                    date = parsedEvent.date,
                                    startTime = parsedEvent.startTime,
                                    endTime = parsedEvent.endTime,
                                    isAllDay = parsedEvent.isAllDay,
                                    location = parsedEvent.location
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
}
