package com.homedashboard.app.data.remote.caldav

import java.time.ZonedDateTime

/**
 * Data models for CalDAV (WebDAV/iCalendar) protocol.
 * These represent the parsed responses from CalDAV PROPFIND and REPORT requests.
 */

/**
 * Represents a CalDAV calendar collection.
 */
data class CalDavCalendar(
    /** The full URL/href of the calendar collection */
    val href: String,
    /** Display name of the calendar */
    val displayName: String,
    /** Calendar color (as hex string, e.g., "#FF5733") */
    val color: String? = null,
    /** Supported component types (e.g., VEVENT, VTODO) */
    val supportedComponents: List<String> = listOf("VEVENT"),
    /** Change tag - changes when any event in calendar changes */
    val ctag: String? = null,
    /** Sync token for incremental sync */
    val syncToken: String? = null,
    /** Whether this calendar is read-only */
    val readOnly: Boolean = false,
    /** Calendar description */
    val description: String? = null,
    /** Calendar order/position */
    val order: Int = 0
)

/**
 * Represents an individual calendar event resource.
 * Contains the raw iCalendar data and metadata.
 */
data class CalDavEventResource(
    /** The full URL/href of the event resource (e.g., /calendars/user/cal/event-123.ics) */
    val href: String,
    /** ETag for optimistic concurrency control */
    val etag: String?,
    /** Raw iCalendar data (VCALENDAR containing VEVENT) */
    val icalData: String?,
    /** HTTP status from multi-status response (200 = exists, 404 = deleted) */
    val status: Int = 200
) {
    /** Check if this resource was deleted (404 in sync-collection response) */
    val isDeleted: Boolean get() = status == 404
}

/**
 * Parsed VEVENT from iCalendar data.
 * This is the intermediate representation between raw iCalendar and CalendarEvent.
 */
data class CalDavEvent(
    /** Unique identifier (UID property in iCalendar) */
    val uid: String,
    /** Event title (SUMMARY property) */
    val summary: String,
    /** Event description (DESCRIPTION property) */
    val description: String? = null,
    /** Event location (LOCATION property) */
    val location: String? = null,
    /** Start time (DTSTART property) */
    val dtStart: ZonedDateTime,
    /** End time (DTEND property) */
    val dtEnd: ZonedDateTime,
    /** Whether this is an all-day event (DTSTART has VALUE=DATE) */
    val isAllDay: Boolean = false,
    /** Creation timestamp (DTSTAMP property) */
    val dtStamp: ZonedDateTime,
    /** Last modification time (LAST-MODIFIED property) */
    val lastModified: ZonedDateTime? = null,
    /** Recurrence rule (RRULE property) */
    val rrule: String? = null,
    /** Exception dates for recurring events (EXDATE property) */
    val exDates: List<ZonedDateTime> = emptyList(),
    /** Event status (CONFIRMED, TENTATIVE, CANCELLED) */
    val status: EventStatus = EventStatus.CONFIRMED,
    /** Organizer email (ORGANIZER property) */
    val organizer: String? = null,
    /** Attendees (ATTENDEE property) */
    val attendees: List<String> = emptyList(),
    /** Sequence number for versioning (SEQUENCE property) */
    val sequence: Int = 0,
    /** Transparency (TRANSP property - OPAQUE or TRANSPARENT) */
    val transparency: Transparency = Transparency.OPAQUE,
    /** URL associated with the event */
    val url: String? = null,
    /** Categories/tags */
    val categories: List<String> = emptyList()
)

/**
 * Event status values from iCalendar spec.
 */
enum class EventStatus {
    TENTATIVE,
    CONFIRMED,
    CANCELLED;

    companion object {
        fun fromString(value: String?): EventStatus = when (value?.uppercase()) {
            "TENTATIVE" -> TENTATIVE
            "CANCELLED" -> CANCELLED
            else -> CONFIRMED
        }
    }
}

/**
 * Time transparency values from iCalendar spec.
 */
enum class Transparency {
    OPAQUE,      // Event blocks time (default)
    TRANSPARENT; // Event doesn't block time (free)

