package com.hermes.playerbridge

import android.content.Context
import android.util.Log
import androidx.work.*
import com.hermes.playerbridge.data.AppDatabase
import com.hermes.playerbridge.data.entities.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
            val request = PeriodicWorkRequestBuilder<SyncWorker>(intervalMin, TimeUnit.MINUTES)
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

            // Daily aggregate (steps, distance)
            val agg = healthManager.readDailyAggregate(today)
            if (agg != null) {
                val dailyRecord = JSONObject().apply {
                    put("type", "daily")
                    put("date", today.toString())
                }
                records.add(dailyRecord)
            }

            // HRV
            for (hrv in healthManager.readHrv(startInstant, endInstant)) {
                records.add(JSONObject().apply {
                    put("type", "hrv")
                    put("ts", hrv.time.toEpochMilli() / 1000)
                    put("value", hrv.rmssd)
                    put("unit", "ms")
                })
            }

            // SpO2
            for (spo2 in healthManager.readSpo2(startInstant, endInstant)) {
                records.add(JSONObject().apply {
                    put("type", "spo2")
                    put("ts", spo2.time.toEpochMilli() / 1000)
                    put("value", spo2.percentage?.times(100) ?: 0)
                    put("unit", "%")
                })
            }

            // Weight
            for (w in healthManager.readWeight(startInstant, endInstant)) {
                records.add(JSONObject().apply {
                    put("type", "weight")
                    put("ts", w.time.toEpochMilli() / 1000)
                    put("value", w.weight?.inKilograms ?: 0.0)
                    put("unit", "kg")
                })
            }

            // Body fat
            for (bf in healthManager.readBodyFat(startInstant, endInstant)) {
                records.add(JSONObject().apply {
                    put("type", "body_fat")
                    put("ts", bf.time.toEpochMilli() / 1000)
                    put("value", bf.percentage ?: 0.0)
                    put("unit", "%")
                })
            }

            // Sleep
            for (sl in healthManager.readSleep(startInstant, endInstant)) {
                records.add(JSONObject().apply {
                    put("type", "sleep")
                    put("date", today.toString())
                    put("bedtime_ts", sl.startTime.toEpochMilli() / 1000)
                    put("wake_ts", sl.endTime.toEpochMilli() / 1000)
                    put("duration_min", sl.duration.toMinutes())
                })
            }

            // Exercise
            for (ex in healthManager.readExercise(startInstant, endInstant)) {
                records.add(JSONObject().apply {
                    put("type", "exercise")
                    put("ts", ex.startTime.toEpochMilli() / 1000)
                    put("duration_min", ex.duration.toMinutes())
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
