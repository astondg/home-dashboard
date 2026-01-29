package com.homedashboard.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.homedashboard.app.auth.AuthState
import com.homedashboard.app.auth.GoogleAuthManager
import com.homedashboard.app.auth.ICloudAuthManager
import com.homedashboard.app.auth.ICloudAuthState
import com.homedashboard.app.calendar.CalendarEventUi
import com.homedashboard.app.calendar.components.TaskPriority
import com.homedashboard.app.calendar.components.TaskUi
import com.homedashboard.app.calendar.dialogs.EventDetails
import com.homedashboard.app.calendar.dialogs.TaskDetails
import com.homedashboard.app.data.local.CalendarDao
import com.homedashboard.app.data.local.TaskDao
import com.homedashboard.app.data.model.CalendarEvent
import com.homedashboard.app.data.model.CalendarProvider
import com.homedashboard.app.data.model.Task
import com.homedashboard.app.sync.SyncManager
import com.homedashboard.app.sync.SyncState
import com.homedashboard.app.sync.SyncStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * ViewModel for managing calendar events and tasks.
 * Provides StateFlows for UI observation and methods for CRUD operations.
 * Manages Google Calendar and iCloud Calendar authentication and sync state.
 */
class CalendarViewModel(
    private val calendarDao: CalendarDao,
    private val taskDao: TaskDao,
    private val authManager: GoogleAuthManager? = null,
    private val iCloudAuthManager: ICloudAuthManager? = null,
    private val syncManager: SyncManager? = null
) : ViewModel() {

    // Time formatter for display
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d")

    // Events grouped by date for efficient UI rendering
    private val _eventsFlow = calendarDao.getAllEvents()
    val eventsByDate: StateFlow<Map<LocalDate, List<CalendarEventUi>>> = _eventsFlow
        .map { events ->
            events
                .filter { !it.isDeleted }
                .groupBy { it.startTime.toLocalDate() }
                .mapValues { (_, dayEvents) ->
                    dayEvents.map { event ->
                        CalendarEventUi(
                            id = event.id,
                            title = event.title,
                            startTime = if (event.isAllDay) null else event.startTime.format(timeFormatter),
                            isAllDay = event.isAllDay,
                            color = getCalendarColor(event.calendarId)
                        )
                    }
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    // Tasks for display
    private val _tasksFlow = taskDao.getAllTasks()
    val tasks: StateFlow<List<TaskUi>> = _tasksFlow
        .map { tasks ->
            tasks.map { task ->
                TaskUi(
                    id = task.id,
                    title = task.title,
                    isCompleted = task.isCompleted,
                    dueDate = task.dueDate?.format(dateFormatter),
                    priority = when (task.priority) {
                        com.homedashboard.app.data.model.TaskPriority.LOW -> TaskPriority.LOW
                        com.homedashboard.app.data.model.TaskPriority.NORMAL -> TaskPriority.NORMAL
                        com.homedashboard.app.data.model.TaskPriority.HIGH -> TaskPriority.HIGH
                    }
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Dialog state
    private val _showAddEventDialog = MutableStateFlow<LocalDate?>(null)
    val showAddEventDialog: StateFlow<LocalDate?> = _showAddEventDialog.asStateFlow()

    private val _showAddTaskDialog = MutableStateFlow(false)
    val showAddTaskDialog: StateFlow<Boolean> = _showAddTaskDialog.asStateFlow()

    private val _showHandwritingDialog = MutableStateFlow<LocalDate?>(null)
    val showHandwritingDialog: StateFlow<LocalDate?> = _showHandwritingDialog.asStateFlow()

    // Full detail dialogs (with all fields for viewing/editing)
    private val _showEventDetailDialog = MutableStateFlow<EventDetails?>(null)
    val showEventDetailDialog: StateFlow<EventDetails?> = _showEventDetailDialog.asStateFlow()

    private val _showTaskDetailDialog = MutableStateFlow<TaskDetails?>(null)
    val showTaskDetailDialog: StateFlow<TaskDetails?> = _showTaskDetailDialog.asStateFlow()

    // ==================== Google Calendar Auth & Sync State ====================

    // Auth state from GoogleAuthManager
    val authState: StateFlow<AuthState> = authManager?.authState
        ?: MutableStateFlow<AuthState>(AuthState.NotAuthenticated).asStateFlow()

    // Sync state - observe from SyncManager or use local state
    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    /**
     * Trigger Google Sign-In flow.
     * Returns the sign-in intent that the Activity should launch.
     */
    fun getGoogleSignInIntent() = authManager?.getSignInIntent()

    /**
     * Handle the sign-in result from the Activity.
     * Returns true if sign-in was successful (for Activity to schedule background sync).
     */
    fun handleSignInResult(data: android.content.Intent?, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val result = authManager?.handleSignInResult(data)
            if (result?.isSuccess == true) {
                onSuccess()
            }
        }
    }

    /**
     * Sign out from Google.
     */
    fun signOut() {
        viewModelScope.launch {
            authManager?.signOut()
            _syncState.value = SyncState() // Reset sync state
        }
    }

    /**
     * Trigger an immediate sync with all connected calendar providers.
     */
    fun syncNow() {
        // Check if any provider is authenticated
        val googleAuthenticated = authState.value is AuthState.Authenticated
        val iCloudAuthenticated = iCloudAuthState.value is ICloudAuthState.Authenticated

        if (!googleAuthenticated && !iCloudAuthenticated) {
            return
        }

        viewModelScope.launch {
            _syncState.value = _syncState.value.copy(
                status = SyncStatus.SYNCING,
                message = "Starting sync...",
                progress = 0f
            )

            try {
                val result = syncManager?.performFullSync()
                result?.fold(
                    onSuccess = { syncResult ->
                        _syncState.value = _syncState.value.copy(
                            status = if (syncResult.hasErrors) SyncStatus.PARTIAL_SUCCESS else SyncStatus.SUCCESS,
                            lastSyncTime = ZonedDateTime.now(),
                            message = "Synced ${syncResult.totalChanges} changes",
                            progress = 1f,
                            error = syncResult.errors.firstOrNull()?.message
                        )
                    },
                    onFailure = { error ->
                        _syncState.value = _syncState.value.copy(
                            status = SyncStatus.ERROR,
                            error = error.message ?: "Sync failed",
                            progress = 0f
                        )
                    }
                )
            } catch (e: Exception) {
                _syncState.value = _syncState.value.copy(
                    status = SyncStatus.ERROR,
                    error = e.message ?: "Unexpected error during sync",
                    progress = 0f
                )
            }
        }
    }

    // ==================== iCloud Calendar Auth ====================

    // iCloud auth state from ICloudAuthManager
    val iCloudAuthState: StateFlow<ICloudAuthState> = iCloudAuthManager?.authState
        ?: MutableStateFlow<ICloudAuthState>(ICloudAuthState.NotAuthenticated).asStateFlow()

    // iCloud connection error for UI display
    private val _iCloudConnectError = MutableStateFlow<String?>(null)
    val iCloudConnectError: StateFlow<String?> = _iCloudConnectError.asStateFlow()

    init {
        // Check iCloud auth state on initialization
        viewModelScope.launch {
            iCloudAuthManager?.checkAuthState()
        }
    }

    /**
     * Connect to iCloud using email and app-specific password.
     */
    fun connectICloud(email: String, appPassword: String) {
        viewModelScope.launch {
            _iCloudConnectError.value = null

            val result = iCloudAuthManager?.authenticate(email, appPassword)
            result?.fold(
                onSuccess = { authResult ->
                    // Successfully connected - trigger a sync
                    _iCloudConnectError.value = null
                    syncNow()
                },
                onFailure = { error ->
                    _iCloudConnectError.value = error.message ?: "Failed to connect to iCloud"
                }
            )
        }
    }

    /**
     * Disconnect from iCloud (sign out).
     */
    fun disconnectICloud() {
        viewModelScope.launch {
            iCloudAuthManager?.signOut()
            _iCloudConnectError.value = null
        }
    }

    // ==================== Event Operations ====================

    fun openAddEventDialog(date: LocalDate) {
        _showAddEventDialog.value = date
    }

    fun closeAddEventDialog() {
        _showAddEventDialog.value = null
    }

    fun addEvent(
        title: String,
        date: LocalDate,
        startTime: LocalTime?,
        endTime: LocalTime?,
        isAllDay: Boolean,
        description: String? = null,
        location: String? = null
    ) {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val startDateTime = if (isAllDay) {
                date.atStartOfDay(zone)
            } else {
                (startTime ?: LocalTime.of(9, 0)).atDate(date).atZone(zone)
            }
            val endDateTime = if (isAllDay) {
                date.plusDays(1).atStartOfDay(zone)
            } else {
                (endTime ?: startTime?.plusHours(1) ?: LocalTime.of(10, 0)).atDate(date).atZone(zone)
            }

            val event = CalendarEvent(
                id = UUID.randomUUID().toString(),
                title = title,
                description = description,
                location = location,
                startTime = startDateTime,
                endTime = endDateTime,
                isAllDay = isAllDay,
                calendarId = "local_default",
                calendarName = "Local",
                providerType = CalendarProvider.LOCAL
            )

            calendarDao.insertEvent(event)
            closeAddEventDialog()
        }
    }

    fun openEventDetail(event: CalendarEventUi) {
        viewModelScope.launch {
            // Fetch full event details from database
            val fullEvent = calendarDao.getEventById(event.id)
            if (fullEvent != null) {
                _showEventDetailDialog.value = EventDetails(
                    id = fullEvent.id,
                    title = fullEvent.title,
                    description = fullEvent.description,
                    location = fullEvent.location,
                    date = fullEvent.startTime.toLocalDate(),
                    startTime = if (fullEvent.isAllDay) null else fullEvent.startTime.toLocalTime(),
                    endTime = if (fullEvent.isAllDay) null else fullEvent.endTime.toLocalTime(),
                    isAllDay = fullEvent.isAllDay,
                    calendarName = fullEvent.calendarName
                )
            }
        }
    }

    fun closeEventDetail() {
        _showEventDetailDialog.value = null
    }

    fun updateEvent(
        eventId: String,
        title: String,
        date: LocalDate,
        startTime: LocalTime?,
        endTime: LocalTime?,
        isAllDay: Boolean,
        description: String? = null,
        location: String? = null
    ) {
        viewModelScope.launch {
            val existingEvent = calendarDao.getEventById(eventId)
            if (existingEvent != null) {
                val zone = ZoneId.systemDefault()
                val startDateTime = if (isAllDay) {
                    date.atStartOfDay(zone)
                } else {
                    (startTime ?: LocalTime.of(9, 0)).atDate(date).atZone(zone)
                }
                val endDateTime = if (isAllDay) {
                    date.plusDays(1).atStartOfDay(zone)
                } else {
                    (endTime ?: startTime?.plusHours(1) ?: LocalTime.of(10, 0)).atDate(date).atZone(zone)
                }

                val updatedEvent = existingEvent.copy(
                    title = title,
                    description = description,
                    location = location,
                    startTime = startDateTime,
                    endTime = endDateTime,
                    isAllDay = isAllDay,
                    updatedAt = ZonedDateTime.now(),
                    needsSync = existingEvent.providerType != CalendarProvider.LOCAL
                )

                calendarDao.updateEvent(updatedEvent)
                closeEventDetail()
            }
        }
    }

    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            calendarDao.softDeleteEvent(eventId)
            closeEventDetail()
        }
    }

    // ==================== Handwriting Operations ====================

    fun openHandwritingDialog(date: LocalDate) {
        _showHandwritingDialog.value = date
    }

    fun closeHandwritingDialog() {
        _showHandwritingDialog.value = null
    }

    /**
     * Create an event from a parsed handwriting result.
     */
    fun createEventFromParsedInput(
        title: String,
        date: LocalDate,
        startTime: LocalTime?,
        endTime: LocalTime?,
        isAllDay: Boolean,
        location: String?
    ) {
        addEvent(
            title = title,
            date = date,
            startTime = startTime,
            endTime = endTime,
            isAllDay = isAllDay,
            location = location
        )
        closeHandwritingDialog()
    }

    // ==================== Task Operations ====================

    fun openAddTaskDialog() {
        _showAddTaskDialog.value = true
    }

    fun closeAddTaskDialog() {
        _showAddTaskDialog.value = false
    }

    fun addTask(
        title: String,
        dueDate: LocalDate? = null,
        priority: TaskPriority = TaskPriority.NORMAL,
        description: String? = null
    ) {
        viewModelScope.launch {
            val task = Task(
                id = UUID.randomUUID().toString(),
                title = title,
                description = description,
                dueDate = dueDate,
                priority = when (priority) {
                    TaskPriority.LOW -> com.homedashboard.app.data.model.TaskPriority.LOW
                    TaskPriority.NORMAL -> com.homedashboard.app.data.model.TaskPriority.NORMAL
                    TaskPriority.HIGH -> com.homedashboard.app.data.model.TaskPriority.HIGH
                }
            )

            taskDao.insertTask(task)
            closeAddTaskDialog()
        }
    }

    fun toggleTaskCompletion(task: TaskUi) {
        viewModelScope.launch {
            val newIsCompleted = !task.isCompleted
            taskDao.updateTaskCompletion(
                id = task.id,
                isCompleted = newIsCompleted,
                completedAt = if (newIsCompleted) ZonedDateTime.now() else null
            )
        }
    }

    fun openTaskDetail(task: TaskUi) {
        viewModelScope.launch {
            // Fetch full task details from database
            val fullTask = taskDao.getTaskById(task.id)
            if (fullTask != null) {
                _showTaskDetailDialog.value = TaskDetails(
                    id = fullTask.id,
                    title = fullTask.title,
                    description = fullTask.description,
                    dueDate = fullTask.dueDate,
                    priority = when (fullTask.priority) {
                        com.homedashboard.app.data.model.TaskPriority.LOW -> TaskPriority.LOW
                        com.homedashboard.app.data.model.TaskPriority.NORMAL -> TaskPriority.NORMAL
                        com.homedashboard.app.data.model.TaskPriority.HIGH -> TaskPriority.HIGH
                    },
                    isCompleted = fullTask.isCompleted
                )
            }
        }
    }

    fun closeTaskDetail() {
        _showTaskDetailDialog.value = null
    }

    fun updateTask(
        taskId: String,
        title: String,
        dueDate: LocalDate? = null,
        priority: TaskPriority = TaskPriority.NORMAL,
        description: String? = null
    ) {
        viewModelScope.launch {
            val existingTask = taskDao.getTaskById(taskId)
            if (existingTask != null) {
                val updatedTask = existingTask.copy(
                    title = title,
                    description = description,
                    dueDate = dueDate,
                    priority = when (priority) {
                        TaskPriority.LOW -> com.homedashboard.app.data.model.TaskPriority.LOW
                        TaskPriority.NORMAL -> com.homedashboard.app.data.model.TaskPriority.NORMAL
                        TaskPriority.HIGH -> com.homedashboard.app.data.model.TaskPriority.HIGH
                    },
                    updatedAt = ZonedDateTime.now()
                )

                taskDao.updateTask(updatedTask)
                closeTaskDetail()
            }
        }
    }

    fun toggleTaskCompletionById(taskId: String) {
        viewModelScope.launch {
            val task = taskDao.getTaskById(taskId)
            if (task != null) {
                val newIsCompleted = !task.isCompleted
                taskDao.updateTaskCompletion(
                    id = taskId,
                    isCompleted = newIsCompleted,
                    completedAt = if (newIsCompleted) ZonedDateTime.now() else null
                )
                // Refresh the detail dialog if it's still open
                if (_showTaskDetailDialog.value?.id == taskId) {
                    _showTaskDetailDialog.value = _showTaskDetailDialog.value?.copy(
                        isCompleted = newIsCompleted
                    )
                }
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            taskDao.softDeleteTask(taskId)
            closeTaskDetail()
        }
    }

    // ==================== Helpers ====================

    private fun getCalendarColor(calendarId: String): Long {
        // Default colors for different calendars
        return when (calendarId) {
            "local_default" -> 0xFF4285F4 // Google Blue
            else -> 0xFF34A853 // Green
        }
    }

    // ==================== Factory ====================

    class Factory(
        private val calendarDao: CalendarDao,
        private val taskDao: TaskDao,
        private val authManager: GoogleAuthManager? = null,
        private val iCloudAuthManager: ICloudAuthManager? = null,
        private val syncManager: SyncManager? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
                return CalendarViewModel(
                    calendarDao,
                    taskDao,
                    authManager,
                    iCloudAuthManager,
                    syncManager
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
