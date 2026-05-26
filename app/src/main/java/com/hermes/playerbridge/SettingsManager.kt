package com.hermes.playerbridge

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Stores app configuration (webhook URL, secret, interval) in
 * EncryptedSharedPreferences.
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        prefs = EncryptedSharedPreferences.create(
            "hermes_player_prefs",
            masterKey,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var webhookUrl: String
        get() = prefs.getString(KEY_WEBHOOK_URL, DEFAULT_WEBHOOK_URL) ?: DEFAULT_WEBHOOK_URL
        set(v) = prefs.edit().putString(KEY_WEBHOOK_URL, v).apply()

    var secretToken: String
        get() = prefs.getString(KEY_SECRET, "hermes-player-bridge") ?: "hermes-player-bridge"
        set(v) = prefs.edit().putString(KEY_SECRET, v).apply()

    var syncIntervalMin: Int
        get() = prefs.getInt(KEY_INTERVAL, 15)
        set(v) = prefs.edit().putInt(KEY_INTERVAL, v.coerceIn(5, 60)).apply()

    var deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, android.os.Build.MODEL) ?: android.os.Build.MODEL
        set(v) = prefs.edit().putString(KEY_DEVICE_ID, v).apply()

    var lastSyncTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(v) = prefs.edit().putLong(KEY_LAST_SYNC, v).apply()

    var autoSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SYNC, true)
        set(v) = prefs.edit().putBoolean(KEY_AUTO_SYNC, v).apply()

    companion object {
        private const val KEY_WEBHOOK_URL = "webhook_url"
        private const val KEY_SECRET = "secret_token"
        private const val KEY_INTERVAL = "sync_interval"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_LAST_SYNC = "last_sync_ts"
        private const val KEY_AUTO_SYNC = "auto_sync"

        // Default: Tailscale IP + webhook path
        private const val DEFAULT_WEBHOOK_URL = "https://100.118.219.23:8645/webhooks/health_connect"
    }
}
