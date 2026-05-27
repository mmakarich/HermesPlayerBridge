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

/**
 * WorkManager worker that reads Health Connect data since last sync
 * and posts it to the Hermes webhook.
 *
 * Runs every 15 minutes (configurable). Retries with exponential backoff.
 */
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
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /** Run a single sync now (for manual button). */
        fun enqueueOneShot(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = SettingsManager(applicationContext)
        val healthManager = HealthConnectManager(applicationContext)
        val apiClient = HermesApiClient(applicationContext)
        val db = AppDatabase.getInstance(applicationContext)

        try {
            // 1. Check availability
            if (!healthManager.isAvailable()) {
                logSync(db, "error", 0, "Health Connect not available")
                return@withContext Result.failure()
            }

            // 2. Check permissions
            if (!healthManager.areAllPermissionsGranted()) {
                logSync(db, "error", 0, "Permissions not granted")
                return@withContext Result.retry()
            }

            // 3. Determine sync window
            val lastSyncMs = settings.lastSyncTimestamp
            val now = System.currentTimeMillis()

            // If never synced, pull last 7 days
            val startInstant = if (lastSyncMs > 0) {
                Instant.ofEpochMilli(lastSyncMs)
            } else {
                Instant.now().minus(7, ChronoUnit.DAYS)
            }
            val endInstant = Instant.now()

            // 4. Read data from Health Connect
            val records = mutableListOf<JSONObject>()
            val today = LocalDate.now()

            // 4a. Daily aggregate (steps, calories, distance, HR)
            val dailyStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val dailyEnd = dailyStart.plus(1, ChronoUnit.DAYS)
            val agg = healthManager.readDailyAggregate(today)
            if (agg != null) {
                val dailyRecord = JSONObject().apply {
                    put("type", "daily")
                    put("date", today.toString())
                    agg.getResult(StepsRecord.COUNT_TOTAL)?.let { put("steps", it) }
                    agg.getResult(TotalCaloriesBurnedRecord.CALORIES_TOTAL)?.let { put("calories_total", it.toInt()) }
                    agg.getResult(DistanceRecord.DISTANCE_TOTAL)?.let { put("distance_m", (it * 1000).toInt()) }
                    agg.getResult(HeartRateRecord.HEART_RATE_AVG)?.let { put("heart_rate_avg", it) }
                    agg.getResult(HeartRateRecord.HEART_RATE_MIN)?.let { put("heart_rate_min", it.toInt()) }
                    agg.getResult(HeartRateRecord.HEART_RATE_MAX)?.let { put("heart_rate_max", it.toInt()) }
                }
                records.add(dailyRecord)
            }

            // 4b. HRV (individual records)
            val hrvRecords = healthManager.readHrv(startInstant, endInstant)
            for (hrv in hrvRecords) {
                records.add(JSONObject().apply {
                    put("type", "hrv")
                    put("ts", hrv.time.toEpochMilli() / 1000)
                    put("value", hrv.rmssd)
                    put("unit", "ms")
                })
            }

            // 4c. SpO2
            val spo2Records = healthManager.readSpo2(startInstant, endInstant)
            for (spo2 in spo2Records) {
                records.add(JSONObject().apply {
                    put("type", "spo2")
                    put("ts", spo2.time.toEpochMilli() / 1000)
                    put("value", spo2.percentage?.times(100) ?: 0)
                    put("unit", "%")
                })
            }

            // 4d. Weight
            val weightRecords = healthManager.readWeight(startInstant, endInstant)
            for (w in weightRecords) {
                records.add(JSONObject().apply {
                    put("type", "weight")
                    put("ts", w.time.toEpochMilli() / 1000)
                    put("value", w.weight?.inKilograms ?: 0.0)
                    put("unit", "kg")
                })
            }

            // 4e. Body fat
            val bfRecords = healthManager.readBodyFat(startInstant, endInstant)
            for (bf in bfRecords) {
                records.add(JSONObject().apply {
                    put("type", "body_fat")
                    put("ts", bf.time.toEpochMilli() / 1000)
                    put("value", bf.percentage ?: 0.0)
                    put("unit", "%")
                })
            }

            // 4f. Stress
            val stressRecords = healthManager.readStress(startInstant, endInstant)
            for (s in stressRecords) {
                records.add(JSONObject().apply {
                    put("type", "stress")
                    put("ts", s.time.toEpochMilli() / 1000)
                    put("value", s.stressLevel?.ordinal?.toDouble() ?: 0.0)
                    put("unit", "level")
                })
            }

            // 4g. Sleep
            val sleepRecords = healthManager.readSleep(dailyStart, dailyEnd)
            for (sl in sleepRecords) {
                val stages = JSONObject()
                for (stage in sl.stages) {
                    val stageName = stage.stage.name.lowercase()
                    stages.put(stageName, stage.duration.toMinutes())
                }
                records.add(JSONObject().apply {
                    put("type", "sleep")
                    put("date", today.toString())
                    put("bedtime_ts", sl.startTime.toEpochMilli() / 1000)
                    put("wake_ts", sl.endTime.toEpochMilli() / 1000)
                    put("duration_min", sl.duration.toMinutes())
                    put("stages", stages)
                })
            }

            // 4h. Exercise
            val exerciseRecords = healthManager.readExercise(startInstant, endInstant)
            var totalExerciseMin = 0
            for (ex in exerciseRecords) {
                val durMin = ex.duration.toMinutes().toInt()
                totalExerciseMin += durMin
            }

            // 5. POST to webhook
            if (records.isEmpty()) {
                logSync(db, "skipped", 0, "No new records to sync")
                settings.lastSyncTimestamp = now
                return@withContext Result.success()
            }

            val result = apiClient.postBatch(
                webhookUrl = settings.webhookUrl,
                secret = settings.secretToken,                deviceId = settings.deviceId,
                records = records,
            )

            if (result.success) {
                logSync(db, "success", records.size, "Synced ${records.size} records")
                settings.lastSyncTimestamp = now
                return@withContext Result.success()
            } else {
                logSync(db, "error", records.size, result.error)
                val retryCount = runAttemptCount
                return@withContext if (retryCount < MAX_RETRIES) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Sync worker failed", e)
            logSync(AppDatabase.getInstance(applicationContext), "error", 0, e.message ?: "Unknown error")
            return@withContext if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    private suspend fun logSync(db: AppDatabase, status: String, count: Int, message: String) {
        db.syncLogDao().insert(SyncLogEntity(
            status = status,
            recordsCount = count,
            message = message
        ))
    }
}
