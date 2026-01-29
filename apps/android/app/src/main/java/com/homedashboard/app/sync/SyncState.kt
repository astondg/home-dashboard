package com.homedashboard.app.sync

import java.time.ZonedDateTime

/**
 * Data classes for tracking sync state and results.
 */

/**
 * Current state of the sync system.
 */
data class SyncState(
    val status: SyncStatus = SyncStatus.IDLE,
    val lastSyncTime: ZonedDateTime? = null,
    val lastSyncResult: SyncResult? = null,
    val currentPhase: SyncPhase? = null,
    val progress: Float = 0f, // 0.0 to 1.0
    val message: String? = null,
    val error: String? = null
)

/**
 * Overall sync status.
 */
enum class SyncStatus {
    IDLE,
    SYNCING,
    SUCCESS,
    PARTIAL_SUCCESS,
    ERROR
}

/**
 * Current phase of sync operation.
 */
enum class SyncPhase {
    AUTHENTICATING,
    FETCHING_CALENDARS,
    DOWNLOADING_EVENTS,
    MERGING,
    UPLOADING_CHANGES,
    DELETING_REMOTE,
    FINALIZING
}

/**
 * Result of a completed sync operation.
 */
data class SyncResult(
    val success: Boolean,
    val eventsDownloaded: Int = 0,
    val eventsInserted: Int = 0,
    val eventsUpdated: Int = 0,
    val eventsDeleted: Int = 0,
    val eventsUploaded: Int = 0,
    val conflictsResolved: Int = 0,
    val errors: List<SyncError> = emptyList(),
    val duration: Long = 0, // milliseconds
    val timestamp: ZonedDateTime = ZonedDateTime.now(),
    val wasIncremental: Boolean = false
) {
    val hasErrors: Boolean get() = errors.isNotEmpty()

    val totalChanges: Int get() = eventsInserted + eventsUpdated + eventsDeleted + eventsUploaded
}

/**
 * Individual sync error.
 */
data class SyncError(
    val type: SyncErrorType,
    val message: String,
    val eventId: String? = null,
    val calendarId: String? = null,
    val isRecoverable: Boolean = true
)

/**
 * Types of sync errors.
 */
enum class SyncErrorType {
    AUTHENTICATION_FAILED,
    NETWORK_ERROR,
    API_ERROR,
    RATE_LIMITED,
    SYNC_TOKEN_EXPIRED,
    CONFLICT,
    PARSE_ERROR,
    DOWNLOAD_FAILED,
    UPLOAD_FAILED,
    DELETE_FAILED,
    UNKNOWN
}

/**
 * Result of downloading events from a single calendar.
 */
data class CalendarSyncResult(
    val calendarId: String,
    val calendarName: String,
    val newEvents: Int,
    val updatedEvents: Int,
    val deletedEvents: Int,
    val nextSyncToken: String?,
    val errors: List<SyncError> = emptyList()
)

/**
 * Sync configuration options.
 */
data class SyncConfig(
    val syncIntervalMinutes: Int = 15,
    val requireWifi: Boolean = false,
    val requireCharging: Boolean = false,
    val syncWindowDaysBack: Int = 30,
    val syncWindowDaysForward: Int = 90,
    val conflictStrategy: ConflictStrategy = ConflictStrategy.LAST_WRITE_WINS
)

/**
 * Strategy for resolving sync conflicts.
 */
enum class ConflictStrategy {
    /**
     * Most recently updated version wins.
     * Compares updatedAt timestamps.
     */
    LAST_WRITE_WINS,

    /**
     * Always prefer the local version.
     */
    PREFER_LOCAL,

    /**
     * Always prefer the remote version.
     */
    PREFER_REMOTE
}

/**
 * Represents a sync conflict that needs resolution.
 */
data class SyncConflict(
    val localEventId: String,
    val remoteEventId: String,
    val localTitle: String,
    val remoteTitle: String,
    val localUpdatedAt: ZonedDateTime,
    val remoteUpdatedAt: ZonedDateTime,
    val differingFields: List<String>
)

/**
 * Information about events that need to be synced upstream.
 */
data class PendingSyncInfo(
    val newEvents: Int,
    val modifiedEvents: Int,
    val deletedEvents: Int
) {
    val total: Int get() = newEvents + modifiedEvents + deletedEvents
    val hasPending: Boolean get() = total > 0
}
