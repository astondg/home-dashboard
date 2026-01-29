package com.homedashboard.app.data.remote

import com.homedashboard.app.data.model.Calendar
import com.homedashboard.app.data.model.CalendarEvent
import com.homedashboard.app.data.model.CalendarProvider
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Maps between Google Calendar API DTOs and local domain models.
 * Handles timezone conversion and field mapping.
 */
class GoogleEventMapper {

    private val rfc3339Formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    private val dateOnlyFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    // ========================================================================
    // CALENDAR MAPPING
    // ========================================================================

    /**
     * Map a Google Calendar DTO to local Calendar model.
     */
    fun toLocalCalendar(
        googleCalendar: GoogleCalendarDto,
        accountEmail: String
    ): Calendar {
        return Calendar(
            id = googleCalendar.id,
            name = googleCalendar.summary ?: "Unnamed Calendar",
            color = googleCalendar.getColorInt(),
            providerType = CalendarProvider.GOOGLE,
            accountEmail = accountEmail,
            isVisible = googleCalendar.selected ?: true,
            isReadOnly = googleCalendar.accessRole == "reader" ||
                    googleCalendar.accessRole == "freeBusyReader"
        )
    }

    // ========================================================================
    // EVENT MAPPING: GOOGLE -> LOCAL
    // ========================================================================

    /**
     * Map a Google Event DTO to local CalendarEvent model.
     *
     * @param googleEvent The event from Google Calendar API
     * @param calendarId The local calendar ID
     * @param calendarName Display name of the calendar
     * @param existingLocalId If updating an existing event, provide its local ID
     * @return CalendarEvent ready to insert/update in local database
     */
    fun toLocalEvent(
        googleEvent: GoogleEventDto,
        calendarId: String,
        calendarName: String,
        existingLocalId: String? = null
    ): CalendarEvent {
        val isAllDay = googleEvent.isAllDay()
        val (startTime, endTime) = parseEventTimes(googleEvent, isAllDay)

        return CalendarEvent(
            id = existingLocalId ?: UUID.randomUUID().toString(),
            title = googleEvent.summary ?: "(No title)",
            description = googleEvent.description,
            location = googleEvent.location,
            startTime = startTime,
            endTime = endTime,
            isAllDay = isAllDay,
            calendarId = calendarId,
            calendarName = calendarName,
            providerType = CalendarProvider.GOOGLE,
            remoteId = googleEvent.id,
            etag = googleEvent.etag,
            lastSyncedAt = ZonedDateTime.now(),
            updatedAt = parseTimestamp(googleEvent.updated) ?: ZonedDateTime.now(),
            isDeleted = googleEvent.isCancelled(),
            needsSync = false
        )
    }

    /**
     * Parse start and end times from Google event.
     * Handles both timed events (dateTime) and all-day events (date).
     */
    private fun parseEventTimes(
        googleEvent: GoogleEventDto,
        isAllDay: Boolean
    ): Pair<ZonedDateTime, ZonedDateTime> {
        val zone = googleEvent.start?.timeZone?.let { ZoneId.of(it) }
            ?: ZoneId.systemDefault()

        val startTime: ZonedDateTime
        val endTime: ZonedDateTime

        if (isAllDay) {
            // All-day events use date strings (yyyy-MM-dd)
            val startDate = googleEvent.start?.date?.let {
                LocalDate.parse(it, dateOnlyFormatter)
            } ?: LocalDate.now()

            val endDate = googleEvent.end?.date?.let {
                LocalDate.parse(it, dateOnlyFormatter)
            } ?: startDate.plusDays(1)

            // All-day events span from midnight to midnight
            startTime = startDate.atStartOfDay(zone)
            endTime = endDate.atStartOfDay(zone)
        } else {
            // Timed events use dateTime strings (RFC 3339)
            startTime = googleEvent.start?.dateTime?.let {
                ZonedDateTime.parse(it, rfc3339Formatter)
            } ?: ZonedDateTime.now()

            endTime = googleEvent.end?.dateTime?.let {
                ZonedDateTime.parse(it, rfc3339Formatter)
            } ?: startTime.plusHours(1)
        }

        return Pair(startTime, endTime)
    }

    /**
     * Parse RFC 3339 timestamp to ZonedDateTime.
     */
    private fun parseTimestamp(timestamp: String?): ZonedDateTime? {
        return try {
            timestamp?.let { ZonedDateTime.parse(it, rfc3339Formatter) }
        } catch (e: Exception) {
            null
        }
    }

    // ========================================================================
    // EVENT MAPPING: LOCAL -> GOOGLE
    // ========================================================================

    /**
     * Map a local CalendarEvent to Google API request format.
     */
    fun toGoogleEventRequest(localEvent: CalendarEvent): EventCreateRequest {
        val start = toEventDateTime(localEvent.startTime, localEvent.isAllDay)
        val end = toEventDateTime(localEvent.endTime, localEvent.isAllDay)

        return EventCreateRequest(
            summary = localEvent.title,
            description = localEvent.description,
            location = localEvent.location,
            start = start,
            end = end
        )
    }

    /**
     * Convert ZonedDateTime to Google EventDateTime format.
     */
    private fun toEventDateTime(
        dateTime: ZonedDateTime,
        isAllDay: Boolean
    ): EventDateTime {
        return if (isAllDay) {
            // All-day events use date string only
            EventDateTime(
                dateTime = null,
                date = dateTime.toLocalDate().format(dateOnlyFormatter),
                timeZone = dateTime.zone.id
            )
        } else {
            // Timed events use RFC 3339 dateTime
            EventDateTime(
                dateTime = dateTime.format(rfc3339Formatter),
                date = null,
                timeZone = dateTime.zone.id
            )
        }
    }

    // ========================================================================
    // SYNC HELPERS
    // ========================================================================

    /**
     * Determine if the remote event is newer than the local event.
     * Used for conflict resolution.
     */
    fun isRemoteNewer(
        localEvent: CalendarEvent,
        remoteEvent: GoogleEventDto
    ): Boolean {
        val remoteUpdated = parseTimestamp(remoteEvent.updated) ?: return true
        return remoteUpdated.isAfter(localEvent.updatedAt)
    }

    /**
     * Merge remote changes into local event while preserving local ID.
     */
    fun mergeRemoteChanges(
        localEvent: CalendarEvent,
        remoteEvent: GoogleEventDto,
        calendarName: String
    ): CalendarEvent {
        return toLocalEvent(
            googleEvent = remoteEvent,
            calendarId = localEvent.calendarId,
            calendarName = calendarName,
            existingLocalId = localEvent.id
        )
    }

    /**
     * Create a list of events from paginated API responses.
     * Handles pagination by collecting all pages.
     */
    fun mapEventsList(
        events: List<GoogleEventDto>,
        calendarId: String,
        calendarName: String,
        existingEventsMap: Map<String, CalendarEvent> // remoteId -> localEvent
    ): List<CalendarEvent> {
        return events.map { googleEvent ->
            val existingEvent = existingEventsMap[googleEvent.id]
            toLocalEvent(
                googleEvent = googleEvent,
                calendarId = calendarId,
                calendarName = calendarName,
                existingLocalId = existingEvent?.id
            )
        }
    }
}
