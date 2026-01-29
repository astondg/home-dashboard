package com.homedashboard.app.data.remote

import com.google.gson.annotations.SerializedName

/**
 * Data Transfer Objects (DTOs) for Google Calendar API responses.
 * These match the structure of the Google Calendar REST API v3.
 *
 * API Reference: https://developers.google.com/calendar/api/v3/reference
 */

// ============================================================================
// CALENDAR LIST API RESPONSES
// ============================================================================

/**
 * Response from GET /users/me/calendarList
 */
data class CalendarListResponse(
    @SerializedName("kind") val kind: String?,
    @SerializedName("etag") val etag: String?,
    @SerializedName("nextPageToken") val nextPageToken: String?,
    @SerializedName("nextSyncToken") val nextSyncToken: String?,
    @SerializedName("items") val items: List<GoogleCalendarDto>?
)

/**
 * A calendar entry from the calendar list.
 */
data class GoogleCalendarDto(
    @SerializedName("kind") val kind: String?,
    @SerializedName("etag") val etag: String?,
    @SerializedName("id") val id: String,
    @SerializedName("summary") val summary: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("timeZone") val timeZone: String?,
    @SerializedName("colorId") val colorId: String?,
    @SerializedName("backgroundColor") val backgroundColor: String?,
    @SerializedName("foregroundColor") val foregroundColor: String?,
    @SerializedName("selected") val selected: Boolean?,
    @SerializedName("accessRole") val accessRole: String?, // owner, writer, reader, freeBusyReader
    @SerializedName("primary") val primary: Boolean?
)

// ============================================================================
// EVENTS API RESPONSES
// ============================================================================

/**
 * Response from GET /calendars/{calendarId}/events
 */
data class EventsListResponse(
    @SerializedName("kind") val kind: String?,
    @SerializedName("etag") val etag: String?,
    @SerializedName("summary") val summary: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("updated") val updated: String?,
    @SerializedName("timeZone") val timeZone: String?,
    @SerializedName("accessRole") val accessRole: String?,
    @SerializedName("nextPageToken") val nextPageToken: String?,
    @SerializedName("nextSyncToken") val nextSyncToken: String?,
    @SerializedName("items") val items: List<GoogleEventDto>?
)

/**
 * A calendar event from Google Calendar API.
 */
data class GoogleEventDto(
    @SerializedName("kind") val kind: String?,
    @SerializedName("etag") val etag: String?,
    @SerializedName("id") val id: String,
    @SerializedName("status") val status: String?, // confirmed, tentative, cancelled
    @SerializedName("htmlLink") val htmlLink: String?,
    @SerializedName("created") val created: String?,
    @SerializedName("updated") val updated: String?, // RFC 3339 timestamp
    @SerializedName("summary") val summary: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("location") val location: String?,
    @SerializedName("colorId") val colorId: String?,
    @SerializedName("start") val start: EventDateTime?,
    @SerializedName("end") val end: EventDateTime?,
    @SerializedName("recurrence") val recurrence: List<String>?,
    @SerializedName("recurringEventId") val recurringEventId: String?,
    @SerializedName("originalStartTime") val originalStartTime: EventDateTime?,
    @SerializedName("transparency") val transparency: String?, // opaque, transparent
    @SerializedName("visibility") val visibility: String?, // default, public, private, confidential
    @SerializedName("iCalUID") val iCalUID: String?,
    @SerializedName("sequence") val sequence: Int?,
    @SerializedName("organizer") val organizer: EventOrganizer?,
    @SerializedName("creator") val creator: EventCreator?,
    @SerializedName("attendees") val attendees: List<EventAttendee>?,
    @SerializedName("reminders") val reminders: EventReminders?
)

/**
 * Date/time for an event (either dateTime for timed events or date for all-day).
 */
data class EventDateTime(
    @SerializedName("dateTime") val dateTime: String?, // RFC 3339 with timezone offset
    @SerializedName("date") val date: String?, // yyyy-MM-dd for all-day events
    @SerializedName("timeZone") val timeZone: String?
)

/**
 * Event organizer information.
 */
data class EventOrganizer(
    @SerializedName("id") val id: String?,
    @SerializedName("email") val email: String?,
    @SerializedName("displayName") val displayName: String?,
    @SerializedName("self") val self: Boolean?
)

/**
 * Event creator information.
 */
data class EventCreator(
    @SerializedName("id") val id: String?,
    @SerializedName("email") val email: String?,
    @SerializedName("displayName") val displayName: String?,
    @SerializedName("self") val self: Boolean?
)

/**
 * Event attendee information.
 */
data class EventAttendee(
    @SerializedName("id") val id: String?,
    @SerializedName("email") val email: String?,
    @SerializedName("displayName") val displayName: String?,
    @SerializedName("organizer") val organizer: Boolean?,
    @SerializedName("self") val self: Boolean?,
    @SerializedName("optional") val optional: Boolean?,
    @SerializedName("responseStatus") val responseStatus: String?, // needsAction, declined, tentative, accepted
    @SerializedName("comment") val comment: String?
)

/**
 * Event reminder settings.
 */
data class EventReminders(
    @SerializedName("useDefault") val useDefault: Boolean?,
    @SerializedName("overrides") val overrides: List<EventReminder>?
)

/**
 * A single reminder override.
 */
data class EventReminder(
    @SerializedName("method") val method: String?, // email, popup
    @SerializedName("minutes") val minutes: Int?
)

// ============================================================================
// REQUEST BODIES FOR CREATE/UPDATE
// ============================================================================

/**
 * Request body for creating or updating an event.
 * Only includes fields that can be set by the client.
 */
data class EventCreateRequest(
    @SerializedName("summary") val summary: String,
    @SerializedName("description") val description: String?,
    @SerializedName("location") val location: String?,
    @SerializedName("start") val start: EventDateTime,
    @SerializedName("end") val end: EventDateTime,
    @SerializedName("reminders") val reminders: EventReminders? = EventReminders(useDefault = true, overrides = null)
)

// ============================================================================
// ERROR RESPONSES
// ============================================================================

/**
 * Error response from Google Calendar API.
 */
data class GoogleApiError(
    @SerializedName("error") val error: GoogleApiErrorDetail?
)

/**
 * Error detail from Google API.
 */
data class GoogleApiErrorDetail(
    @SerializedName("code") val code: Int?,
    @SerializedName("message") val message: String?,
    @SerializedName("errors") val errors: List<GoogleApiErrorItem>?
)

/**
 * Individual error item.
 */
data class GoogleApiErrorItem(
    @SerializedName("domain") val domain: String?,
    @SerializedName("reason") val reason: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("locationType") val locationType: String?,
    @SerializedName("location") val location: String?
)

// ============================================================================
// HELPER EXTENSIONS
// ============================================================================

/**
 * Check if this event is cancelled (deleted from Google Calendar).
 */
fun GoogleEventDto.isCancelled(): Boolean = status == "cancelled"

/**
 * Check if this event is an all-day event.
 */
fun GoogleEventDto.isAllDay(): Boolean = start?.date != null

/**
 * Get the calendar's color as an Android color int.
 */
fun GoogleCalendarDto.getColorInt(): Int {
    return try {
        if (backgroundColor != null) {
            android.graphics.Color.parseColor(backgroundColor)
        } else {
            0xFF4285F4.toInt() // Default Google blue
        }
    } catch (e: Exception) {
        0xFF4285F4.toInt()
    }
}
