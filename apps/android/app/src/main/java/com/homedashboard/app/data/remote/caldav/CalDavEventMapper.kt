package com.homedashboard.app.data.remote.caldav

import android.util.Log
import com.homedashboard.app.data.model.CalendarEvent
import com.homedashboard.app.data.model.CalendarProvider
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.*
import java.io.StringReader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.Temporal
import java.util.UUID

/**
 * Mapper for converting between iCalendar format and CalendarEvent model.
 * Uses ical4j library for parsing and generating iCalendar data.
 * Compatible with ical4j 4.x API.
 */
class CalDavEventMapper {

    private val calendarBuilder = CalendarBuilder()

    // ==================== Parsing (iCalendar -> CalendarEvent) ====================

    /**
     * Parse iCalendar data to extract VEvent(s).
     * @param icalData Raw iCalendar string
     * @return List of parsed CalDavEvent objects
     */
    fun parseICalendar(icalData: String): List<CalDavEvent> {
        return try {
            val calendar = calendarBuilder.build(StringReader(icalData))
            val events = mutableListOf<CalDavEvent>()

            // ical4j 4.x: Use getComponents() with component type
            val vEvents = calendar.getComponents<VEvent>(Component.VEVENT)
            for (vEvent in vEvents) {
                parseVEvent(vEvent)?.let { events.add(it) }
            }

            events
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse iCalendar data", e)
            throw ICalendarParseException("Failed to parse iCalendar: ${e.message}", e)
        }
    }

    /**
     * Parse a single VEvent component.
     */
    private fun parseVEvent(vEvent: VEvent): CalDavEvent? {
        try {
            // UID is required - ical4j 4.x uses Optional-based getters
            val uid = vEvent.getProperty<Uid>(Property.UID)
                .map { it.value }
                .orElse(null) ?: return null

            // SUMMARY (title)
            val summary = vEvent.getProperty<Summary>(Property.SUMMARY)
                .map { it.value }
                .orElse("Untitled Event")

            // DTSTART is required - ical4j 4.x uses getDateTimeStart()
            val dtStartProp = vEvent.getDateTimeStart<Temporal>().orElse(null) ?: return null
            val dtStartValue = dtStartProp.date

            // Check if all-day by looking at the parameter or if it's a LocalDate
            val valueParam = dtStartProp.getParameter<Value>("VALUE")
            val isAllDay = dtStartValue is LocalDate ||
                (valueParam != null && valueParam == Value.DATE)

            // Parse start time
            val startDateTime = parseTemporalToZonedDateTime(dtStartValue, isAllDay)

            // Parse end time (or calculate from duration/use start)
            val dtEndProp = vEvent.getDateTimeEnd<Temporal>().orElse(null)
            val endDateTime = if (dtEndProp != null) {
                parseTemporalToZonedDateTime(dtEndProp.date, isAllDay)
            } else {
                // Try duration
                val durationProp = vEvent.getProperty<Duration>(Property.DURATION).orElse(null)
                if (durationProp != null) {
                    startDateTime.plus(durationProp.duration)
                } else {
                    // Default: 1 day for all-day, 1 hour otherwise
                    if (isAllDay) startDateTime.plusDays(1) else startDateTime.plusHours(1)
                }
            }

            // DTSTAMP
            val dtStamp = vEvent.getProperty<DtStamp>(Property.DTSTAMP)
                .map { parseTemporalToZonedDateTime(it.date, false) }
                .orElse(ZonedDateTime.now())

            // LAST-MODIFIED
            val lastModified = vEvent.getProperty<LastModified>(Property.LAST_MODIFIED)
                .map { parseTemporalToZonedDateTime(it.date, false) }
                .orElse(null)

            // Optional properties
            val description = vEvent.getProperty<Description>(Property.DESCRIPTION)
                .map { it.value }
                .orElse(null)

            val location = vEvent.getProperty<Location>(Property.LOCATION)
                .map { it.value }
                .orElse(null)

            val rrule = vEvent.getProperty<RRule<Temporal>>(Property.RRULE)
                .map { it.value }
                .orElse(null)

            val status = vEvent.getProperty<Status>(Property.STATUS)
                .map { EventStatus.fromString(it.value) }
                .orElse(EventStatus.CONFIRMED)

            val sequence = vEvent.getProperty<Sequence>(Property.SEQUENCE)
                .map { it.sequenceNo }
                .orElse(0)

            val transp = vEvent.getProperty<Transp>(Property.TRANSP)
                .map { Transparency.fromString(it.value) }
                .orElse(Transparency.OPAQUE)

            val url = vEvent.getProperty<Url>(Property.URL)
                .map { it.uri?.toString() }
                .orElse(null)

            // Categories - cast to List to avoid iterator ambiguity
            val categoriesProp = vEvent.getProperty<Categories>(Property.CATEGORIES).orElse(null)
            val categories: List<String> = if (categoriesProp != null) {
                val textList = categoriesProp.categories as List<*>
                textList.map { it.toString() }
            } else {
                emptyList()
            }

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
            Log.e(TAG, "Failed to parse VEvent", e)
            return null
        }
    }

    /**
     * Parse a Temporal (LocalDate, LocalDateTime, ZonedDateTime, Instant) to ZonedDateTime.
     */
    private fun parseTemporalToZonedDateTime(temporal: Temporal, isAllDay: Boolean): ZonedDateTime {
        return when (temporal) {
            is ZonedDateTime -> temporal
            is java.time.LocalDateTime -> temporal.atZone(ZoneId.systemDefault())
            is LocalDate -> temporal.atStartOfDay(ZoneId.systemDefault())
            is Instant -> temporal.atZone(ZoneId.of("UTC"))
            else -> {
                // Fallback: try to convert via toString parsing
                try {
                    ZonedDateTime.parse(temporal.toString())
                } catch (e: Exception) {
                    ZonedDateTime.now()
                }
            }
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
     * Parses the iCalendar data and maps to local model.
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
     * Format: 20260129T143000Z
     */
    private fun formatDateTime(dateTime: ZonedDateTime): String {
        val utc = dateTime.withZoneSameInstant(ZoneId.of("UTC"))
        return utc.format(DATE_TIME_FORMATTER)
    }

    /**
     * Format ZonedDateTime as iCalendar DATE (for all-day events).
     * Format: 20260129
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
     * Used for conflict resolution.
     */
    fun isRemoteNewer(localEvent: CalendarEvent, remoteEvent: CalDavEvent): Boolean {
        val localUpdated = localEvent.updatedAt
        val remoteUpdated = remoteEvent.lastModified ?: remoteEvent.dtStamp

        return remoteUpdated.isAfter(localUpdated)
    }

    /**
     * Merge remote changes into local event.
     * Preserves local ID while updating other fields.
     */
    fun mergeRemoteChanges(
        localEvent: CalendarEvent,
        remoteEvent: CalDavEvent,
        newEtag: String?
    ): CalendarEvent {
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
        val uid = event.remoteId ?: event.id
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

        // iCalendar date formatters
        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}