    companion object {
        fun fromString(value: String?): Transparency = when (value?.uppercase()) {
            "TRANSPARENT" -> TRANSPARENT
            else -> OPAQUE
        }
    }
}

/**
 * Response from a sync-collection REPORT request.
 */
data class SyncCollectionResponse(
    /** List of event resources (new/modified have data, deleted have 404 status) */
    val events: List<CalDavEventResource>,
    /** Next sync token to use for incremental sync */
    val syncToken: String?,
    /** Whether more results are available (pagination) */
    val hasMore: Boolean = false
)

/**
 * Response from a calendar-query REPORT request.
 */
data class CalendarQueryResponse(
    /** List of event resources matching the query */
    val events: List<CalDavEventResource>
)

/**
 * Response from calendar list PROPFIND.
 */
data class CalendarListResponse(
    /** List of calendars */
    val calendars: List<CalDavCalendar>
)

/**
 * Request body for creating/updating an event via PUT.
 */
data class CalDavEventRequest(
    /** The VEVENT to create/update */
    val event: CalDavEvent,
    /** For updates: the current ETag (for If-Match header) */
    val etag: String? = null
)

/**
 * Response from a PUT request (create/update event).
 */
data class CalDavPutResponse(
    /** New ETag from the server */
    val etag: String,
    /** Event URL/href */
    val href: String,
    /** Whether this was a create (true) or update (false) */
    val created: Boolean
)

// ==================== CalDAV Error Types ====================

/**
 * Base exception for CalDAV errors.
 */
open class CalDavException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Authentication failed (401/403).
 */
class CalDavAuthException(
    message: String = "Authentication failed"
) : CalDavException(message)

/**
 * Precondition failed - ETag mismatch (412).
 * The event was modified on the server since we last fetched it.
 */
class CalDavPreconditionFailedException(
    message: String = "ETag mismatch - resource was modified",
    /** The event URL that had the conflict */
    val eventHref: String? = null
) : CalDavException(message)

/**
 * Resource is locked (423).
 * Another client has a lock on the resource.
 */
class CalDavLockedException(
    message: String = "Resource is locked"
) : CalDavException(message)

/**
 * Sync token expired (410 Gone or specific CalDAV error).
 * Need to perform a full sync.
 */
class CalDavSyncTokenExpiredException(
    message: String = "Sync token expired - full sync required"
) : CalDavException(message)

/**
 * Resource not found (404).
 */
class CalDavNotFoundException(
    message: String = "Resource not found",
    val resourceHref: String? = null
) : CalDavException(message)

/**
 * Server error (5xx).
 */
class CalDavServerException(
    val statusCode: Int,
    message: String = "Server error: $statusCode"
) : CalDavException(message)

/**
 * Failed to parse CalDAV XML response.
 */
class CalDavParseException(
    message: String,
    cause: Throwable? = null
) : CalDavException(message, cause)

/**
 * Failed to parse iCalendar data.
 */
class ICalendarParseException(
    message: String,
    cause: Throwable? = null
) : CalDavException(message, cause)

// ==================== Constants ====================

/**
 * XML namespaces used in CalDAV.
 */
object CalDavNamespaces {
    const val DAV = "DAV:"
    const val CALDAV = "urn:ietf:params:xml:ns:caldav"
    const val APPLE = "http://apple.com/ns/ical/"
    const val CALENDARSERVER = "http://calendarserver.org/ns/"
}

/**
 * Common CalDAV properties.
 */
object CalDavProperties {
    // DAV properties
    const val RESOURCETYPE = "resourcetype"
    const val DISPLAYNAME = "displayname"
    const val GETETAG = "getetag"
    const val GETCTAG = "getctag"
    const val SYNC_TOKEN = "sync-token"
    const val CURRENT_USER_PRINCIPAL = "current-user-principal"

    // CalDAV properties
    const val CALENDAR_HOME_SET = "calendar-home-set"
    const val CALENDAR_DATA = "calendar-data"
    const val SUPPORTED_CALENDAR_COMPONENT_SET = "supported-calendar-component-set"

    // Apple iCal properties
    const val CALENDAR_COLOR = "calendar-color"
    const val CALENDAR_ORDER = "calendar-order"
}
