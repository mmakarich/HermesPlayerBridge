package com.hermes.playerbridge.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.playerbridge.data.entities.SyncLogEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(
    isHealthConnectAvailable: Boolean,
    permissionsGranted: Boolean,
    lastSyncTime: String,
    syncLogs: List<SyncLogEntity>,
    isSyncing: Boolean,
    onGrantPermissions: () -> Unit,
    onManualSync: () -> Unit,
    onStartService: () -> Unit,
    onStopService: Unit,  // unused, for future
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hermes Player") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E),
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // ── Connection status card ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isHealthConnectAvailable) Icons.Default.CheckCircle
                                          else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isHealthConnectAvailable) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Health Connect",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Permission status
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (permissionsGranted) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = if (permissionsGranted) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (permissionsGranted) "All permissions granted"
                            else "Permissions needed",
                            fontSize = 13.sp,
                            color = Color(0xFFB0B0B0)
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    // Last sync
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color(0xFFB0B0B0),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Last sync: $lastSyncTime",
                            fontSize = 13.sp,
                            color = Color(0xFFB0B0B0)
                        )
                    }

                    if (isSyncing) {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Action buttons ──
            if (!permissionsGranted) {
                Button(
                    onClick = onGrantPermissions,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460))
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Grant Health Permissions")
                }
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = onManualSync,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSyncing && permissionsGranted,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460))
            ) {
                Icon(
                    if (isSyncing) Icons.Default.HourglassTop else Icons.Default.Sync,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isSyncing) "Syncing..." else "Manual Sync Now")
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onStartService,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF533483))
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start Background Sync")
            }

            Spacer(Modifier.height(16.dp))

            // ── Sync log ──
            Text(
                "Sync Log",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(4.dp))

            if (syncLogs.isEmpty()) {
                Text(
                    "No sync events yet",
                    fontSize = 13.sp,
                    color = Color(0xFF666666)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(syncLogs) { log ->
                        SyncLogItem(log)
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncLogItem(log: SyncLogEntity) {
    val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val time = dateFormat.format(Date(log.timestamp))

    val (color, icon) = when (log.status) {
        "success" -> Color(0xFF4CAF50) to Icons.Default.CheckCircle
        "error" -> Color(0xFFF44336) to Icons.Default.Error
        "skipped" -> Color(0xFFFF9800) to Icons.Default.Info
        else -> Color(0xFFB0B0B0) to Icons.Default.Circle
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2F))
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${log.status.uppercase()} — ${log.recordsCount} records",
                    fontSize = 13.sp,
                    color = Color.White
                )
                Text(
                    log.message,
                    fontSize = 11.sp,
                    color = Color(0xFF888888),
                    maxLines = 1
                )
            }
            Text(
                time,
                fontSize = 11.sp,
                color = Color(0xFF666666)
            )
        }
    }
}
