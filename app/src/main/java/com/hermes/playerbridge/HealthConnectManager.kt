package com.hermes.playerbridge

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateGroupRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.*

class HealthConnectManager(private val context: Context) {

    companion object {
        private const val TAG = "HealthConnect"
        const val AGGREGATION_PERIOD_DAYS = 1  // daily summaries

        // All permissions we request
        val REQUIRED_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(BodyFatRecord::class),
            HealthPermission.getReadPermission(StressRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        )
    }

    private val healthClient: HealthConnectClient? by lazy {
        try {
            HealthConnectClient.getOrCreate(context)
        } catch (e: Exception) {
            Log.e(TAG, "Health Connect not available: ${e.message}")
            null
        }
    }

    fun isAvailable(): Boolean = healthClient != null

    suspend fun getGrantedPermissions(): Set<String> {
        return try {
            healthClient?.permissionController?.getGrantedPermissions() ?: emptySet()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get permissions", e)
            emptySet()
        }
    }

    suspend fun areAllPermissionsGranted(): Boolean {
        val granted = getGrantedPermissions()
        return REQUIRED_PERMISSIONS.all { it in granted }
    }

    /** Returns the intent to launch the Health Connect permissions screen. */
    fun getPermissionIntent() = healthClient?.permissionController?.getPermissionIntent(REQUIRED_PERMISSIONS)

    // ── Data queries ─────────────────────────────────────────

    /** Aggregate daily stats: steps, calories, distance, heart rate. */
    suspend fun readDailyAggregate(date: LocalDate): AggregationResult? {
        val client = healthClient ?: return null
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val end = start.plus(1, ChronoUnit.DAYS)

        val request = AggregateGroupRequest(
            metrics = setOf(
                StepsRecord.COUNT_TOTAL,
                TotalCaloriesBurnedRecord.CALORIES_TOTAL,
                DistanceRecord.DISTANCE_TOTAL,
                HeartRateRecord.HEART_RATE_AVG,
                HeartRateRecord.HEART_RATE_MIN,
                HeartRateRecord.HEART_RATE_MAX,
            ),
            timeRangeFilter = TimeRangeFilter.between(start, end)
        )
        return try {
            client.aggregate(request)
        } catch (e: Exception) {
            Log.e(TAG, "Aggregate failed for $date", e)
            null
        }
    }

    /** Read HRV records for a time range. */
    suspend fun readHrv(start: Instant, end: Instant): List<HeartRateVariabilityRmssdRecord> {
        val client = healthClient ?: return emptyList()
        return try {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateVariabilityRmssdRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            ).records
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read HRV", e)
            emptyList()
        }
    }

    /** Read SpO2 records for a time range. */
    suspend fun readSpo2(start: Instant, end: Instant): List<OxygenSaturationRecord> {
        val client = healthClient ?: return emptyList()
        return try {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = OxygenSaturationRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            ).records
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read SpO2", e)
            emptyList()
        }
    }

    /** Read weight records for a time range. */
    suspend fun readWeight(start: Instant, end: Instant): List<WeightRecord> {
        val client = healthClient ?: return emptyList()
        return try {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            ).records
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read weight", e)
            emptyList()
        }
    }

    /** Read body fat records. */
    suspend fun readBodyFat(start: Instant, end: Instant): List<BodyFatRecord> {
        val client = healthClient ?: return emptyList()
        return try {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = BodyFatRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            ).records
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read body fat", e)
            emptyList()
        }
    }

    /** Read stress records. */
    suspend fun readStress(start: Instant, end: Instant): List<StressRecord> {
        val client = healthClient ?: return emptyList()
        return try {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = StressRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            ).records
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read stress", e)
            emptyList()
        }
    }

    /** Read sleep sessions for a date range. */
    suspend fun readSleep(start: Instant, end: Instant): List<SleepSessionRecord> {
        val client = healthClient ?: return emptyList()
        return try {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            ).records
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read sleep", e)
            emptyList()
        }
    }

    /** Read exercise sessions. */
    suspend fun readExercise(start: Instant, end: Instant): List<ExerciseSessionRecord> {
        val client = healthClient ?: return emptyList()
        return try {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            ).records
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read exercise", e)
            emptyList()
        }
    }
}
