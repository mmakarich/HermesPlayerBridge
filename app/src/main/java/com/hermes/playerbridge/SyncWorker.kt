package com.hermes.playerbridge

import android.content.Context
import android.util.Log
import androidx.work.*
import com.hermes.playerbridge.data.AppDatabase
import com.hermes.playerbridge.data.entities.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "SyncWorker"
        private const val WORK_NAME = "health_sync"
        const val MAX_RETRIES = 5

        fun enqueue(context: Context, intervalMin: Int = 15) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<SyncWorker>(Duration.ofMinutes(intervalMin.toLong()))
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        fun enqueueOneShot(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val healthManager = HealthConnectManager(applicationContext)
        val apiClient = HermesApiClient(applicationContext)
        val db = AppDatabase.getInstance(applicationContext)

        try {
            if (!healthManager.isAvailable()) {
                logSync(db, "error", 0, "Health Connect not available")
                return@withContext Result.failure()
            }
            if (!healthManager.areAllPermissionsGranted()) {
                logSync(db, "error", 0, "Permissions not granted")
                return@withContext Result.retry()
            }

            val settings = SettingsManager(applicationContext)
            val now = System.currentTimeMillis()
            val today = LocalDate.now()
            val endInstant = Instant.now()
            val lastSyncMs = settings.lastSyncTimestamp
            val startInstant = if (lastSyncMs > 0) Instant.ofEpochMilli(lastSyncMs) else Instant.now().minus(7, ChronoUnit.DAYS)

            val records = mutableListOf<JSONObject>()

            // Daily aggregate
            val agg = healthManager.readDailyAggregate(today)
            if (agg != null) {
                records.add(JSONObject().apply {
                    put("type", "daily")
                    put("date", today.toString())
                })
            }

            // Sleep (using start/end times only, no duration property issues)
            val sleepRecords = healthManager.readSleep(startInstant, endInstant)
            for (sl in sleepRecords) {
                val durSec = (sl.endTime.toEpochMilli() - sl.startTime.toEpochMilli()) / 1000
                records.add(JSONObject().apply {
                    put("type", "sleep")
                    put("date", today.toString())
                    put("bedtime_ts", sl.startTime.toEpochMilli() / 1000)
                    put("wake_ts", sl.endTime.toEpochMilli() / 1000)
                    put("duration_min", durSec / 60)
                })
            }

            // Exercise
            for (ex in healthManager.readExercise(startInstant, endInstant)) {
                records.add(JSONObject().apply {
                    put("type", "exercise")
                    put("ts", ex.startTime.toEpochMilli() / 1000)
                    val durSec = (ex.endTime.toEpochMilli() - ex.startTime.toEpochMilli()) / 1000
                    put("duration_min", durSec / 60)
                })
            }

            if (records.isEmpty()) {
                logSync(db, "skipped", 0, "No new records")
                return@withContext Result.success()
            }

            val result = apiClient.postBatch(
                webhookUrl = settings.webhookUrl,
                secret = settings.secretToken,
                deviceId = settings.deviceId,
                records = records,
            )

            if (result.success) {
                logSync(db, "success", records.size, "Synced ${records.size} records")
                settings.lastSyncTimestamp = now
                return@withContext Result.success()
            } else {
                logSync(db, "error", records.size, result.error)
                return@withContext if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            logSync(db, "error", 0, e.message ?: "Unknown")
            return@withContext if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    private suspend fun logSync(db: AppDatabase, status: String, count: Int, message: String) {
        db.syncLogDao().insert(SyncLogEntity(status = status, recordsCount = count, message = message))
    }
}
