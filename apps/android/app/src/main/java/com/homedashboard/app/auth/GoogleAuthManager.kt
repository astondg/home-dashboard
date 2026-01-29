package com.homedashboard.app.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Manages Google Sign-In authentication for Google Calendar access.
 * Handles sign-in flow, token retrieval, and sign-out.
 */
class GoogleAuthManager(
    private val context: Context,
    private val tokenStorage: TokenStorage
) {
    private val googleSignInClient: GoogleSignInClient

    // Authentication state
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(SCOPE_CALENDAR_EVENTS),
                Scope(SCOPE_CALENDAR_READONLY)
            )
            .build()

        googleSignInClient = GoogleSignIn.getClient(context, gso)
    }

    /**
     * Check if user is currently signed in.
     */
    suspend fun checkAuthState() {
        val hasTokens = tokenStorage.hasTokens()
        val account = GoogleSignIn.getLastSignedInAccount(context)

        _authState.value = when {
            account != null && hasTokens -> {
                AuthState.Authenticated(
                    email = account.email ?: "",
                    displayName = account.displayName
                )
            }
            else -> AuthState.NotAuthenticated
        }
    }

    /**
     * Get the sign-in intent to launch.
     * Use this with ActivityResultLauncher.launch()
     */
    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    /**
     * Handle the result from the sign-in activity.
     * Call this from onActivityResult or ActivityResultCallback.
     */
    suspend fun handleSignInResult(data: Intent?): Result<GoogleSignInAccount> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.await()

            // Get access token for API calls
            val accessToken = getAccessToken(account)
            if (accessToken != null) {
                // Store token - Google Sign-In tokens don't have explicit expiry,
                // but we set a reasonable TTL (1 hour)
                val expiryMs = System.currentTimeMillis() + (60 * 60 * 1000)
                tokenStorage.saveTokens(
                    accessToken = accessToken,
                    refreshToken = null, // Google Sign-In handles refresh automatically
                    expiryTimeMs = expiryMs,
                    accountEmail = account.email ?: ""
                )

                _authState.value = AuthState.Authenticated(
                    email = account.email ?: "",
                    displayName = account.displayName
                )

                Log.d(TAG, "Sign-in successful: ${account.email}")
                Result.success(account)
            } else {
                Log.e(TAG, "Failed to get access token")
                Result.failure(Exception("Failed to get access token"))
            }
        } catch (e: ApiException) {
            Log.e(TAG, "Sign-in failed with code: ${e.statusCode}", e)
            _authState.value = AuthState.Error("Sign-in failed: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in failed", e)
            _authState.value = AuthState.Error("Sign-in failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get a fresh access token for API calls.
     * Silently refreshes if needed.
     */
    suspend fun getValidAccessToken(): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Try to get token silently (will refresh if needed)
                val account = GoogleSignIn.getLastSignedInAccount(context)
                    ?: return@withContext null

                val token = getAccessToken(account)
                if (token != null) {
                    // Update stored token
                    val expiryMs = System.currentTimeMillis() + (60 * 60 * 1000)
                    tokenStorage.updateAccessToken(token, expiryMs)
                }
                token
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get access token", e)
                null
            }
        }
    }

    /**
     * Get access token from GoogleSignInAccount.
     * This uses GoogleAuthUtil to get an OAuth2 token for the Calendar API.
     */
    private suspend fun getAccessToken(account: GoogleSignInAccount): String? {
        return withContext(Dispatchers.IO) {
            try {
                val scopes = "oauth2:$SCOPE_CALENDAR_EVENTS $SCOPE_CALENDAR_READONLY"
                com.google.android.gms.auth.GoogleAuthUtil.getToken(
                    context,
                    account.account!!,
                    scopes
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get OAuth token", e)
                null
            }
        }
    }

    /**
     * Sign out and clear stored tokens.
     */
    suspend fun signOut() {
        withContext(Dispatchers.IO) {
            try {
                googleSignInClient.signOut().await()
                tokenStorage.clearTokens()
                _authState.value = AuthState.NotAuthenticated
                Log.d(TAG, "Sign-out successful")
            } catch (e: Exception) {
                Log.e(TAG, "Sign-out failed", e)
            }
        }
    }

    /**
     * Revoke access and disconnect the app.
     * Use this for "forget this account" functionality.
     */
    suspend fun revokeAccess() {
        withContext(Dispatchers.IO) {
            try {
                googleSignInClient.revokeAccess().await()
                tokenStorage.clearTokens()
                _authState.value = AuthState.NotAuthenticated
                Log.d(TAG, "Access revoked")
            } catch (e: Exception) {
                Log.e(TAG, "Revoke access failed", e)
            }
        }
    }

    /**
     * Get the currently signed in account email.
     */
    fun getCurrentAccountEmail(): String? {
        return GoogleSignIn.getLastSignedInAccount(context)?.email
    }

    /**
     * Check if the app has the required calendar permissions.
     */
    fun hasCalendarPermission(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        return GoogleSignIn.hasPermissions(
            account,
            Scope(SCOPE_CALENDAR_EVENTS),
            Scope(SCOPE_CALENDAR_READONLY)
        )
    }

    companion object {
        private const val TAG = "GoogleAuthManager"

        // Google Calendar API scopes
        const val SCOPE_CALENDAR_EVENTS = "https://www.googleapis.com/auth/calendar.events"
        const val SCOPE_CALENDAR_READONLY = "https://www.googleapis.com/auth/calendar.readonly"
    }
}

/**
 * Represents the current authentication state.
 */
sealed class AuthState {
    object Unknown : AuthState()
    object NotAuthenticated : AuthState()
    data class Authenticated(
        val email: String,
        val displayName: String?
    ) : AuthState()
    data class Error(val message: String) : AuthState()
}
