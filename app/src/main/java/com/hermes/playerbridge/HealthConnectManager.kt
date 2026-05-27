package com.hermes.playerbridge

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.*
import java.time.temporal.ChronoUnit

class HealthConnectManager(private val context: Context) {

    companion object {
        private const val TAG = "HealthConnect"
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

    fun getClient() = healthClient

    suspend fun areAllPermissionsGranted(): Boolean {
        val client = healthClient ?: return false
        return try {
            val granted = client.permissionController.getGrantedPermissions()
            REQUIRED_PERMISSIONS.all { it in granted }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun readDailyAggregate(date: LocalDate): AggregationResult? {
        val client = healthClient ?: return null
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val end = start.plus(1, ChronoUnit.DAYS)
        val request = AggregateRequest(
            metrics = setOf(
                StepsRecord.COUNT_TOTAL,
                DistanceRecord.DISTANCE_TOTAL,
            ),
            timeRangeFilter = TimeRangeFilter.between(start, end)
        )
        return try {
            client.aggregate(request)
        } catch (e: Exception) {
            Log.e(TAG, "Aggregate failed", e)
            null
        }
    }

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
            emptyList()
        }
    }

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
            emptyList()
        }
    }

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
            emptyList()
        }
    }

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
            emptyList()
        }
    }

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
            emptyList()
        }
    }

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
            emptyList()
        }
    }
}
