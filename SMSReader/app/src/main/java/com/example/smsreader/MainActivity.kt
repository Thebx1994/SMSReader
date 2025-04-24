package com.example.smsreader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.smsreader.service.SmsMonitorService
import com.example.smsreader.ui.theme.SMSReaderTheme
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.example.smsreader.data.LogEntry
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import android.os.Build

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            // Permissions granted, can proceed
        }
    }

    private val logEntries = mutableStateListOf<LogEntry>()
    
    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra(SmsMonitorService.EXTRA_LOG_MESSAGE)?.let { message ->
                logEntries.add(0, LogEntry(message))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()
        registerLogReceiver()
        
        setContent {
            SMSReaderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SmsMonitorScreen(
                        onStartMonitoring = { username, searchString ->
                            startSmsMonitoring(username, searchString)
                        },
                        onStopMonitoring = {
                            stopSmsMonitoring()
                        },
                        logEntries = logEntries
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(logReceiver)
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
        )
        
        // Add notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissions.any { permission ->
                ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            }) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun startSmsMonitoring(username: String, searchString: String) {
        Intent(this, SmsMonitorService::class.java).apply {
            action = SmsMonitorService.ACTION_START
            putExtra(SmsMonitorService.EXTRA_USERNAME, username)
            putExtra(SmsMonitorService.EXTRA_SEARCH_STRING, searchString)
            startService(this)
        }
    }

    private fun stopSmsMonitoring() {
        Intent(this, SmsMonitorService::class.java).apply {
            action = SmsMonitorService.ACTION_STOP
            startService(this)
        }
    }

    private fun registerLogReceiver() {
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(logReceiver, IntentFilter(SmsMonitorService.LOG_ACTION))
    }
}

@Composable
fun SmsMonitorScreen(
    onStartMonitoring: (String, String) -> Unit,
    onStopMonitoring: () -> Unit,
    logEntries: List<LogEntry> = emptyList()
) {
    var username by remember { mutableStateOf("") }
    var searchString by remember { mutableStateOf("") }
    var isMonitoring by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Sender Name") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = searchString,
            onValueChange = { searchString = it },
            label = { Text("Search String") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                if (isMonitoring) {
                    onStopMonitoring()
                } else {
                    onStartMonitoring(username, searchString)
                }
                isMonitoring = !isMonitoring
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isMonitoring) "Stop Monitoring" else "Start Monitoring")
        }

        Text(
            text = "Activity Log",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .padding(8.dp)
        ) {
            items(logEntries) { entry ->
                LogEntryItem(entry)
            }
        }
    }
}

@Composable
fun LogEntryItem(entry: LogEntry) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = dateFormat.format(entry.timestamp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}