package com.hermes.playerbridge.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vitals_cache")
data class VitalsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val type: String,          // steps, heart_rate, hrv, spo2, calories, distance, weight, body_fat, stress
    val value: Double,
    val unit: String = "",
    val synced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sleep_cache")
data class SleepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val bedtimeTs: Long? = null,
    val wakeTs: Long? = null,
    val durationMin: Int = 0,
    val stagesJson: String = "{}",
    val synced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "daily_stats_cache")
data class DailyStatsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val steps: Int? = null,
    val heartRateAvg: Double? = null,
    val heartRateMin: Int? = null,
    val heartRateMax: Int? = null,
    val hrvAvg: Double? = null,
    val spo2Avg: Double? = null,
    val caloriesTotal: Int? = null,
    val distanceM: Int? = null,
    val weightKg: Double? = null,
    val bodyFatPct: Double? = null,
    val stressAvg: Double? = null,
    val exerciseMin: Int? = null,
    val synced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_log")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String,          // success, error, skipped
    val recordsCount: Int = 0,
    val message: String = ""
)
