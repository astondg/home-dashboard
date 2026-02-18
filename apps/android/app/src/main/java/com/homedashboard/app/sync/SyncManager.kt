package com.homedashboard.app.sync

import android.util.Log
import com.homedashboard.app.auth.TokenStorage
import com.homedashboard.app.data.local.CalendarDao
import com.homedashboard.app.data.model.CalendarEvent
import com.homedashboard.app.data.model.CalendarProvider
import com.homedashboard.app.data.remote.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Orchestrates the sync flow between local database and calendar providers.
 * Supports multiple providers: Google Calendar and iCloud Calendar.
 * Handles incremental sync with syncToken, conflict resolution, and error recovery.
 */
class SyncManager(
    private val calendarDao: CalendarDao,
    private val googleService: GoogleCalendarService?,
    private val eventMapper: GoogleEventMapper?,
    private val tokenStorage: TokenStorage,
    private val iCloudSyncProvider: ICloudSyncProvider? = null,
    private val config: SyncConfig = SyncConfig()
) {
    companion object {
        private const val TAG = "SyncManager"
        // Process-wide lock so multiple SyncManager instances (foreground + SyncWorker) don't overlap
        private val isSyncing = AtomicBoolean(false)
    }

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private var syncJob: Job? = null

    /**
     * Perform a full two-way sync across all configured providers.
     *
     * Flow:
     * 1. Sync Google Calendar (if configured)
     * 2. Sync iCloud Calendar (if configured)
     * 3. Aggregate results from all providers
     */
    suspend fun performFullSync(forceFullSync: Boolean = false): Result<SyncResult> {
        if (!isSyncing.compareAndSet(false, true)) {
            Log.w(TAG, "Sync already in progress")
            return Result.failure(Exception("Sync already in progress"))
        }

        val startTime = System.currentTimeMillis()
        val errors = mutableListOf<SyncError>()

        var eventsDownloaded = 0
        var eventsInserted = 0
        var eventsUpdated = 0
        var eventsDeleted = 0
        var eventsUploaded = 0
        var conflictsResolved = 0
        var wasIncremental = false

        try {
            // Check which providers are configured
            val hasGoogle = googleService != null && tokenStorage.hasTokens()
            val hasICloud = iCloudSyncProvider != null && tokenStorage.hasICloudCredentials()
            Log.d(TAG, "Sync providers: Google=$hasGoogle, iCloud=$hasICloud (provider=${iCloudSyncProvider != null}, creds=${tokenStorage.hasICloudCredentials()})")

            if (!hasGoogle && !hasICloud) {
                Log.w(TAG, "No calendar providers configured")
                return finishSync(startTime, true, errors = errors)
            }

            // Calculate progress weights based on active providers
            val totalProviders = listOf(hasGoogle, hasICloud).count { it }
            val progressPerProvider = 0.9f / totalProviders
            var currentProgress = 0.05f

            // Sync Google Calendar
            if (hasGoogle && googleService != null && eventMapper != null) {
                updateState(SyncStatus.SYNCING, SyncPhase.FETCHING_CALENDARS, currentProgress, "Syncing Google Calendar...")

                val googleResult = syncGoogleCalendar(forceFullSync) { phase, progress, message ->
                    val adjustedProgress = currentProgress + (progress * progressPerProvider)
                    updateState(SyncStatus.SYNCING, phase, adjustedProgress, message)
                }

                googleResult.fold(
                    onSuccess = { result ->
                        eventsDownloaded += result.eventsDownloaded
                        eventsInserted += result.eventsInserted
                        eventsUpdated += result.eventsUpdated
                        eventsDeleted += result.eventsDeleted
                        eventsUploaded += result.eventsUploaded
                        wasIncremental = wasIncremental || result.wasIncremental
                        errors.addAll(result.errors.map { SyncError(SyncErrorType.API_ERROR, it.message, calendarId = it.calendarId) })
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Google Calendar sync failed", error)
                        errors.add(SyncError(SyncErrorType.API_ERROR, "Google sync failed: ${error.message}"))
                    }
                )

                currentProgress += progressPerProvider
            }

            // Sync iCloud Calendar
            if (hasICloud && iCloudSyncProvider != null) {
                updateState(SyncStatus.SYNCING, SyncPhase.FETCHING_CALENDARS, currentProgress, "Syncing iCloud Calendar...")

                val iCloudResult = iCloudSyncProvider.performSync(forceFullSync) { progress, message ->
                    val adjustedProgress = currentProgress + (progress * progressPerProvider)
                    updateState(SyncStatus.SYNCING, SyncPhase.DOWNLOADING_EVENTS, adjustedProgress, message)
                }

                iCloudResult.fold(
                    onSuccess = { result ->
                        eventsDownloaded += result.eventsDownloaded
                        eventsInserted += result.eventsInserted
                        eventsUpdated += result.eventsUpdated
                        eventsDeleted += result.eventsDeleted
                        eventsUploaded += result.eventsUploaded
                        result.errors.forEach { syncError ->
                            errors.add(SyncError(
                                type = SyncErrorType.API_ERROR,
                                message = syncError.message,
                                calendarId = syncError.calendarId
                            ))
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "iCloud Calendar sync failed", error)
                        errors.add(SyncError(SyncErrorType.API_ERROR, "iCloud sync failed: ${error.message}"))
                    }
                )

                currentProgress += progressPerProvider
            }

            // Finalize
            updateState(SyncStatus.SYNCING, SyncPhase.FINALIZING, 0.95f, "Finalizing...")
            tokenStorage.saveLastSyncTime(System.currentTimeMillis())

            return finishSync(
                startTime = startTime,
                success = errors.isEmpty() || errors.all { it.isRecoverable },
                eventsDownloaded = eventsDownloaded,
                eventsInserted = eventsInserted,
                eventsUpdated = eventsUpdated,
                eventsDeleted = eventsDeleted,
                eventsUploaded = eventsUploaded,
                conflictsResolved = conflictsResolved,
                errors = errors,
                wasIncremental = wasIncremental
            )

        } catch (e: CancellationException) {
            Log.d(TAG, "Sync cancelled")
            _syncState.value = SyncState(status = SyncStatus.IDLE, message = "Sync cancelled")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed with exception", e)
            errors.add(SyncError(SyncErrorType.UNKNOWN, e.message ?: "Unknown error"))
            return finishSync(startTime, false, errors = errors)
        } finally {
            isSyncing.set(false)
        }
    }

    /**
     * Sync Google Calendar only.
     */
    private suspend fun syncGoogleCalendar(
        forceFullSync: Boolean,
        onProgress: (SyncPhase, Float, String) -> Unit
    ): Result<SyncResult> {
        val errors = mutableListOf<SyncError>()
        var eventsDownloaded = 0
        var eventsInserted = 0
        var eventsUpdated = 0
        var eventsDeleted = 0
        var eventsUploaded = 0
        var wasIncremental = false

        val googleService = this.googleService ?: return Result.failure(Exception("Google service not configured"))
        val eventMapper = this.eventMapper ?: return Result.failure(Exception("Event mapper not configured"))

        try {
            // Step 1: Get list of calendars
            onProgress(SyncPhase.FETCHING_CALENDARS, 0.1f, "Fetching Google calendars...")

            val calendarsResult = googleService.listCalendars()
            val calendars = calendarsResult.getOrElse {
                errors.add(SyncError(SyncErrorType.API_ERROR, "Failed to fetch calendars: ${it.message}"))
                return Result.failure(it)
            }

            val calendarList = calendars.items ?: emptyList()
            Log.d(TAG, "Found ${calendarList.size} Google calendars")

            // Upsert Calendar entities to Room
            val accountEmail = tokenStorage.getAccountEmail() ?: "unknown"
            val remoteCalendarIds = mutableSetOf<String>()
            for (googleCal in calendarList) {
                remoteCalendarIds.add(googleCal.id)
                val existingCal = calendarDao.getCalendarById(googleCal.id)
                if (existingCal != null) {
                    // Update name/color/readOnly, preserve isVisible
                    val localCal = eventMapper.toLocalCalendar(googleCal, accountEmail)
                    calendarDao.updateCalendar(existingCal.copy(
                        name = localCal.name,
                        color = localCal.color,
                        isReadOnly = localCal.isReadOnly
                    ))
                } else {
                    // New calendar — insert with defaults from Google's selected field
                    calendarDao.insertCalendar(eventMapper.toLocalCalendar(googleCal, accountEmail))
                }
            }

            // Clean up calendars removed from remote
            val existingGoogleCals = calendarDao.getCalendarsByProvider(CalendarProvider.GOOGLE)
            for (localCal in existingGoogleCals) {
                if (localCal.id !in remoteCalendarIds) {
                    calendarDao.deleteCalendarById(localCal.id)
                }
            }

            // Filter to only visible calendars
            val visibleCalendarIds = calendarDao.getVisibleCalendarIds().toSet()

            // Step 2: Download events from each calendar (only visible ones)
            val visibleCalendarList = calendarList.filter { it.id in visibleCalendarIds }
            for ((index, calendar) in visibleCalendarList.withIndex()) {
                val progress = 0.2f + (0.4f * index / visibleCalendarList.size.coerceAtLeast(1))
                onProgress(SyncPhase.DOWNLOADING_EVENTS, progress, "Syncing ${calendar.summary ?: "calendar"}...")

                val downloadResult = downloadCalendarEvents(
                    calendarId = calendar.id,
                    calendarName = calendar.summary ?: "Calendar",
                    forceFullSync = forceFullSync
                )

                downloadResult.fold(
                    onSuccess = { result ->
                        eventsDownloaded += result.newEvents + result.updatedEvents
                        eventsInserted += result.newEvents
                        eventsUpdated += result.updatedEvents
                        eventsDeleted += result.deletedEvents
                        wasIncremental = result.nextSyncToken != null && !forceFullSync

                        result.nextSyncToken?.let { token ->
                            tokenStorage.saveSyncToken(calendar.id, token)
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to sync calendar ${calendar.id}", error)
                        errors.add(
                            SyncError(
                                type = if (error is SyncTokenExpiredException) {
                                    SyncErrorType.SYNC_TOKEN_EXPIRED
                                } else {
                                    SyncErrorType.API_ERROR
                                },
                                message = error.message ?: "Unknown error",
                                calendarId = calendar.id
                            )
                        )
                    }
                )
            }

            // Step 3: Upload local changes
            onProgress(SyncPhase.UPLOADING_CHANGES, 0.7f, "Uploading changes to Google...")

            val uploadResult = uploadLocalChanges()
            uploadResult.fold(
                onSuccess = { uploaded ->
                    eventsUploaded = uploaded
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to upload changes", error)
                    errors.add(SyncError(SyncErrorType.API_ERROR, "Upload failed: ${error.message}"))
                }
            )

            // Step 4: Delete remote events
            onProgress(SyncPhase.DELETING_REMOTE, 0.85f, "Syncing Google deletions...")

            val deleteResult = syncDeletions()
            deleteResult.fold(
                onSuccess = { deleted ->
                    eventsDeleted += deleted
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to sync deletions", error)
                    errors.add(SyncError(SyncErrorType.API_ERROR, "Delete sync failed: ${error.message}"))
                }
            )

            return Result.success(
                SyncResult(
                    success = errors.isEmpty(),
                    eventsDownloaded = eventsDownloaded,
                    eventsInserted = eventsInserted,
                    eventsUpdated = eventsUpdated,
                    eventsDeleted = eventsDeleted,
                    eventsUploaded = eventsUploaded,
                    conflictsResolved = 0,
                    errors = errors,
                    duration = 0,
                    wasIncremental = wasIncremental
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "Google Calendar sync failed", e)
            return Result.failure(e)
        }
    }

    /**
     * Download events from a specific Google calendar.
     */
    private suspend fun downloadCalendarEvents(
        calendarId: String,
        calendarName: String,
        forceFullSync: Boolean
    ): Result<CalendarSyncResult> {
        val googleService = this.googleService
            ?: return Result.failure(Exception("Google service not configured"))
        val eventMapper = this.eventMapper
            ?: return Result.failure(Exception("Event mapper not configured"))

        var newEvents = 0
        var updatedEvents = 0
        var deletedEvents = 0
        var nextSyncToken: String? = null
        val errors = mutableListOf<SyncError>()

        try {
            // Try incremental sync first
            val syncToken = if (forceFullSync) null else tokenStorage.getSyncToken(calendarId)
            var pageToken: String? = null

            // Time range for full sync
            val timeMin = Instant.now().minus(config.syncWindowDaysBack.toLong(), ChronoUnit.DAYS)
            val timeMax = Instant.now().plus(config.syncWindowDaysForward.toLong(), ChronoUnit.DAYS)

            // Get existing events for this calendar (for merge)
            val existingEvents = calendarDao.getEventsByCalendar(calendarId)
            val existingByRemoteId = existingEvents.associateBy { it.remoteId }

            do {
                val response = googleService.listEvents(
                    calendarId = calendarId,
                    syncToken = syncToken,
                    pageToken = pageToken,
                    timeMin = if (syncToken == null) timeMin else null,
                    timeMax = if (syncToken == null) timeMax else null
                )

                val eventsResponse = response.getOrElse { error ->
                    if (error is SyncTokenExpiredException) {
                        // Clear token and retry with full sync
                        tokenStorage.clearSyncToken(calendarId)
                        return downloadCalendarEvents(calendarId, calendarName, forceFullSync = true)
                    }
                    return Result.failure(error)
                }

                // Process each event
                for (googleEvent in eventsResponse.items ?: emptyList()) {
                    val existingEvent = existingByRemoteId[googleEvent.id]

                    if (googleEvent.isCancelled()) {
                        // Event was deleted remotely
                        if (existingEvent != null) {
                            calendarDao.hardDeleteEvent(existingEvent.id)
                            deletedEvents++
                        }
                    } else if (existingEvent != null) {
                        // Event exists locally - check for updates
                        if (eventMapper.isRemoteNewer(existingEvent, googleEvent)) {
                            val merged = eventMapper.mergeRemoteChanges(existingEvent, googleEvent, calendarName)
                            calendarDao.updateEvent(merged)
                            updatedEvents++
                        }
                        // If local is newer, needsSync should still be true (handled by upload)
                    } else {
                        // New event from remote
                        val localEvent = eventMapper.toLocalEvent(
                            googleEvent = googleEvent,
                            calendarId = calendarId,
                            calendarName = calendarName
                        )
                        calendarDao.insertEvent(localEvent)
                        newEvents++
                    }
                }

                pageToken = eventsResponse.nextPageToken
                nextSyncToken = eventsResponse.nextSyncToken

            } while (pageToken != null)

            return Result.success(
                CalendarSyncResult(
                    calendarId = calendarId,
                    calendarName = calendarName,
                    newEvents = newEvents,
                    updatedEvents = updatedEvents,
                    deletedEvents = deletedEvents,
                    nextSyncToken = nextSyncToken,
                    errors = errors
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error downloading calendar $calendarId", e)
            return Result.failure(e)
        }
    }

    /**
     * Upload local changes to Google Calendar.
     */
    private suspend fun uploadLocalChanges(): Result<Int> {
        val googleService = this.googleService
            ?: return Result.failure(Exception("Google service not configured"))
        val eventMapper = this.eventMapper
            ?: return Result.failure(Exception("Event mapper not configured"))

        var uploaded = 0

        try {
            // Get events that need syncing (new or modified)
            val eventsToSync = calendarDao.getEventsNeedingSync()

            for (event in eventsToSync) {
                if (event.providerType != CalendarProvider.GOOGLE) continue
                if (event.isDeleted) continue // Deletions handled separately

                val request = eventMapper.toGoogleEventRequest(event)

                val result = if (event.remoteId == null) {
                    // New event - create on Google
                    googleService.createEvent(event.calendarId, request)
                } else {
                    // Existing event - update on Google
                    googleService.updateEvent(event.calendarId, event.remoteId, request)
                }

                result.fold(
                    onSuccess = { remoteEvent ->
                        // Update local event with remote ID and etag
                        val updatedEvent = event.copy(
                            remoteId = remoteEvent.id,
                            etag = remoteEvent.etag,
                            lastSyncedAt = ZonedDateTime.now(),
                            needsSync = false
                        )
                        calendarDao.updateEvent(updatedEvent)
                        uploaded++
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to upload event ${event.id}", error)
                        // Keep needsSync=true for retry
                    }
                )
            }

            return Result.success(uploaded)

        } catch (e: Exception) {
            Log.e(TAG, "Error uploading changes", e)
            return Result.failure(e)
        }
    }

    /**
     * Sync deletions - delete events from Google that were deleted locally.
     */
    private suspend fun syncDeletions(): Result<Int> {
        val googleService = this.googleService
            ?: return Result.failure(Exception("Google service not configured"))

        var deleted = 0

        try {
            val deletedEvents = calendarDao.getDeletedEventsNeedingSync()

            for (event in deletedEvents) {
                if (event.providerType != CalendarProvider.GOOGLE) continue
                if (event.remoteId == null) {
                    // Never synced to Google - just hard delete locally
                    calendarDao.hardDeleteEvent(event.id)
                    deleted++
                    continue
                }

                val result = googleService.deleteEvent(event.calendarId, event.remoteId)

                result.fold(
                    onSuccess = {
                        // Successfully deleted from Google - hard delete locally
                        calendarDao.hardDeleteEvent(event.id)
                        deleted++
                    },
                    onFailure = { error ->
                        if (error is ApiException && error.isNotFound) {
                            // Already deleted on Google - hard delete locally
                            calendarDao.hardDeleteEvent(event.id)
                            deleted++
                        } else {
                            Log.e(TAG, "Failed to delete event ${event.id} from Google", error)
                        }
                    }
                )
            }

            return Result.success(deleted)

        } catch (e: Exception) {
            Log.e(TAG, "Error syncing deletions", e)
            return Result.failure(e)
        }
    }

    /**
     * Cancel any ongoing sync operation.
     */
    fun cancelSync() {
        syncJob?.cancel()
        isSyncing.set(false)
        _syncState.value = SyncState(status = SyncStatus.IDLE, message = "Sync cancelled")
    }

    private fun updateState(
        status: SyncStatus,
        phase: SyncPhase,
        progress: Float,
        message: String
    ) {
        _syncState.value = SyncState(
            status = status,
            currentPhase = phase,
            progress = progress,
            message = message
        )
    }

    private fun finishSync(
        startTime: Long,
        success: Boolean,
        eventsDownloaded: Int = 0,
        eventsInserted: Int = 0,
        eventsUpdated: Int = 0,
        eventsDeleted: Int = 0,
        eventsUploaded: Int = 0,
        conflictsResolved: Int = 0,
        errors: List<SyncError> = emptyList(),
        wasIncremental: Boolean = false
    ): Result<SyncResult> {
        val duration = System.currentTimeMillis() - startTime
        val result = SyncResult(
            success = success,
            eventsDownloaded = eventsDownloaded,
            eventsInserted = eventsInserted,
            eventsUpdated = eventsUpdated,
            eventsDeleted = eventsDeleted,
            eventsUploaded = eventsUploaded,
            conflictsResolved = conflictsResolved,
            errors = errors,
            duration = duration,
            wasIncremental = wasIncremental
        )

        val status = when {
            success && errors.isEmpty() -> SyncStatus.SUCCESS
            success -> SyncStatus.PARTIAL_SUCCESS
            else -> SyncStatus.ERROR
        }

        _syncState.value = SyncState(
            status = status,
            lastSyncTime = ZonedDateTime.now(),
            lastSyncResult = result,
            progress = 1f,
            message = if (success) "Sync complete" else "Sync failed",
            error = errors.firstOrNull()?.message
        )

        Log.d(TAG, "Sync finished: $result")

        return if (success) Result.success(result) else Result.failure(
            Exception(errors.firstOrNull()?.message ?: "Sync failed")
        )
    }

}
