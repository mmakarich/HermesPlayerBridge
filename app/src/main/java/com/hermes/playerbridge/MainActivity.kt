package com.hermes.playerbridge

import android.os.Bundle
import android.util.Log
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.health.connect.client.permission.HealthPermission
import androidx.lifecycle.lifecycleScope
import com.hermes.playerbridge.data.AppDatabase
import com.hermes.playerbridge.data.entities.SyncLogEntity
import com.hermes.playerbridge.ui.StatusScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var healthManager: HealthConnectManager
    private lateinit var settings: SettingsManager
    private lateinit var db: AppDatabase

    private var permissionLauncher: androidx.activity.result.ActivityResultLauncher<Set<HealthPermission>>? = null

    private val _isSyncing = MutableStateFlow(false)
    private val _logEntries = MutableStateFlow<List<SyncLogEntity>>(emptyList())
    private val _permissionsGranted = MutableStateFlow(false)
    private val _hcAvailable = MutableStateFlow(false)
    private val _lastSync = MutableStateFlow("Never")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        healthManager = HealthConnectManager(this)
        settings = SettingsManager(this)
        db = AppDatabase.getInstance(this)

        // Register Health Connect permission launcher
        try {
            permissionLauncher = registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { granted ->
                _permissionsGranted.value = granted.all { it.value }
                Log.i(TAG, "Permissions: $granted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register permission launcher", e)
        }

        lifecycleScope.launch {
            refreshState()
            pollLogs()
        }

        if (settings.autoSyncEnabled) {
            SyncService.start(this)
        }

        setContent {
            val isSyncing by _isSyncing.collectAsState()
            val logs by _logEntries.collectAsState()
            val permGranted by _permissionsGranted.collectAsState()
            val hcAvail by _hcAvailable.collectAsState()
            val lastSync by _lastSync.collectAsState()

            StatusScreen(
                isHealthConnectAvailable = hcAvail,
                permissionsGranted = permGranted,
                lastSyncTime = lastSync,
                syncLogs = logs,
                isSyncing = isSyncing,
                onGrantPermissions = { grantPermissions() },
                onManualSync = { manualSync() },
                onStartService = { SyncService.start(this@MainActivity) },
                onStopService = Unit
            )
        }
    }

    private suspend fun refreshState() {
        _hcAvailable.value = healthManager.isAvailable()
        if (healthManager.isAvailable()) {
            _permissionsGranted.value = healthManager.areAllPermissionsGranted()
        }
        val lastSyncMs = settings.lastSyncTimestamp
        _lastSync.value = if (lastSyncMs > 0) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            sdf.format(Date(lastSyncMs))
        } else {
            "Never"
        }
    }

    private suspend fun pollLogs() {
        while (true) {
            _logEntries.value = db.syncLogDao().getRecent()
            delay(3000)
        }
    }

    private fun grantPermissions() {
        lifecycleScope.launch {
            try {
                val client = healthManager.getClient()
                if (client != null) {
                    // Open Health Connect permissions screen
                    val intent = Intent("androidx.health.connect.action.REQUEST_PERMISSIONS")
                        .setPackage("com.google.android.apps.healthdata")
                        .putExtra("PERMISSIONS", HealthConnectManager.REQUIRED_PERMISSIONS.toTypedArray())
                    startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch permission intent", e)
                try {
                    val intent = packageManager.getLaunchIntentForPackage("com.google.android.apps.healthdata")
                    if (intent != null) startActivity(intent)
                } catch (e2: Exception) {
                    Log.e(TAG, "Health Connect not installed", e2)
                }
            }
        }
    }

    private fun manualSync() {
        if (_isSyncing.value) return
        _isSyncing.value = true

        lifecycleScope.launch {
            db.syncLogDao().insert(
                SyncLogEntity(
                    status = "pending",
                    message = "Manual sync started"
                )
            )
        }

        SyncWorker.enqueueOneShot(this)

        lifecycleScope.launch {
            delay(5000)
            _isSyncing.value = false
            refreshState()
        }
    }
}
