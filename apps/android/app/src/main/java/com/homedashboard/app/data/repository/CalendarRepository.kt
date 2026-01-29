package com.homedashboard.app.data.repository

import com.homedashboard.app.data.model.Calendar
import com.homedashboard.app.data.model.CalendarEvent
import com.homedashboard.app.sync.PendingSyncInfo
import com.homedashboard.app.sync.SyncConfig
import com.homedashboard.app.sync.SyncResult
import com.homedashboard.app.sync.SyncState
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Repository interface for calendar data operations.
 * Abstracts the coordination between local storage (Room) and remote sources (Google Calendar).
 */
interface CalendarRepository {

    // ========================================================================
    // CALENDAR OPERATIONS
    // ========================================================================

    /**
     * Get all visible calendars.
     */
    fun getVisibleCalendars(): Flow<List<Calendar>>

    /**
     * Get all calendars (including hidden ones).
     */
    fun getAllCalendars(): Flow<List<Calendar>>

    /**
     * Update calendar visibility.
     */
    suspend fun setCalendarVisible(calendarId: String, visible: Boolean)

    /**
     * Refresh calendar list from remote.
     */
    suspend fun refreshCalendars(): Result<List<Calendar>>

    // ========================================================================
    // EVENT OPERATIONS (LOCAL)
    // ========================================================================

    /**
     * Get events for a date range.
     */
    fun getEventsInRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<CalendarEvent>>

    /**
     * Get a single event by ID.
     */
    suspend fun getEventById(eventId: String): CalendarEvent?

    /**
     * Insert a new event locally.
     * Sets needsSync=true for remote calendars.
     */
    suspend fun insertEvent(event: CalendarEvent)

    /**
     * Update an existing event locally.
     * Sets needsSync=true for remote calendars.
     */
    suspend fun updateEvent(event: CalendarEvent)

    /**
     * Delete an event (soft delete).
     * Sets needsSync=true for remote calendars.
     */
    suspend fun deleteEvent(eventId: String)

    // ========================================================================
    // SYNC OPERATIONS
    // ========================================================================

    /**
     * Observe sync state changes.
     */
    val syncState: Flow<SyncState>

    /**
     * Get info about events pending sync.
     */
    suspend fun getPendingSyncInfo(): PendingSyncInfo

    /**
     * Perform a full two-way sync.
     *
     * Flow:
     * 1. Download changes from Google Calendar (incremental if possible)
     * 2. Merge remote changes with local (conflict resolution)
     * 3. Upload local changes to Google Calendar
     *
     * @param forceFullSync If true, ignores sync tokens and downloads all events
     * @return Sync result with statistics
     */
    suspend fun performSync(forceFullSync: Boolean = false): Result<SyncResult>

    /**
     * Sync a specific calendar only.
     */
    suspend fun syncCalendar(calendarId: String): Result<SyncResult>

    /**
     * Download events from remote (no upload).
     */
    suspend fun downloadChanges(): Result<SyncResult>

    /**
     * Upload local changes to remote.
     */
    suspend fun uploadChanges(): Result<SyncResult>

    /**
     * Cancel any ongoing sync operation.
     */
    fun cancelSync()

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    /**
     * Get current sync configuration.
     */
    suspend fun getSyncConfig(): SyncConfig

    /**
     * Update sync configuration.
     */
    suspend fun updateSyncConfig(config: SyncConfig)
}
