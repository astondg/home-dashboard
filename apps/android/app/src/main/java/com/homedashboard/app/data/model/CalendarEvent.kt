package com.homedashboard.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import java.time.ZonedDateTime

/**
 * Represents a calendar event in the local database.
 * Syncs with remote calendars (Google, Microsoft, iCloud).
 */
@Entity(tableName = "calendar_events")
data class CalendarEvent(
    @PrimaryKey
    val id: String,

    // Event details
    val title: String,
    val description: String? = null,
    val location: String? = null,

    // Timing
    val startTime: ZonedDateTime,
    val endTime: ZonedDateTime,
    val isAllDay: Boolean = false,

    // Source tracking
    val calendarId: String,
    val calendarName: String,
    val providerType: CalendarProvider,

    // Remote sync info
    val remoteId: String? = null,
    val etag: String? = null,
    val lastSyncedAt: ZonedDateTime? = null,

    // Local state
    val createdAt: ZonedDateTime = ZonedDateTime.now(),
    val updatedAt: ZonedDateTime = ZonedDateTime.now(),
    val isDeleted: Boolean = false,
    val needsSync: Boolean = false
)

/**
 * Supported calendar providers
 */
enum class CalendarProvider {
    LOCAL,      // Local-only events (not synced)
    GOOGLE,     // Google Calendar (OAuth)
    MICROSOFT,  // Microsoft 365/Outlook (OAuth)
    ICLOUD,     // Apple iCloud (CalDAV)
    CALDAV      // Generic CalDAV (Nextcloud, etc.)
}

/**
 * Represents a calendar (container for events)
 */
@Entity(tableName = "calendars")
data class Calendar(
    @PrimaryKey
    val id: String,
    val name: String,
    val color: Int,
    val providerType: CalendarProvider,
    val accountEmail: String,
    val isVisible: Boolean = true,
    val isReadOnly: Boolean = false
)
