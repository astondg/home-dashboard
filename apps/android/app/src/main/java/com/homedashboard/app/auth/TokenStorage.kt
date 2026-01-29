package com.homedashboard.app.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Secure storage for OAuth tokens using Android's EncryptedSharedPreferences.
 * Tokens are encrypted at rest using AES-256 GCM.
 */
class TokenStorage(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Save OAuth tokens after successful authentication.
     * @param accessToken The access token for API calls
     * @param refreshToken Optional refresh token for token renewal (null for Google Sign-In)
     * @param expiryTimeMs Token expiry time in milliseconds since epoch
     * @param accountEmail The authenticated user's email address
     */
    suspend fun saveTokens(
        accessToken: String,
        refreshToken: String?,
        expiryTimeMs: Long,
        accountEmail: String
    ) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_TOKEN_EXPIRY, expiryTimeMs)
            .putString(KEY_ACCOUNT_EMAIL, accountEmail)
            .apply()
    }

    /**
     * Get the current access token.
     * @return Access token or null if not authenticated
     */
    suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_ACCESS_TOKEN, null)
    }

    /**
     * Get the refresh token.
     * @return Refresh token or null if not available
     */
    suspend fun getRefreshToken(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_REFRESH_TOKEN, null)
    }

    /**
     * Get the token expiry time.
     * @return Expiry time in milliseconds since epoch, or 0 if not set
     */
    suspend fun getTokenExpiry(): Long = withContext(Dispatchers.IO) {
        prefs.getLong(KEY_TOKEN_EXPIRY, 0L)
    }

    /**
     * Get the authenticated account email.
     * @return Email address or null if not authenticated
     */
    suspend fun getAccountEmail(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_ACCOUNT_EMAIL, null)
    }

    /**
     * Check if the current token is expired or about to expire.
     * Considers token expired if within 5 minutes of expiry.
     * @return true if token needs refresh, false otherwise
     */
    suspend fun isTokenExpired(): Boolean = withContext(Dispatchers.IO) {
        val expiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0L)
        if (expiry == 0L) return@withContext true

        // Consider expired if within 5 minutes of expiry
        val bufferMs = 5 * 60 * 1000
        System.currentTimeMillis() >= (expiry - bufferMs)
    }

    /**
     * Check if user has any stored tokens (is authenticated).
     * @return true if tokens exist, false otherwise
     */
    suspend fun hasTokens(): Boolean = withContext(Dispatchers.IO) {
        prefs.getString(KEY_ACCESS_TOKEN, null) != null
    }

    /**
     * Update only the access token (after refresh).
     * @param accessToken New access token
     * @param expiryTimeMs New expiry time
     */
    suspend fun updateAccessToken(
        accessToken: String,
        expiryTimeMs: Long
    ) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putLong(KEY_TOKEN_EXPIRY, expiryTimeMs)
            .apply()
    }

    /**
     * Clear all stored tokens (sign out).
     */
    suspend fun clearTokens() = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_EXPIRY)
            .remove(KEY_ACCOUNT_EMAIL)
            .apply()
    }

    /**
     * Store sync token for incremental calendar sync.
     * @param calendarId The calendar's ID
     * @param syncToken The sync token from Google Calendar API
     */
    suspend fun saveSyncToken(calendarId: String, syncToken: String) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString("$KEY_SYNC_TOKEN_PREFIX$calendarId", syncToken)
            .apply()
    }

    /**
     * Get sync token for a specific calendar.
     * @param calendarId The calendar's ID
     * @return Sync token or null if no previous sync
     */
    suspend fun getSyncToken(calendarId: String): String? = withContext(Dispatchers.IO) {
        prefs.getString("$KEY_SYNC_TOKEN_PREFIX$calendarId", null)
    }

    /**
     * Clear sync token for a calendar (forces full sync).
     * @param calendarId The calendar's ID
     */
    suspend fun clearSyncToken(calendarId: String) = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove("$KEY_SYNC_TOKEN_PREFIX$calendarId")
            .apply()
    }

    /**
     * Store last sync timestamp.
     * @param timestampMs Timestamp in milliseconds since epoch
     */
    suspend fun saveLastSyncTime(timestampMs: Long) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putLong(KEY_LAST_SYNC_TIME, timestampMs)
            .apply()
    }

    /**
     * Get last sync timestamp.
     * @return Timestamp in milliseconds or 0 if never synced
     */
    suspend fun getLastSyncTime(): Long = withContext(Dispatchers.IO) {
        prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
    }

    // ==================== iCloud/CalDAV Credentials ====================

    /**
     * Save iCloud/CalDAV credentials after successful authentication.
     * Uses HTTP Basic Auth, so we store email and app-specific password.
     * @param email The iCloud account email (Apple ID)
     * @param appPassword The app-specific password (16 characters from Apple ID settings)
     * @param serverUrl The discovered CalDAV server URL (e.g., p34-caldav.icloud.com)
     * @param principalUrl The user's principal URL for calendar discovery
     */
    suspend fun saveICloudCredentials(
        email: String,
        appPassword: String,
        serverUrl: String,
        principalUrl: String? = null
    ) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_ICLOUD_EMAIL, email)
            .putString(KEY_ICLOUD_PASSWORD, appPassword)
            .putString(KEY_ICLOUD_SERVER_URL, serverUrl)
            .apply {
                if (principalUrl != null) {
                    putString(KEY_ICLOUD_PRINCIPAL_URL, principalUrl)
                }
            }
            .apply()
    }

    /**
     * Get iCloud credentials for HTTP Basic Auth.
     * @return Pair of (email, appPassword) or null if not authenticated
     */
    suspend fun getICloudCredentials(): Pair<String, String>? = withContext(Dispatchers.IO) {
        val email = prefs.getString(KEY_ICLOUD_EMAIL, null)
        val password = prefs.getString(KEY_ICLOUD_PASSWORD, null)
        if (email != null && password != null) {
            Pair(email, password)
        } else {
            null
        }
    }

    /**
     * Get the iCloud account email.
     * @return Email address or null if not authenticated
     */
    suspend fun getICloudEmail(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_ICLOUD_EMAIL, null)
    }

    /**
     * Get the discovered CalDAV server URL.
     * @return Server URL or null if not discovered
     */
    suspend fun getICloudServerUrl(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_ICLOUD_SERVER_URL, null)
    }

    /**
     * Get the user's principal URL for calendar discovery.
     * @return Principal URL or null if not discovered
     */
    suspend fun getICloudPrincipalUrl(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_ICLOUD_PRINCIPAL_URL, null)
    }

    /**
     * Update the server URL after discovery.
     * @param serverUrl The discovered CalDAV server URL
     */
    suspend fun updateICloudServerUrl(serverUrl: String) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_ICLOUD_SERVER_URL, serverUrl)
            .apply()
    }

    /**
     * Update the principal URL after discovery.
     * @param principalUrl The discovered principal URL
     */
    suspend fun updateICloudPrincipalUrl(principalUrl: String) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_ICLOUD_PRINCIPAL_URL, principalUrl)
            .apply()
    }

    /**
     * Check if user has stored iCloud credentials.
     * @return true if credentials exist, false otherwise
     */
    suspend fun hasICloudCredentials(): Boolean = withContext(Dispatchers.IO) {
        prefs.getString(KEY_ICLOUD_EMAIL, null) != null &&
                prefs.getString(KEY_ICLOUD_PASSWORD, null) != null
    }

    /**
     * Clear all stored iCloud credentials (sign out from iCloud).
     */
    suspend fun clearICloudCredentials() = withContext(Dispatchers.IO) {
        // Get all keys that start with iCloud or caldav prefixes
        val keysToRemove = prefs.all.keys.filter { key ->
            key.startsWith("icloud_") || key.startsWith("caldav_sync_token_")
        }

        prefs.edit().apply {
            // Remove specific iCloud keys
            remove(KEY_ICLOUD_EMAIL)
            remove(KEY_ICLOUD_PASSWORD)
            remove(KEY_ICLOUD_SERVER_URL)
            remove(KEY_ICLOUD_PRINCIPAL_URL)

            // Remove all CalDAV sync tokens
            keysToRemove.forEach { remove(it) }
        }.apply()
    }

    // ==================== CalDAV Sync Tokens ====================

    /**
     * Store CalDAV sync token for incremental sync.
     * CalDAV sync tokens are different from Google's - they're URIs that the server provides.
     * @param calendarUrl The calendar's URL (used as unique identifier)
     * @param syncToken The sync token from CalDAV server
     */
    suspend fun saveCalDavSyncToken(calendarUrl: String, syncToken: String) = withContext(Dispatchers.IO) {
        // Use URL hash as key to avoid special characters
        val key = "$KEY_CALDAV_SYNC_TOKEN_PREFIX${calendarUrl.hashCode()}"
        prefs.edit()
            .putString(key, syncToken)
            .apply()
    }

    /**
     * Get CalDAV sync token for a specific calendar.
     * @param calendarUrl The calendar's URL
     * @return Sync token or null if no previous sync
     */
    suspend fun getCalDavSyncToken(calendarUrl: String): String? = withContext(Dispatchers.IO) {
        val key = "$KEY_CALDAV_SYNC_TOKEN_PREFIX${calendarUrl.hashCode()}"
        prefs.getString(key, null)
    }

    /**
     * Clear CalDAV sync token for a calendar (forces full sync).
     * @param calendarUrl The calendar's URL
     */
    suspend fun clearCalDavSyncToken(calendarUrl: String) = withContext(Dispatchers.IO) {
        val key = "$KEY_CALDAV_SYNC_TOKEN_PREFIX${calendarUrl.hashCode()}"
        prefs.edit()
            .remove(key)
            .apply()
    }

    /**
     * Save iCloud-specific last sync time.
     * @param timestampMs Timestamp in milliseconds since epoch
     */
    suspend fun saveICloudLastSyncTime(timestampMs: Long) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putLong(KEY_ICLOUD_LAST_SYNC_TIME, timestampMs)
            .apply()
    }

    /**
     * Get iCloud last sync timestamp.
     * @return Timestamp in milliseconds or 0 if never synced
     */
    suspend fun getICloudLastSyncTime(): Long = withContext(Dispatchers.IO) {
        prefs.getLong(KEY_ICLOUD_LAST_SYNC_TIME, 0L)
    }

    companion object {
        private const val PREFS_FILE_NAME = "homedashboard_secure_prefs"

        // Google OAuth token keys
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val KEY_ACCOUNT_EMAIL = "account_email"

        // Google sync state keys
        private const val KEY_SYNC_TOKEN_PREFIX = "sync_token_"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"

        // iCloud/CalDAV credential keys
        private const val KEY_ICLOUD_EMAIL = "icloud_email"
        private const val KEY_ICLOUD_PASSWORD = "icloud_password"
        private const val KEY_ICLOUD_SERVER_URL = "icloud_server_url"
        private const val KEY_ICLOUD_PRINCIPAL_URL = "icloud_principal_url"
        private const val KEY_ICLOUD_LAST_SYNC_TIME = "icloud_last_sync_time"

        // CalDAV sync token prefix (separate from Google's sync tokens)
        private const val KEY_CALDAV_SYNC_TOKEN_PREFIX = "caldav_sync_token_"
    }
}
