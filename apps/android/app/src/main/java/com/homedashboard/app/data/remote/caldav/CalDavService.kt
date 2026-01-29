package com.homedashboard.app.data.remote.caldav

import android.util.Log
import com.homedashboard.app.auth.ICloudAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * CalDAV service for interacting with iCloud Calendar via WebDAV protocol.
 * Handles calendar discovery, event fetching, and CRUD operations.
 */
class CalDavService(
    private val okHttpClient: OkHttpClient,
    private val authManager: ICloudAuthManager
) {
    private val xmlParser = CalDavXmlParser()

    // Date formatter for CalDAV time-range queries
    private val calDavDateFormatter = DateTimeFormatter
        .ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZone(ZoneOffset.UTC)

    // ==================== Calendar Discovery ====================

    /**
     * Discover all calendars for the authenticated user.
     * @param calendarHomeUrl The calendar home URL (discovered during auth)
     * @return List of calendars
     */
    suspend fun listCalendars(calendarHomeUrl: String): Result<List<CalDavCalendar>> =
        withContext(Dispatchers.IO) {
            try {
                val authHeader = authManager.getAuthorizationHeader()
                    ?: return@withContext Result.failure(CalDavAuthException())

                val requestBody = xmlParser.buildCalendarListPropfind()
                    .toRequestBody(CONTENT_TYPE_XML)

                val request = Request.Builder()
                    .url(calendarHomeUrl)
                    .addHeader("Authorization", authHeader)
                    .addHeader("Content-Type", "application/xml; charset=utf-8")
                    .addHeader("Depth", "1")
                    .method("PROPFIND", requestBody)
                    .build()

                val response = okHttpClient.newCall(request).execute()

                when (response.code) {
                    401, 403 -> return@withContext Result.failure(CalDavAuthException())
                    404 -> return@withContext Result.failure(CalDavNotFoundException("Calendar home not found", calendarHomeUrl))
                    in 500..599 -> return@withContext Result.failure(CalDavServerException(response.code))
                }

                val body = response.body?.string()
                    ?: return@withContext Result.failure(CalDavParseException("Empty response"))

                Log.d(TAG, "Calendar list response: $body")

                val calendarList = xmlParser.parseCalendarList(body)
                Result.success(calendarList.calendars)
            } catch (e: CalDavException) {
                Result.failure(e)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to list calendars", e)
                Result.failure(CalDavException("Failed to list calendars: ${e.message}", e))
            }
        }

    // ==================== Event Fetching ====================

    /**
     * Fetch events using incremental sync (sync-collection REPORT).
     * @param calendarUrl The calendar URL
     * @param syncToken Previous sync token (null for initial sync)
     * @return Events and new sync token
     */
    suspend fun syncEvents(
        calendarUrl: String,
        syncToken: String?
    ): Result<SyncCollectionResponse> = withContext(Dispatchers.IO) {
        try {
            val authHeader = authManager.getAuthorizationHeader()
                ?: return@withContext Result.failure(CalDavAuthException())

            val requestBody = xmlParser.buildSyncCollectionReport(syncToken)
                .toRequestBody(CONTENT_TYPE_XML)

            val request = Request.Builder()
                .url(calendarUrl)
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "application/xml; charset=utf-8")
                .addHeader("Depth", "1")
                .method("REPORT", requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()

            when (response.code) {
                401, 403 -> return@withContext Result.failure(CalDavAuthException())
                404 -> return@withContext Result.failure(CalDavNotFoundException("Calendar not found", calendarUrl))
                410 -> {
                    // Sync token expired
                    return@withContext Result.failure(CalDavSyncTokenExpiredException())
                }
                in 500..599 -> return@withContext Result.failure(CalDavServerException(response.code))
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(CalDavParseException("Empty response"))

            Log.d(TAG, "Sync response (first 500 chars): ${body.take(500)}")

            val syncResponse = xmlParser.parseSyncCollectionResponse(body)
            Result.success(syncResponse)
        } catch (e: CalDavException) {
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync events", e)
            Result.failure(CalDavException("Failed to sync events: ${e.message}", e))
        }
    }

    /**
     * Fetch events within a time range (calendar-query REPORT).
     * Use this for initial sync or when sync token is invalid.
     * @param calendarUrl The calendar URL
     * @param startTime Start of time range
     * @param endTime End of time range
     */
    suspend fun getEventsInRange(
        calendarUrl: String,
        startTime: Instant,
        endTime: Instant
    ): Result<List<CalDavEventResource>> = withContext(Dispatchers.IO) {
        try {
            val authHeader = authManager.getAuthorizationHeader()
                ?: return@withContext Result.failure(CalDavAuthException())

            val startStr = calDavDateFormatter.format(startTime)
            val endStr = calDavDateFormatter.format(endTime)

            val requestBody = xmlParser.buildCalendarQueryReport(startStr, endStr)
                .toRequestBody(CONTENT_TYPE_XML)

            val request = Request.Builder()
                .url(calendarUrl)
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "application/xml; charset=utf-8")
                .addHeader("Depth", "1")
                .method("REPORT", requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()

            when (response.code) {
                401, 403 -> return@withContext Result.failure(CalDavAuthException())
                404 -> return@withContext Result.failure(CalDavNotFoundException("Calendar not found", calendarUrl))
                in 500..599 -> return@withContext Result.failure(CalDavServerException(response.code))
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(CalDavParseException("Empty response"))

            val queryResponse = xmlParser.parseCalendarQueryResponse(body)
            Result.success(queryResponse.events)
        } catch (e: CalDavException) {
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get events in range", e)
            Result.failure(CalDavException("Failed to get events: ${e.message}", e))
        }
    }

    /**
     * Fetch specific events by their hrefs (calendar-multiget REPORT).
     */
    suspend fun getEventsByHref(
        calendarUrl: String,
        eventHrefs: List<String>
    ): Result<List<CalDavEventResource>> = withContext(Dispatchers.IO) {
        if (eventHrefs.isEmpty()) {
            return@withContext Result.success(emptyList())
        }

        try {
            val authHeader = authManager.getAuthorizationHeader()
                ?: return@withContext Result.failure(CalDavAuthException())

            val requestBody = xmlParser.buildCalendarMultigetReport(eventHrefs)
                .toRequestBody(CONTENT_TYPE_XML)

            val request = Request.Builder()
                .url(calendarUrl)
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "application/xml; charset=utf-8")
                .method("REPORT", requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()

            when (response.code) {
                401, 403 -> return@withContext Result.failure(CalDavAuthException())
                in 500..599 -> return@withContext Result.failure(CalDavServerException(response.code))
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(CalDavParseException("Empty response"))

            val queryResponse = xmlParser.parseCalendarQueryResponse(body)
            Result.success(queryResponse.events)
        } catch (e: CalDavException) {
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get events by href", e)
            Result.failure(CalDavException("Failed to get events: ${e.message}", e))
        }
    }

    // ==================== Event CRUD ====================

    /**
     * Create a new event via PUT.
     * @param calendarUrl The calendar URL
     * @param eventFileName The event filename (e.g., "event-uuid.ics")
     * @param icalData The iCalendar data
     * @return The new ETag from the server
     */
    suspend fun createEvent(
        calendarUrl: String,
        eventFileName: String,
        icalData: String
    ): Result<CalDavPutResponse> = withContext(Dispatchers.IO) {
        try {
            val authHeader = authManager.getAuthorizationHeader()
                ?: return@withContext Result.failure(CalDavAuthException())

            val eventUrl = buildEventUrl(calendarUrl, eventFileName)
            val requestBody = icalData.toRequestBody(CONTENT_TYPE_ICAL)

            val request = Request.Builder()
                .url(eventUrl)
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "text/calendar; charset=utf-8")
                .addHeader("If-None-Match", "*") // Ensure we're creating, not updating
                .put(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()

            when (response.code) {
                401, 403 -> return@withContext Result.failure(CalDavAuthException())
                412 -> {
                    // Event already exists
                    return@withContext Result.failure(
                        CalDavPreconditionFailedException("Event already exists", eventUrl)
                    )
                }
                in 500..599 -> return@withContext Result.failure(CalDavServerException(response.code))
            }

            if (!response.isSuccessful && response.code != 201 && response.code != 204) {
                return@withContext Result.failure(
                    CalDavException("Failed to create event: ${response.code}")
                )
            }

            // Get the ETag from response
            val etag = response.header("ETag")?.removeSurrounding("\"")
                ?: return@withContext Result.failure(CalDavParseException("No ETag in response"))

            Result.success(
                CalDavPutResponse(
                    etag = etag,
                    href = eventUrl,
                    created = true
                )
            )
        } catch (e: CalDavException) {
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create event", e)
            Result.failure(CalDavException("Failed to create event: ${e.message}", e))
        }
    }

    /**
     * Update an existing event via PUT.
     * @param eventUrl The full event URL
     * @param icalData The updated iCalendar data
     * @param etag The current ETag for optimistic locking
     * @return The new ETag from the server
     */
    suspend fun updateEvent(
        eventUrl: String,
        icalData: String,
        etag: String
    ): Result<CalDavPutResponse> = withContext(Dispatchers.IO) {
        try {
            val authHeader = authManager.getAuthorizationHeader()
                ?: return@withContext Result.failure(CalDavAuthException())

            val requestBody = icalData.toRequestBody(CONTENT_TYPE_ICAL)

            val request = Request.Builder()
                .url(eventUrl)
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "text/calendar; charset=utf-8")
                .addHeader("If-Match", "\"$etag\"") // Optimistic locking
                .put(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()

            when (response.code) {
                401, 403 -> return@withContext Result.failure(CalDavAuthException())
                404 -> return@withContext Result.failure(CalDavNotFoundException("Event not found", eventUrl))
                412 -> {
                    // ETag mismatch - event was modified
                    return@withContext Result.failure(
                        CalDavPreconditionFailedException("Event was modified", eventUrl)
                    )
                }
                423 -> return@withContext Result.failure(CalDavLockedException())
                in 500..599 -> return@withContext Result.failure(CalDavServerException(response.code))
            }

            if (!response.isSuccessful && response.code != 204) {
                return@withContext Result.failure(
                    CalDavException("Failed to update event: ${response.code}")
                )
            }

            // Get the new ETag
            val newEtag = response.header("ETag")?.removeSurrounding("\"")
                ?: return@withContext Result.failure(CalDavParseException("No ETag in response"))

            Result.success(
                CalDavPutResponse(
                    etag = newEtag,
                    href = eventUrl,
                    created = false
                )
            )
        } catch (e: CalDavException) {
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update event", e)
            Result.failure(CalDavException("Failed to update event: ${e.message}", e))
        }
    }

    /**
     * Delete an event via DELETE.
     * @param eventUrl The full event URL
     * @param etag The current ETag for optimistic locking
     */
    suspend fun deleteEvent(
        eventUrl: String,
        etag: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val authHeader = authManager.getAuthorizationHeader()
                ?: return@withContext Result.failure(CalDavAuthException())

            val requestBuilder = Request.Builder()
                .url(eventUrl)
                .addHeader("Authorization", authHeader)
                .delete()

            // Add If-Match if we have an ETag
            if (!etag.isNullOrEmpty()) {
                requestBuilder.addHeader("If-Match", "\"$etag\"")
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()

            when (response.code) {
                401, 403 -> return@withContext Result.failure(CalDavAuthException())
                404 -> {
                    // Already deleted - consider success
                    return@withContext Result.success(Unit)
                }
                412 -> {
                    return@withContext Result.failure(
                        CalDavPreconditionFailedException("Event was modified", eventUrl)
                    )
                }
                423 -> return@withContext Result.failure(CalDavLockedException())
                in 500..599 -> return@withContext Result.failure(CalDavServerException(response.code))
            }

            if (!response.isSuccessful && response.code != 204) {
                return@withContext Result.failure(
                    CalDavException("Failed to delete event: ${response.code}")
                )
            }

            Result.success(Unit)
        } catch (e: CalDavException) {
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete event", e)
            Result.failure(CalDavException("Failed to delete event: ${e.message}", e))
        }
    }

    // ==================== Helpers ====================

    /**
     * Build the full event URL from calendar URL and event filename.
     */
    private fun buildEventUrl(calendarUrl: String, eventFileName: String): String {
        val baseUrl = if (calendarUrl.endsWith("/")) calendarUrl else "$calendarUrl/"
        val filename = if (eventFileName.endsWith(".ics")) eventFileName else "$eventFileName.ics"
        return "$baseUrl$filename"
    }

    companion object {
        private const val TAG = "CalDavService"
        private val CONTENT_TYPE_XML = "application/xml; charset=utf-8".toMediaType()
        private val CONTENT_TYPE_ICAL = "text/calendar; charset=utf-8".toMediaType()
    }
}
