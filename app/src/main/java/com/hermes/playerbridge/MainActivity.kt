package com.hermes.playerbridge

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.hermes.playerbridge.data.AppDatabase
import com.hermes.playerbridge.data.entities.SyncLogEntity
import com.hermes.playerbridge.ui.StatusScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    // Permission launcher (no longer used directly — Health Connect uses intent)
    private var permissionLauncher: ActivityResultLauncher<Intent>? = null

    // State
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
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                Log.i(TAG, "Permissions granted from Health Connect UI")
                refreshState()
            } else {
                Log.w(TAG, "Permissions not granted by user")
            }
        }

        // Refresh permissions & start polling logs
        lifecycleScope.launch {
            refreshState()
            pollLogs()
        }

        // Auto-start foreground service if enabled
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
            delay(3000) // refresh every 3s
        }
    }

    private fun grantPermissions() {
        lifecycleScope.launch {
            try {
                val intent = healthManager.getPermissionIntent()
                if (intent != null) {
                    // Health Connect uses ActivityResultContracts.StartIntentSenderForResult
                    permissionLauncher?.launch(
                        Intent(Intent.ACTION_VIEW).apply {
                            // This is the correct way to launch Health Connect permission screen
                            setClassName(
                                "com.google.android.apps.healthdata",
                                "com.google.android.apps.healthdata.permission.OnboardingActivity"
                            )
                            putExtra("Permissions", healthManager.REQUIRED_PERMISSIONS.toTypedArray())
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch permission intent", e)
                // Fallback: open Health Connect app
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

        // Insert a pending log entry
        lifecycleScope.launch {
            db.syncLogDao().insert(
                SyncLogEntity(
                    status = "pending",
                    message = "Manual sync started"
                )
            )
        }

        // Enqueue one-shot sync worker
        SyncWorker.enqueueOneShot(this)

        // After a delay, refresh
        lifecycleScope.launch {
            delay(5000)
            _isSyncing.value = false
            refreshState()
        }
    }
}
