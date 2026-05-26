package com.hermes.playerbridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground service that keeps the sync process alive.
 * Android 14+ requires foreground services for periodic data sync.
 * Notification is persistent (cannot be swiped away).
 */
class SyncService : Service() {

    companion object {
        private const val CHANNEL_ID = "hermes_player_sync"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_START = "com.hermes.playerbridge.START_SYNC"
        private const val ACTION_STOP = "com.hermes.playerbridge.STOP_SYNC"

        fun start(context: Context) {
            val intent = Intent(context, SyncService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SyncService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val notification = buildNotification()
                startForeground(NOTIFICATION_ID, notification)

                // Enqueue the periodic WorkManager job
                val settings = SettingsManager(this)
                SyncWorker.enqueue(this, settings.syncIntervalMin)

                // Also run one-shot sync immediately
                SyncWorker.enqueueOneShot(this)
            }
            ACTION_STOP -> {
                SyncWorker.cancel(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_sync_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_sync_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.sync_notification_title))
            .setContentText(getString(R.string.sync_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingOpen)
            .setOngoing(true)                     // cannot be swiped away
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
