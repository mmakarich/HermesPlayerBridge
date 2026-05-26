package com.hermes.playerbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts the foreground sync service after device reboot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val settings = SettingsManager(context)
            if (settings.autoSyncEnabled) {
                SyncService.start(context)
            }
        }
    }
}
