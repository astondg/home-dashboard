package com.homedashboard.app.data.remote.caldav

import android.util.Log
import com.homedashboard.app.data.model.CalendarEvent
import com.homedashboard.app.data.model.CalendarProvider
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.component.VEvent
import java.io.StringReader
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Mapper for converting between iCalendar format and CalendarEvent model.
 * Uses ical4j 3.x for robust RFC 5545 parsing.
 */
class CalDavEventMapper {

    private val calendarBuilder = CalendarBuilder()

    // ==================== Parsing (iCalendar -> CalendarEvent) ====================

    /**
     * Parse iCalendar data to extract VEvent(s) using ical4j 3.x.
     * @param icalData Raw iCalendar string
     * @return List of parsed CalDavEvent objects
     */
    fun parseICalendar(icalData: String): List<CalDavEvent> {
        return try {
            val calendar = calendarBuilder.build(StringReader(icalData))
            val events = mutableListOf<CalDavEvent>()

            for (component in calendar.components) {
                if (component is VEvent) {
                    convertVEvent(component)?.let { events.add(it) }
                }
            }

            events
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse iCalendar data", e)
            throw ICalendarParseException("Failed to parse iCalendar: ${e.message}", e)
        }
    }

    /**
     * Convert an ical4j VEvent to our CalDavEvent model.
     */
    private fun convertVEvent(vevent: VEvent): CalDavEvent? {
        try {
            val uid = vevent.uid?.value ?: return null
            val summary = vevent.summary?.value ?: "Untitled Event"

            // Detect all-day: DtStart with Date (not DateTime) means all-day
            val dtStartProp = vevent.startDate ?: return null
            val isAllDay = dtStartProp.date !is net.fortuna.ical4j.model.DateTime

            val startDateTime = convertToZonedDateTime(dtStartProp.date, isAllDay)
                ?: return null

            // Parse DTEND
            val dtEndProp = vevent.endDate
            val endDateTime = if (dtEndProp != null) {
                convertToZonedDateTime(dtEndProp.date, isAllDay)
            } else {
                // Fallback: all-day gets +1 day, timed gets +1 hour
                if (isAllDay) startDateTime.plusDays(1) else startDateTime.plusHours(1)
            } ?: (if (isAllDay) startDateTime.plusDays(1) else startDateTime.plusHours(1))

            // DTSTAMP
            val dtStamp = vevent.dateStamp?.date?.let { convertToZonedDateTime(it, false) }
                ?: ZonedDateTime.now()

            // LAST-MODIFIED
            val lastModified = vevent.lastModified?.dateTime?.let { convertToZonedDateTime(it, false) }

            // Optional properties
            val description = vevent.description?.value
            val location = vevent.location?.value
            val rrule = vevent.getProperty<net.fortuna.ical4j.model.property.RRule>("RRULE")?.value
            val status = EventStatus.fromString(
                vevent.status?.value
            )
            val sequence = vevent.sequence?.sequenceNo ?: 0
            val transp = Transparency.fromString(
                vevent.transparency?.value
            )
            val url = vevent.url?.value?.toString()
            val categories = vevent.getProperty<net.fortuna.ical4j.model.property.Categories>("CATEGORIES")
                ?.categories?.toList() ?: emptyList()

            return CalDavEvent(
                uid = uid,
                summary = summary,
                description = description,
                location = location,
                dtStart = startDateTime,
                dtEnd = endDateTime,
                isAllDay = isAllDay,
                dtStamp = dtStamp,
                lastModified = lastModified,
                rrule = rrule,
                status = status,
                sequence = sequence,
                transparency = transp,
                url = url,
                categories = categories
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert VEvent", e)
            return null
        }
    }

    /**
     * Convert ical4j Date/DateTime to java.time.ZonedDateTime.
     * ical4j 3.x uses net.fortuna.ical4j.model.Date and DateTime (extends Date).
     */
    private fun convertToZonedDateTime(
        date: net.fortuna.ical4j.model.Date,
        isAllDay: Boolean
    ): ZonedDateTime? {
        return try {
            if (isAllDay) {
                // All-day: date-only value. Parse the string representation directly
                // to avoid any timezone issues with ical4j's epoch millis.
                val dateStr = date.toString() // ical4j Date.toString() returns "yyyyMMdd"
                val localDate = try {
                    java.time.LocalDate.parse(dateStr, DATE_FORMATTER)
                } catch (e: Exception) {
                    // Fallback to epoch millis via UTC
                    Log.w(TAG, "Failed to parse date string '$dateStr', falling back to epoch millis", e)
                    Instant.ofEpochMilli(date.time).atZone(ZoneId.of("UTC")).toLocalDate()
                }
                Log.d(TAG, "convertToZonedDateTime: allDay dateStr='$dateStr' → localDate=$localDate")
                localDate.atStartOfDay(ZoneId.systemDefault())
            } else {
                // DateTime with time component
                val instant = Instant.ofEpochMilli(date.time)
                // Always convert to system default timezone for display
                // iCal UTC times (ending in Z) need conversion to local time
                if (date is net.fortuna.ical4j.model.DateTime && date.timeZone != null) {
                    instant.atZone(ZoneId.of(date.timeZone.id))
                        .withZoneSameInstant(ZoneId.systemDefault())
                } else {
                    // UTC or floating time — convert to local
                    instant.atZone(ZoneId.systemDefault())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert date: $date", e)
            null
        }
    }

    /**
     * Convert CalDavEvent to local CalendarEvent model.
     */
    fun toLocalEvent(
        calDavEvent: CalDavEvent,
        resourceHref: String,
        etag: String?,
        calendarId: String,
        calendarName: String
    ): CalendarEvent {
        return CalendarEvent(
            id = UUID.randomUUID().toString(),
            title = calDavEvent.summary,
            description = calDavEvent.description,
            location = calDavEvent.location,
            startTime = calDavEvent.dtStart,
            endTime = calDavEvent.dtEnd,
            isAllDay = calDavEvent.isAllDay,
            calendarId = calendarId,
            calendarName = calendarName,
            providerType = CalendarProvider.ICLOUD,
            remoteId = calDavEvent.uid,
            etag = etag,
            lastSyncedAt = ZonedDateTime.now(),
            needsSync = false,
            isDeleted = calDavEvent.status == EventStatus.CANCELLED,
            createdAt = ZonedDateTime.now(),
            updatedAt = calDavEvent.lastModified ?: calDavEvent.dtStamp
        )
    }

    /**
     * Map CalDavEventResource to CalendarEvent.
     */
    fun resourceToLocalEvent(
        resource: CalDavEventResource,
        calendarId: String,
        calendarName: String
    ): CalendarEvent? {
        val icalData = resource.icalData ?: return null
        val events = parseICalendar(icalData)
        val calDavEvent = events.firstOrNull() ?: return null

        return toLocalEvent(
            calDavEvent = calDavEvent,
            resourceHref = resource.href,
            etag = resource.etag,
            calendarId = calendarId,
            calendarName = calendarName
        )
    }

    // ==================== Generation (CalendarEvent -> iCalendar) ====================

    /**
     * Convert CalendarEvent to iCalendar format string.
     */
    fun toICalendar(event: CalendarEvent): String {
        val uid = event.remoteId ?: "${event.id}@homedashboard.app"
        val now = ZonedDateTime.now()

        if (event.isAllDay) {
            Log.d(TAG, "toICalendar: allDay event '${event.title}' startTime=${event.startTime} → DATE=${formatDate(event.startTime)} (localDate=${event.startTime.toLocalDate()})")
        }

        val sb = StringBuilder()
        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:-//HomeDashboard//Wall Calendar//EN")
        sb.appendLine("BEGIN:VEVENT")
        sb.appendLine("UID:$uid")
        sb.appendLine("DTSTAMP:${formatDateTime(now)}")

        // Start/end times
        if (event.isAllDay) {
            sb.appendLine("DTSTART;VALUE=DATE:${formatDate(event.startTime)}")
            sb.appendLine("DTEND;VALUE=DATE:${formatDate(event.endTime)}")
        } else {
            sb.appendLine("DTSTART:${formatDateTime(event.startTime)}")
            sb.appendLine("DTEND:${formatDateTime(event.endTime)}")
        }

        // SUMMARY (title)
        sb.appendLine("SUMMARY:${escapeICalText(event.title)}")

        // Optional properties
        event.description?.let {
            sb.appendLine("DESCRIPTION:${escapeICalText(it)}")
        }
        event.location?.let {
            sb.appendLine("LOCATION:${escapeICalText(it)}")
        }

        // LAST-MODIFIED
        sb.appendLine("LAST-MODIFIED:${formatDateTime(event.updatedAt)}")

        // CREATED
        sb.appendLine("CREATED:${formatDateTime(event.createdAt)}")

        // STATUS
        if (event.isDeleted) {
            sb.appendLine("STATUS:CANCELLED")
        }

        sb.appendLine("END:VEVENT")
        sb.appendLine("END:VCALENDAR")

        return sb.toString()
    }

    /**
     * Format ZonedDateTime as iCalendar DATE-TIME (UTC).
     */
    private fun formatDateTime(dateTime: ZonedDateTime): String {
        val utc = dateTime.withZoneSameInstant(ZoneId.of("UTC"))
        return utc.format(DATE_TIME_FORMATTER)
    }

    /**
     * Format ZonedDateTime as iCalendar DATE (for all-day events).
     */
    private fun formatDate(dateTime: ZonedDateTime): String {
        return dateTime.format(DATE_FORMATTER)
    }

    /**
     * Escape special characters in iCalendar text values.
     */
    private fun escapeICalText(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\n", "\\n")
            .replace("\r", "")
    }

    // ==================== Sync Helpers ====================

    /**
     * Check if the remote event is newer than the local event.
     */
    fun isRemoteNewer(localEvent: CalendarEvent, remoteEvent: CalDavEvent): Boolean {
        val localUpdated = localEvent.updatedAt
        val remoteUpdated = remoteEvent.lastModified ?: remoteEvent.dtStamp

        return remoteUpdated.isAfter(localUpdated)
    }

    /**
     * Merge remote changes into local event.
     */
    fun mergeRemoteChanges(
        localEvent: CalendarEvent,
        remoteEvent: CalDavEvent,
        newEtag: String?
    ): CalendarEvent {
        val localDate = localEvent.startTime.toLocalDate()
        val remoteDate = remoteEvent.dtStart.toLocalDate()
        if (localDate != remoteDate) {
            Log.d(TAG, "mergeRemoteChanges: date change for '${localEvent.title}': " +
                "local=$localDate → remote=$remoteDate")
        }

        return localEvent.copy(
            title = remoteEvent.summary,
            description = remoteEvent.description,
            location = remoteEvent.location,
            startTime = remoteEvent.dtStart,
            endTime = remoteEvent.dtEnd,
            isAllDay = remoteEvent.isAllDay,
            etag = newEtag ?: localEvent.etag,
            updatedAt = remoteEvent.lastModified ?: remoteEvent.dtStamp,
            lastSyncedAt = ZonedDateTime.now(),
            needsSync = false,
            isDeleted = remoteEvent.status == EventStatus.CANCELLED
        )
    }

    /**
     * Generate a unique filename for a new event.
     */
    fun generateEventFilename(event: CalendarEvent): String {
        // Must match the UID used in toICalendar() so remoteId matches on download
        val uid = event.remoteId ?: "${event.id}@homedashboard.app"
        return "$uid.ics"
    }

    /**
     * Extract the event filename from a full href.
     */
    fun extractFilenameFromHref(href: String): String {
        return href.substringAfterLast("/")
    }

    /**
     * Extract the UID from iCalendar data.
     */
    fun extractUid(icalData: String): String? {
        return try {
            val events = parseICalendar(icalData)
            events.firstOrNull()?.uid
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "CalDavEventMapper"

        // iCalendar date formatters (used for generation only)
        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}
