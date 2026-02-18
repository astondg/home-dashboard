package com.homedashboard.app.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.homedashboard.app.auth.GoogleAuthManager
import com.homedashboard.app.auth.ICloudAuthManager
import com.homedashboard.app.auth.TokenStorage
import com.homedashboard.app.data.local.AppDatabase
import com.homedashboard.app.data.remote.GoogleCalendarService
import com.homedashboard.app.data.remote.GoogleEventMapper
import com.homedashboard.app.data.remote.caldav.CalDavEventMapper
import com.homedashboard.app.data.remote.caldav.CalDavService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for background calendar sync.
 * Runs periodically to sync changes with Google Calendar.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting background sync...")

        return try {
            // Initialize dependencies
            val tokenStorage = TokenStorage(applicationContext)

            // Check if user is authenticated with any provider
            val hasGoogle = tokenStorage.hasTokens()
            val hasICloud = tokenStorage.hasICloudCredentials()
            if (!hasGoogle && !hasICloud) {
                Log.d(TAG, "Not authenticated with any provider, skipping sync")
                return Result.success()
            }

            // Build dependencies
            val database = AppDatabase.getDatabase(applicationContext)
            val okHttpClient = buildOkHttpClient()

            // Google Calendar
            val authManager = if (hasGoogle) GoogleAuthManager(applicationContext, tokenStorage) else null
            val googleService = if (hasGoogle && authManager != null) GoogleCalendarService(okHttpClient, authManager) else null
            val eventMapper = if (hasGoogle) GoogleEventMapper() else null

            // iCloud Calendar
            val iCloudSyncProvider = if (hasICloud) {
                val iCloudAuthManager = ICloudAuthManager(tokenStorage, okHttpClient)
                val calDavService = CalDavService(okHttpClient, iCloudAuthManager)
                val calDavEventMapper = CalDavEventMapper()
                ICloudSyncProvider(
                    calendarDao = database.calendarDao(),
                    calDavService = calDavService,
                    eventMapper = calDavEventMapper,
                    tokenStorage = tokenStorage,
                    authManager = iCloudAuthManager
                )
            } else null

            val syncManager = SyncManager(
                calendarDao = database.calendarDao(),
                googleService = googleService,
                eventMapper = eventMapper,
                tokenStorage = tokenStorage,
                iCloudSyncProvider = iCloudSyncProvider
            )

            // Perform sync
            val result = syncManager.performFullSync()

            result.fold(
                onSuccess = { syncResult ->
                    Log.d(TAG, "Sync completed: ${syncResult.totalChanges} changes")
                    Result.success()
                },
                onFailure = { error ->
                    Log.e(TAG, "Sync failed", error)
                    // Retry with exponential backoff
                    if (runAttemptCount < MAX_RETRY_COUNT) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Sync worker exception", e)
            if (runAttemptCount < MAX_RETRY_COUNT) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private fun buildOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val MAX_RETRY_COUNT = 3

        const val WORK_NAME = "calendar_sync"
        const val WORK_NAME_PERIODIC = "calendar_sync_periodic"

        /**
         * Schedule periodic background sync.
         *
         * @param context Application context
         * @param intervalMinutes Sync interval in minutes (minimum 15)
         * @param requireWifi Only sync when connected to WiFi
         * @param requireCharging Only sync when device is charging
         */
        fun schedulePeriodicSync(
            context: Context,
            intervalMinutes: Long = 15,
            requireWifi: Boolean = false,
            requireCharging: Boolean = false
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (requireWifi) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .setRequiresCharging(requireCharging)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                intervalMinutes.coerceAtLeast(15), // WorkManager minimum is 15 minutes
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTag(WORK_NAME_PERIODIC)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                syncRequest
            )

            Log.d(TAG, "Scheduled periodic sync every $intervalMinutes minutes")
        }

        /**
         * Trigger an immediate one-time sync.
         *
         * @param context Application context
         * @return WorkRequest ID for tracking
         */
        fun triggerImmediateSync(context: Context): java.util.UUID {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                syncRequest
            )

            Log.d(TAG, "Triggered immediate sync")
            return syncRequest.id
        }

        /**
         * Cancel all scheduled sync work.
         */
        fun cancelSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
            Log.d(TAG, "Cancelled all sync work")
        }

        /**
         * Get sync work status.
         */
        fun getSyncWorkInfo(context: Context) =
            WorkManager.getInstance(context).getWorkInfosForUniqueWork(WORK_NAME)

        /**
         * Get periodic sync work status.
         */
        fun getPeriodicSyncWorkInfo(context: Context) =
            WorkManager.getInstance(context).getWorkInfosForUniqueWork(WORK_NAME_PERIODIC)
    }
}
