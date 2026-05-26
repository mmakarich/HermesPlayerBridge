package com.hermes.playerbridge.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hermes.playerbridge.data.dao.*
import com.hermes.playerbridge.data.entities.*

@Database(
    entities = [
        VitalsEntity::class,
        SleepEntity::class,
        DailyStatsEntity::class,
        SyncLogEntity::class,
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vitalsDao(): VitalsDao
    abstract fun sleepDao(): SleepDao
    abstract fun dailyStatsDao(): DailyStatsDao
    abstract fun syncLogDao(): SyncLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hermes_player_cache.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
