package com.homedashboard.app.sync

import android.util.Log
import com.homedashboard.app.auth.ICloudAuthManager
import com.homedashboard.app.auth.TokenStorage
import com.homedashboard.app.data.local.CalendarDao
import com.homedashboard.app.data.model.CalendarEvent
import com.homedashboard.app.data.model.CalendarProvider
import com.homedashboard.app.data.remote.caldav.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Sync provider for iCloud Calendar via CalDAV protocol.
 * Handles bidirectional sync: download → merge → upload → delete.
 */
class ICloudSyncProvider(
    private val calendarDao: CalendarDao,
    private val calDavService: CalDavService,
    private val eventMapper: CalDavEventMapper,
    private val tokenStorage: TokenStorage,
    private val authManager: ICloudAuthManager
) {
    /**
     * Perform a full sync with iCloud calendars.
     */
    suspend fun performSync(
        forceFullSync: Boolean = false,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<ICloudSyncResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val errors = mutableListOf<SyncError>()

        var eventsDownloaded = 0
        var eventsInserted = 0
        var eventsUpdated = 0
        var eventsDeleted = 0
        var eventsUploaded = 0

        try {
            // Check authentication
            if (!tokenStorage.hasICloudCredentials()) {
                return@withContext Result.failure(
                    CalDavAuthException("Not authenticated with iCloud")
                )
            }

            val accountEmail = tokenStorage.getICloudEmail()
                ?: return@withContext Result.failure(CalDavAuthException("No iCloud email"))

            // Step 1: Discover calendars
            onProgress(0.1f, "Discovering calendars...")
            val calendarHomeUrl = authManager.getPrincipalUrl()
                ?.let { principalUrl ->
                    // For iCloud, calendar home is typically /[principal]/calendars/
                    principalUrl.replace("/principal/", "/calendars/")
                }
                ?: tokenStorage.getICloudServerUrl()?.let { "$it/calendars/" }
                ?: return@withContext Result.failure(CalDavException("No calendar home URL"))

            val calendarsResult = calDavService.listCalendars(calendarHomeUrl)
            if (calendarsResult.isFailure) {
                return@withContext Result.failure(calendarsResult.exceptionOrNull()!!)
            }

            val calendars = calendarsResult.getOrThrow()
                .filter { it.supportedComponents.contains("VEVENT") }

            Log.d(TAG, "Found ${calendars.size} calendars")

            // Step 2: Download events from each calendar
            val progressPerCalendar = 0.5f / calendars.size.coerceAtLeast(1)
            var currentProgress = 0.1f

            for (calendar in calendars) {
                onProgress(currentProgress, "Syncing ${calendar.displayName}...")

                try {
                    val downloadResult = downloadCalendarEvents(
                        calendar = calendar,
                        accountEmail = accountEmail,
                        forceFullSync = forceFullSync
                    )

                    eventsDownloaded += downloadResult.downloaded
                    eventsInserted += downloadResult.inserted
                    eventsUpdated += downloadResult.updated
                    eventsDeleted += downloadResult.deleted

                    downloadResult.errors.forEach { errors.add(it) }
                } catch (e: CalDavSyncTokenExpiredException) {
                    Log.w(TAG, "Sync token expired for ${calendar.displayName}, doing full sync")
                    tokenStorage.clearCalDavSyncToken(calendar.href)

                    // Retry with full sync
                    val downloadResult = downloadCalendarEvents(
                        calendar = calendar,
                        accountEmail = accountEmail,
                        forceFullSync = true
                    )

                    eventsDownloaded += downloadResult.downloaded
                    eventsInserted += downloadResult.inserted
                    eventsUpdated += downloadResult.updated
                    eventsDeleted += downloadResult.deleted
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync calendar ${calendar.displayName}", e)
                    errors.add(SyncError(
                        calendarId = calendar.href,
                        eventId = null,
                        message = "Failed to sync ${calendar.displayName}: ${e.message}",
                        type = SyncErrorType.DOWNLOAD_FAILED
                    ))
                }

                currentProgress += progressPerCalendar
            }

            // Step 3: Upload local changes
            onProgress(0.7f, "Uploading changes...")

            val uploadResult = uploadLocalChanges(calendars, accountEmail)
            eventsUploaded = uploadResult.uploaded
            uploadResult.errors.forEach { errors.add(it) }

            // Step 4: Delete remotely deleted events
            onProgress(0.85f, "Processing deletions...")

            val deleteResult = syncDeletions(calendars, accountEmail)
            eventsDeleted += deleteResult.deleted
            deleteResult.errors.forEach { errors.add(it) }

            // Step 5: Save last sync time
            tokenStorage.saveICloudLastSyncTime(System.currentTimeMillis())

            onProgress(1f, "Sync complete")

            val duration = System.currentTimeMillis() - startTime

            Result.success(
                ICloudSyncResult(
                    eventsDownloaded = eventsDownloaded,
                    eventsInserted = eventsInserted,
                    eventsUpdated = eventsUpdated,
                    eventsDeleted = eventsDeleted,
                    eventsUploaded = eventsUploaded,
                    errors = errors,
                    durationMs = duration
                )
            )
        } catch (e: CalDavAuthException) {
            Log.e(TAG, "Authentication error during sync", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.failure(CalDavException("Sync failed: ${e.message}", e))
        }
    }

    /**
     * Download events from a single calendar.
     */
    private suspend fun downloadCalendarEvents(
        calendar: CalDavCalendar,
        accountEmail: String,
        forceFullSync: Boolean
    ): DownloadResult {
        var downloaded = 0
        var inserted = 0
        var updated = 0
        var deleted = 0
        val errors = mutableListOf<SyncError>()

        // Try incremental sync first
        val syncToken = if (!forceFullSync) {
            tokenStorage.getCalDavSyncToken(calendar.href)
        } else null

        val syncResult = if (syncToken != null) {
            // Incremental sync
            calDavService.syncEvents(calendar.href, syncToken)
        } else {
            // Full sync with time range
            val startTime = Instant.now().minus(30, ChronoUnit.DAYS)
            val endTime = Instant.now().plus(90, ChronoUnit.DAYS)

            val eventsResult = calDavService.getEventsInRange(calendar.href, startTime, endTime)
            eventsResult.map { events ->
                SyncCollectionResponse(
                    events = events,
                    syncToken = calendar.syncToken
                )
            }
        }

        if (syncResult.isFailure) {
            val exception = syncResult.exceptionOrNull()
            if (exception is CalDavSyncTokenExpiredException) {
                throw exception
            }
            errors.add(SyncError(
                calendarId = calendar.href,
                message = "Download failed: ${exception?.message}",
                type = SyncErrorType.DOWNLOAD_FAILED
            ))
            return DownloadResult(downloaded, inserted, updated, deleted, errors)
        }

        val response = syncResult.getOrThrow()
        downloaded = response.events.size

        // Process each event
        for (resource in response.events) {
            try {
                if (resource.isDeleted) {
                    // Event was deleted on server
                    val localEvent = calendarDao.getEventByRemoteId(
                        extractUidFromHref(resource.href)
                    )
                    if (localEvent != null) {
                        calendarDao.hardDeleteEvent(localEvent.id)
                        deleted++
                    }
                } else if (resource.icalData != null) {
                    // New or modified event
                    val calDavEvents = eventMapper.parseICalendar(resource.icalData)
                    val calDavEvent = calDavEvents.firstOrNull() ?: continue

                    val existingEvent = calendarDao.getEventByRemoteId(calDavEvent.uid)

                    if (existingEvent != null) {
                        // Update existing event (if remote is newer)
                        if (eventMapper.isRemoteNewer(existingEvent, calDavEvent)) {
                            val updatedEvent = eventMapper.mergeRemoteChanges(
                                existingEvent, calDavEvent, resource.etag
                            )
                            calendarDao.updateEvent(updatedEvent)
                            updated++
                        }
                    } else {
                        // Insert new event
                        val newEvent = eventMapper.toLocalEvent(
                            calDavEvent = calDavEvent,
                            resourceHref = resource.href,
                            etag = resource.etag,
                            calendarId = calendar.href,
                            calendarName = calendar.displayName
                        )
                        calendarDao.insertEvent(newEvent)
                        inserted++
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process event ${resource.href}", e)
                errors.add(SyncError(
                    calendarId = calendar.href,
                    eventId = resource.href,
                    message = "Failed to process event: ${e.message}",
                    type = SyncErrorType.PARSE_ERROR
                ))
            }
        }

        // Save sync token for next incremental sync
        response.syncToken?.let { token ->
            tokenStorage.saveCalDavSyncToken(calendar.href, token)
        }

        return DownloadResult(downloaded, inserted, updated, deleted, errors)
    }

    /**
     * Upload local changes to iCloud.
     */
    private suspend fun uploadLocalChanges(
        calendars: List<CalDavCalendar>,
        accountEmail: String
    ): UploadResult {
        var uploaded = 0
        val errors = mutableListOf<SyncError>()

        // Get events that need sync (iCloud provider only, not deleted)
        val eventsToSync = calendarDao.getEventsNeedingSync()
            .filter { it.providerType == CalendarProvider.ICLOUD && !it.isDeleted }

        for (event in eventsToSync) {
            try {
                val icalData = eventMapper.toICalendar(event)
                val filename = eventMapper.generateEventFilename(event)

                // Find the calendar URL
                val calendarUrl = event.calendarId.ifEmpty {
                    // Use first available calendar as default
                    calendars.firstOrNull()?.href
                } ?: continue

                val result = if (event.remoteId == null) {
                    // New event - create
                    calDavService.createEvent(calendarUrl, filename, icalData)
                } else {
                    // Existing event - update
                    val eventUrl = buildEventUrl(calendarUrl, filename)
                    val etag = event.etag ?: ""
                    calDavService.updateEvent(eventUrl, icalData, etag)
                }

                if (result.isSuccess) {
                    val putResponse = result.getOrThrow()

                    // Update local event with new etag and remote ID
                    val updatedEvent = event.copy(
                        remoteId = event.remoteId ?: extractUidFromFilename(filename),
                        etag = putResponse.etag,
                        needsSync = false,
                        lastSyncedAt = ZonedDateTime.now()
                    )
                    calendarDao.updateEvent(updatedEvent)
                    uploaded++
                } else {
                    val error = result.exceptionOrNull()
                    if (error is CalDavPreconditionFailedException) {
                        // ETag mismatch - mark for re-download
                        Log.w(TAG, "ETag mismatch for event ${event.id}, will re-sync")
                    }
                    errors.add(SyncError(
                        calendarId = event.calendarId,
                        eventId = event.id,
                        message = "Upload failed: ${error?.message}",
                        type = SyncErrorType.UPLOAD_FAILED
                    ))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload event ${event.id}", e)
                errors.add(SyncError(
                    calendarId = event.calendarId,
                    eventId = event.id,
                    message = "Upload failed: ${e.message}",
                    type = SyncErrorType.UPLOAD_FAILED
                ))
            }
        }

        return UploadResult(uploaded, errors)
    }

    /**
     * Sync deletions - delete remote events that were deleted locally.
     */
    private suspend fun syncDeletions(
        calendars: List<CalDavCalendar>,
        accountEmail: String
    ): DeleteResult {
        var deleted = 0
        val errors = mutableListOf<SyncError>()

        // Get locally deleted events that have a remote ID
        val deletedEvents = calendarDao.getDeletedEventsNeedingSync()
            .filter { it.providerType == CalendarProvider.ICLOUD && it.remoteId != null }

        for (event in deletedEvents) {
            try {
                val calendarUrl = event.calendarId
                val filename = eventMapper.generateEventFilename(event)
                val eventUrl = buildEventUrl(calendarUrl, filename)

                val result = calDavService.deleteEvent(eventUrl, event.etag)

                if (result.isSuccess) {
                    // Hard delete locally
                    calendarDao.hardDeleteEvent(event.id)
                    deleted++
                } else {
                    val error = result.exceptionOrNull()
                    // If 404, event already deleted remotely - clean up locally
                    if (error is CalDavNotFoundException) {
                        calendarDao.hardDeleteEvent(event.id)
                        deleted++
                    } else {
                        errors.add(SyncError(
                            calendarId = event.calendarId,
                            eventId = event.id,
                            message = "Delete failed: ${error?.message}",
                            type = SyncErrorType.DELETE_FAILED
                        ))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete event ${event.id}", e)
                errors.add(SyncError(
                    calendarId = event.calendarId,
                    eventId = event.id,
                    message = "Delete failed: ${e.message}",
                    type = SyncErrorType.DELETE_FAILED
                ))
            }
        }

        return DeleteResult(deleted, errors)
    }

    // ==================== Helpers ====================

    private fun buildEventUrl(calendarUrl: String, filename: String): String {
        val baseUrl = if (calendarUrl.endsWith("/")) calendarUrl else "$calendarUrl/"
        return "$baseUrl$filename"
    }

    private fun extractUidFromHref(href: String): String {
        // Remove .ics extension and extract filename as UID
        return href.substringAfterLast("/").removeSuffix(".ics")
    }

    private fun extractUidFromFilename(filename: String): String {
        return filename.removeSuffix(".ics")
    }

    companion object {
        private const val TAG = "ICloudSyncProvider"
    }
}

/**
 * Result of iCloud sync operation.
 */
data class ICloudSyncResult(
    val eventsDownloaded: Int,
    val eventsInserted: Int,
    val eventsUpdated: Int,
    val eventsDeleted: Int,
    val eventsUploaded: Int,
    val errors: List<SyncError>,
    val durationMs: Long
) {
    val totalChanges: Int get() = eventsInserted + eventsUpdated + eventsDeleted + eventsUploaded
    val hasErrors: Boolean get() = errors.isNotEmpty()
}

// Internal result classes
private data class DownloadResult(
    val downloaded: Int,
    val inserted: Int,
    val updated: Int,
    val deleted: Int,
    val errors: List<SyncError>
)

private data class UploadResult(
    val uploaded: Int,
    val errors: List<SyncError>
)

private data class DeleteResult(
    val deleted: Int,
    val errors: List<SyncError>
)
