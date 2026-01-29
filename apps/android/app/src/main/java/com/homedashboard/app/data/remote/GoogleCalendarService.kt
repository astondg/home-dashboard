package com.homedashboard.app.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.homedashboard.app.auth.GoogleAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Low-level REST client for Google Calendar API v3.
 * Uses OkHttp for HTTP operations and Gson for JSON parsing.
 */
class GoogleCalendarService(
    private val okHttpClient: OkHttpClient,
    private val authManager: GoogleAuthManager
) {
    private val gson: Gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
        .create()

    // ========================================================================
    // CALENDAR LIST OPERATIONS
    // ========================================================================

    /**
     * List all calendars for the authenticated user.
     * GET /users/me/calendarList
     */
    suspend fun listCalendars(
        pageToken: String? = null,
        showDeleted: Boolean = false,
        showHidden: Boolean = false
    ): Result<CalendarListResponse> {
        val params = buildList {
            if (pageToken != null) add("pageToken" to pageToken)
            if (showDeleted) add("showDeleted" to "true")
            if (showHidden) add("showHidden" to "true")
        }

        return makeGetRequest(
            path = "/users/me/calendarList",
            params = params,
            responseType = CalendarListResponse::class.java
        )
    }

    // ========================================================================
    // EVENTS OPERATIONS
    // ========================================================================

    /**
     * List events from a calendar.
     * GET /calendars/{calendarId}/events
     *
     * For incremental sync, provide syncToken from previous response.
     * For full sync, provide timeMin/timeMax range.
     */
    suspend fun listEvents(
        calendarId: String,
        syncToken: String? = null,
        pageToken: String? = null,
        timeMin: Instant? = null,
        timeMax: Instant? = null,
        maxResults: Int = 250,
        singleEvents: Boolean = true,
        orderBy: String = "updated"
    ): Result<EventsListResponse> {
        val params = buildList {
            if (syncToken != null) {
                // Incremental sync - only use syncToken
                add("syncToken" to syncToken)
            } else {
                // Full sync - use time range
                if (timeMin != null) {
                    add("timeMin" to timeMin.toString())
                }
                if (timeMax != null) {
                    add("timeMax" to timeMax.toString())
                }
            }
            if (pageToken != null) add("pageToken" to pageToken)
            add("maxResults" to maxResults.toString())
            add("singleEvents" to singleEvents.toString())
            add("orderBy" to orderBy)
            add("showDeleted" to "true") // Required for incremental sync
        }

        return makeGetRequest(
            path = "/calendars/${urlEncode(calendarId)}/events",
            params = params,
            responseType = EventsListResponse::class.java
        )
    }

    /**
     * Get a single event by ID.
     * GET /calendars/{calendarId}/events/{eventId}
     */
    suspend fun getEvent(
        calendarId: String,
        eventId: String
    ): Result<GoogleEventDto> {
        return makeGetRequest(
            path = "/calendars/${urlEncode(calendarId)}/events/${urlEncode(eventId)}",
            params = emptyList(),
            responseType = GoogleEventDto::class.java
        )
    }

    /**
     * Create a new event.
     * POST /calendars/{calendarId}/events
     */
    suspend fun createEvent(
        calendarId: String,
        event: EventCreateRequest
    ): Result<GoogleEventDto> {
        return makePostRequest(
            path = "/calendars/${urlEncode(calendarId)}/events",
            body = event,
            responseType = GoogleEventDto::class.java
        )
    }

    /**
     * Update an existing event.
     * PUT /calendars/{calendarId}/events/{eventId}
     */
    suspend fun updateEvent(
        calendarId: String,
        eventId: String,
        event: EventCreateRequest
    ): Result<GoogleEventDto> {
        return makePutRequest(
            path = "/calendars/${urlEncode(calendarId)}/events/${urlEncode(eventId)}",
            body = event,
            responseType = GoogleEventDto::class.java
        )
    }

    /**
     * Patch an existing event (partial update).
     * PATCH /calendars/{calendarId}/events/{eventId}
     */
    suspend fun patchEvent(
        calendarId: String,
        eventId: String,
        updates: Map<String, Any?>
    ): Result<GoogleEventDto> {
        return makePatchRequest(
            path = "/calendars/${urlEncode(calendarId)}/events/${urlEncode(eventId)}",
            body = updates,
            responseType = GoogleEventDto::class.java
        )
    }

    /**
     * Delete an event.
     * DELETE /calendars/{calendarId}/events/{eventId}
     */
    suspend fun deleteEvent(
        calendarId: String,
        eventId: String
    ): Result<Unit> {
        return makeDeleteRequest(
            path = "/calendars/${urlEncode(calendarId)}/events/${urlEncode(eventId)}"
        )
    }

    // ========================================================================
    // HTTP REQUEST HELPERS
    // ========================================================================

    private suspend fun <T> makeGetRequest(
        path: String,
        params: List<Pair<String, String>>,
        responseType: Class<T>
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            val accessToken = authManager.getValidAccessToken()
                ?: return@withContext Result.failure(AuthException("Not authenticated"))

            val url = buildUrl(path, params)
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            executeRequest(request, responseType)
        } catch (e: Exception) {
            Log.e(TAG, "GET request failed: $path", e)
            Result.failure(e)
        }
    }

    private suspend fun <T> makePostRequest(
        path: String,
        body: Any,
        responseType: Class<T>
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            val accessToken = authManager.getValidAccessToken()
                ?: return@withContext Result.failure(AuthException("Not authenticated"))

            val jsonBody = gson.toJson(body)
            val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url("$BASE_URL$path")
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .post(requestBody)
                .build()

            executeRequest(request, responseType)
        } catch (e: Exception) {
            Log.e(TAG, "POST request failed: $path", e)
            Result.failure(e)
        }
    }

    private suspend fun <T> makePutRequest(
        path: String,
        body: Any,
        responseType: Class<T>
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            val accessToken = authManager.getValidAccessToken()
                ?: return@withContext Result.failure(AuthException("Not authenticated"))

            val jsonBody = gson.toJson(body)
            val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url("$BASE_URL$path")
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .put(requestBody)
                .build()

            executeRequest(request, responseType)
        } catch (e: Exception) {
            Log.e(TAG, "PUT request failed: $path", e)
            Result.failure(e)
        }
    }

    private suspend fun <T> makePatchRequest(
        path: String,
        body: Any,
        responseType: Class<T>
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            val accessToken = authManager.getValidAccessToken()
                ?: return@withContext Result.failure(AuthException("Not authenticated"))

            val jsonBody = gson.toJson(body)
            val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url("$BASE_URL$path")
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .patch(requestBody)
                .build()

            executeRequest(request, responseType)
        } catch (e: Exception) {
            Log.e(TAG, "PATCH request failed: $path", e)
            Result.failure(e)
        }
    }

    private suspend fun makeDeleteRequest(
        path: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val accessToken = authManager.getValidAccessToken()
                ?: return@withContext Result.failure(AuthException("Not authenticated"))

            val request = Request.Builder()
                .url("$BASE_URL$path")
                .addHeader("Authorization", "Bearer $accessToken")
                .delete()
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful || response.code == 204 || response.code == 410) {
                // 204 No Content is success for DELETE
                // 410 Gone means already deleted (acceptable)
                Result.success(Unit)
            } else {
                val errorBody = response.body?.string()
                Log.e(TAG, "DELETE failed: ${response.code} - $errorBody")
                Result.failure(ApiException(response.code, parseError(errorBody)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "DELETE request failed: $path", e)
            Result.failure(e)
        }
    }

    private fun <T> executeRequest(
        request: Request,
        responseType: Class<T>
    ): Result<T> {
        val response = okHttpClient.newCall(request).execute()
        val responseBody = response.body?.string()

        return if (response.isSuccessful && responseBody != null) {
            try {
                val result = gson.fromJson(responseBody, responseType)
                Result.success(result)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse response", e)
                Result.failure(ParseException("Failed to parse response: ${e.message}"))
            }
        } else if (response.code == 410) {
            // 410 Gone - sync token expired
            Result.failure(SyncTokenExpiredException("Sync token expired"))
        } else {
            val error = parseError(responseBody)
            Log.e(TAG, "API error: ${response.code} - $error")
            Result.failure(ApiException(response.code, error))
        }
    }

    private fun buildUrl(path: String, params: List<Pair<String, String>>): String {
        val queryString = if (params.isNotEmpty()) {
            "?" + params.joinToString("&") { (key, value) ->
                "${urlEncode(key)}=${urlEncode(value)}"
            }
        } else {
            ""
        }
        return "$BASE_URL$path$queryString"
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
    }

    private fun parseError(body: String?): String {
        if (body == null) return "Unknown error"
        return try {
            val error = gson.fromJson(body, GoogleApiError::class.java)
            error.error?.message ?: body
        } catch (e: Exception) {
            body
        }
    }

    companion object {
        private const val TAG = "GoogleCalendarService"
        private const val BASE_URL = "https://www.googleapis.com/calendar/v3"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

// ============================================================================
// EXCEPTION TYPES
// ============================================================================

/**
 * Exception for authentication failures.
 */
class AuthException(message: String) : Exception(message)

/**
 * Exception for API errors with status code.
 */
class ApiException(val statusCode: Int, message: String) : Exception(message) {
    val isRateLimited: Boolean get() = statusCode == 429
    val isNotFound: Boolean get() = statusCode == 404
    val isUnauthorized: Boolean get() = statusCode == 401
    val isForbidden: Boolean get() = statusCode == 403
}

/**
 * Exception when sync token has expired (HTTP 410).
 * Caller should clear the sync token and perform a full sync.
 */
class SyncTokenExpiredException(message: String) : Exception(message)

/**
 * Exception for JSON parsing failures.
 */
class ParseException(message: String) : Exception(message)
