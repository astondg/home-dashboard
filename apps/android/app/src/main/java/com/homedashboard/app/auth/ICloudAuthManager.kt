package com.homedashboard.app.auth

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Manages iCloud Calendar authentication via CalDAV (HTTP Basic Auth).
 * Handles server discovery, credential validation, and principal URL detection.
 */
class ICloudAuthManager(
    private val tokenStorage: TokenStorage,
    private val okHttpClient: OkHttpClient
) {
    // Authentication state
    private val _authState = MutableStateFlow<ICloudAuthState>(ICloudAuthState.Unknown)
    val authState: StateFlow<ICloudAuthState> = _authState.asStateFlow()

    /**
     * Check if user is currently authenticated with iCloud.
     */
    suspend fun checkAuthState() {
        val hasCredentials = tokenStorage.hasICloudCredentials()
        val email = tokenStorage.getICloudEmail()

        _authState.value = if (hasCredentials && email != null) {
            ICloudAuthState.Authenticated(email = email)
        } else {
            ICloudAuthState.NotAuthenticated
        }
    }

    /**
     * Generate HTTP Basic Auth header for CalDAV requests.
     * @return Authorization header value or null if not authenticated
     */
    suspend fun getAuthorizationHeader(): String? {
        val credentials = tokenStorage.getICloudCredentials() ?: return null
        val (email, password) = credentials
        val credentialsString = "$email:$password"
        val encodedCredentials = Base64.encodeToString(
            credentialsString.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
        return "Basic $encodedCredentials"
    }

    /**
     * Authenticate with iCloud using email and app-specific password.
     * Performs server discovery and validates credentials.
     *
     * @param email The iCloud email (Apple ID)
     * @param appPassword The 16-character app-specific password from Apple ID settings
     * @return Result indicating success or failure with error message
     */
    suspend fun authenticate(
        email: String,
        appPassword: String
    ): Result<ICloudAuthResult> = withContext(Dispatchers.IO) {
        _authState.value = ICloudAuthState.Authenticating

        try {
            // Step 1: Discover the actual CalDAV server
            Log.d(TAG, "Starting iCloud authentication for: $email")
            val serverUrl = discoverServer(email, appPassword)
                ?: return@withContext Result.failure(
                    ICloudAuthException("Failed to discover iCloud CalDAV server")
                )

            Log.d(TAG, "Discovered server: $serverUrl")

            // Step 2: Discover the user's principal URL
            val principalUrl = discoverPrincipal(serverUrl, email, appPassword)
                ?: return@withContext Result.failure(
                    ICloudAuthException("Failed to discover principal URL. Check your credentials.")
                )

            Log.d(TAG, "Discovered principal: $principalUrl")

            // Step 3: Discover the calendar home set
            val calendarHomeUrl = discoverCalendarHome(principalUrl, email, appPassword)

            Log.d(TAG, "Calendar home: $calendarHomeUrl")

            // Step 4: Save credentials
            tokenStorage.saveICloudCredentials(
                email = email,
                appPassword = appPassword,
                serverUrl = serverUrl,
                principalUrl = principalUrl
            )

            _authState.value = ICloudAuthState.Authenticated(email = email)

            Result.success(
                ICloudAuthResult(
                    email = email,
                    serverUrl = serverUrl,
                    principalUrl = principalUrl,
                    calendarHomeUrl = calendarHomeUrl
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Authentication failed", e)
            _authState.value = ICloudAuthState.Error(e.message ?: "Authentication failed")
            Result.failure(ICloudAuthException(e.message ?: "Authentication failed", e))
        }
    }

    /**
     * Discover the actual CalDAV server via .well-known redirect.
     * iCloud redirects from caldav.icloud.com to p{N}-caldav.icloud.com
     */
    private suspend fun discoverServer(
        email: String,
        password: String
    ): String? = withContext(Dispatchers.IO) {
        val authHeader = createAuthHeader(email, password)

        // Try .well-known/caldav endpoint first
        val wellKnownUrl = "https://caldav.icloud.com/.well-known/caldav"

        try {
            val request = Request.Builder()
                .url(wellKnownUrl)
                .addHeader("Authorization", authHeader)
                .method("PROPFIND", createPropfindBody(listOf("current-user-principal")))
                .addHeader("Content-Type", "application/xml; charset=utf-8")
                .addHeader("Depth", "0")
                .build()

            // Use a client that follows redirects
            val response = okHttpClient.newCall(request).execute()

            // Check for redirect
            if (response.isRedirect || response.code == 301 || response.code == 302) {
                val redirectUrl = response.header("Location")
                if (redirectUrl != null) {
                    // Extract server from redirect URL
                    val uri = java.net.URI(redirectUrl)
                    return@withContext "https://${uri.host}"
                }
            }

            // Check response for server info
            if (response.isSuccessful || response.code == 207) {
                // Extract server from response URL or use default
                val responseUrl = response.request.url.toString()
                val uri = java.net.URI(responseUrl)
                return@withContext "https://${uri.host}"
            }

            // Fallback: use the base iCloud CalDAV server
            if (response.code == 401) {
                Log.e(TAG, "Authentication failed - invalid credentials")
                return@withContext null
            }

            "https://caldav.icloud.com"
        } catch (e: Exception) {
            Log.e(TAG, "Server discovery failed", e)
            // Fallback to default
            "https://caldav.icloud.com"
        }
    }

    /**
     * Discover the user's principal URL via PROPFIND.
     */
    private suspend fun discoverPrincipal(
        serverUrl: String,
        email: String,
        password: String
    ): String? = withContext(Dispatchers.IO) {
        val authHeader = createAuthHeader(email, password)

        // PROPFIND on root to get current-user-principal
        val url = "$serverUrl/"

        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", authHeader)
                .method("PROPFIND", createPropfindBody(listOf("current-user-principal")))
                .addHeader("Content-Type", "application/xml; charset=utf-8")
                .addHeader("Depth", "0")
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (response.code == 401 || response.code == 403) {
                Log.e(TAG, "Authentication failed with status: ${response.code}")
                return@withContext null
            }

            if (!response.isSuccessful && response.code != 207) {
                Log.e(TAG, "PROPFIND failed with status: ${response.code}")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            Log.d(TAG, "Principal response: $body")

            // Parse XML to extract principal href
            val principalHref = extractPrincipalHref(body)

            if (principalHref != null) {
                // Resolve relative URL if needed
                if (principalHref.startsWith("http")) {
                    principalHref
                } else {
                    "$serverUrl$principalHref"
                }
            } else {
                // Fallback: construct principal URL from email
                // iCloud format: /[dsid]/principal/
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Principal discovery failed", e)
            null
        }
    }

    /**
     * Discover the calendar home set URL.
     */
    private suspend fun discoverCalendarHome(
        principalUrl: String,
        email: String,
        password: String
    ): String? = withContext(Dispatchers.IO) {
        val authHeader = createAuthHeader(email, password)

        try {
            val request = Request.Builder()
                .url(principalUrl)
                .addHeader("Authorization", authHeader)
                .method("PROPFIND", createPropfindBody(listOf("calendar-home-set")))
                .addHeader("Content-Type", "application/xml; charset=utf-8")
                .addHeader("Depth", "0")
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful && response.code != 207) {
                Log.e(TAG, "Calendar home discovery failed: ${response.code}")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            Log.d(TAG, "Calendar home response: $body")

            extractCalendarHomeHref(body)?.let { href ->
                if (href.startsWith("http")) href else {
                    val uri = java.net.URI(principalUrl)
                    "https://${uri.host}$href"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Calendar home discovery failed", e)
            null
        }
    }

    /**
     * Validate stored credentials by making a test PROPFIND request.
     * @return true if credentials are valid
     */
    suspend fun validateCredentials(): Boolean = withContext(Dispatchers.IO) {
        val credentials = tokenStorage.getICloudCredentials() ?: return@withContext false
        val serverUrl = tokenStorage.getICloudServerUrl() ?: return@withContext false

        try {
            val (email, password) = credentials
            val authHeader = createAuthHeader(email, password)

            val request = Request.Builder()
                .url("$serverUrl/")
                .addHeader("Authorization", authHeader)
                .method("PROPFIND", createPropfindBody(listOf("resourcetype")))
                .addHeader("Content-Type", "application/xml; charset=utf-8")
                .addHeader("Depth", "0")
                .build()

            val response = okHttpClient.newCall(request).execute()
            response.isSuccessful || response.code == 207
        } catch (e: Exception) {
            Log.e(TAG, "Credential validation failed", e)
            false
        }
    }

    /**
     * Sign out from iCloud (clear stored credentials).
     */
    suspend fun signOut() {
        tokenStorage.clearICloudCredentials()
        _authState.value = ICloudAuthState.NotAuthenticated
        Log.d(TAG, "Signed out from iCloud")
    }

    /**
     * Get the stored server URL.
     */
    suspend fun getServerUrl(): String? = tokenStorage.getICloudServerUrl()

    /**
     * Get the stored principal URL.
     */
    suspend fun getPrincipalUrl(): String? = tokenStorage.getICloudPrincipalUrl()

    // ==================== Helper Methods ====================

    private fun createAuthHeader(email: String, password: String): String {
        val credentialsString = "$email:$password"
        val encodedCredentials = Base64.encodeToString(
            credentialsString.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
        return "Basic $encodedCredentials"
    }

    private fun createPropfindBody(properties: List<String>): okhttp3.RequestBody {
        val propElements = properties.joinToString("\n") { prop ->
            when (prop) {
                "current-user-principal" -> "<d:current-user-principal/>"
                "calendar-home-set" -> "<c:calendar-home-set xmlns:c=\"urn:ietf:params:xml:ns:caldav\"/>"
                "resourcetype" -> "<d:resourcetype/>"
                "displayname" -> "<d:displayname/>"
                "getetag" -> "<d:getetag/>"
                else -> "<d:$prop/>"
            }
        }

        val xml = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    $propElements
  </d:prop>
</d:propfind>"""

        return xml.toRequestBody("application/xml; charset=utf-8".toMediaType())
    }

    private fun extractPrincipalHref(xmlBody: String): String? {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(xmlBody)))

            // Look for current-user-principal/href
            val hrefs = doc.getElementsByTagNameNS("DAV:", "href")
            for (i in 0 until hrefs.length) {
                val element = hrefs.item(i) as? Element
                val parent = element?.parentNode as? Element
                if (parent?.localName == "current-user-principal") {
                    return element?.textContent?.trim()
                }
            }

            // Fallback: look for any href in response
            if (hrefs.length > 0) {
                val href = (hrefs.item(0) as? Element)?.textContent?.trim()
                if (href != null && href.contains("/principal")) {
                    return href
                }
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse principal XML", e)
            null
        }
    }

    private fun extractCalendarHomeHref(xmlBody: String): String? {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(xmlBody)))

            // Look for calendar-home-set/href
            val homeSet = doc.getElementsByTagNameNS("urn:ietf:params:xml:ns:caldav", "calendar-home-set")
            if (homeSet.length > 0) {
                val homeElement = homeSet.item(0) as? Element
                val hrefs = homeElement?.getElementsByTagNameNS("DAV:", "href")
                if (hrefs != null && hrefs.length > 0) {
                    return (hrefs.item(0) as? Element)?.textContent?.trim()
                }
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse calendar home XML", e)
            null
        }
    }

    companion object {
        private const val TAG = "ICloudAuthManager"
    }
}

/**
 * Sealed class representing iCloud authentication state.
 */
sealed class ICloudAuthState {
    object Unknown : ICloudAuthState()
    object NotAuthenticated : ICloudAuthState()
    object Authenticating : ICloudAuthState()
    data class Authenticated(val email: String) : ICloudAuthState()
    data class Error(val message: String) : ICloudAuthState()
}

/**
 * Result of successful iCloud authentication.
 */
data class ICloudAuthResult(
    val email: String,
    val serverUrl: String,
    val principalUrl: String,
    val calendarHomeUrl: String?
)

/**
 * Exception for iCloud authentication errors.
 */
class ICloudAuthException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
