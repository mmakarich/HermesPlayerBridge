package com.hermes.playerbridge.data.dao

import androidx.room.*
import com.hermes.playerbridge.data.entities.*

@Dao
interface VitalsDao {
    @Query("SELECT * FROM vitals_cache WHERE synced = 0 ORDER BY ts ASC")
    suspend fun getUnsynced(): List<VitalsEntity>

    @Query("SELECT * FROM vitals_cache ORDER BY ts DESC LIMIT 500")
    suspend fun getRecent(): List<VitalsEntity>

    @Insert
    suspend fun insertAll(records: List<VitalsEntity>)

    @Query("UPDATE vitals_cache SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("DELETE FROM vitals_cache WHERE synced = 1")
    suspend fun purgeSynced()

    @Query("SELECT COUNT(*) FROM vitals_cache WHERE synced = 0")
    suspend fun countUnsynced(): Int
}

@Dao
interface SleepDao {
    @Query("SELECT * FROM sleep_cache WHERE synced = 0 ORDER BY date ASC")
    suspend fun getUnsynced(): List<SleepEntity>

    @Insert
    suspend fun insertAll(records: List<SleepEntity>)

    @Query("UPDATE sleep_cache SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("DELETE FROM sleep_cache WHERE synced = 1")
    suspend fun purgeSynced()
}

@Dao
interface DailyStatsDao {
    @Query("SELECT * FROM daily_stats_cache WHERE synced = 0")
    suspend fun getUnsynced(): List<DailyStatsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: DailyStatsEntity)

    @Query("UPDATE daily_stats_cache SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Query("DELETE FROM daily_stats_cache WHERE synced = 1")
    suspend fun purgeSynced()
}

@Dao
interface SyncLogDao {
    @Query("SELECT * FROM sync_log ORDER BY id DESC LIMIT 50")
    suspend fun getRecent(): List<SyncLogEntity>

    @Insert
    suspend fun insert(log: SyncLogEntity)

    @Query("DELETE FROM sync_log")
    suspend fun clear()
}
