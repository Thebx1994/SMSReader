package com.example.smsreader.service

import android.app.*
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.provider.Telephony
import android.util.Log
import com.example.smsreader.data.DatabaseHelper
import com.example.smsreader.data.VoucherData
import java.util.regex.Pattern
import android.content.Context
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.example.smsreader.util.NotificationHelper

class SmsMonitorService : Service() {
    private var smsObserver: ContentObserver? = null
    private var username: String = ""
    private var searchString: String = ""
    private var isRunning = false
    private lateinit var workManager: WorkManager
    private lateinit var notificationHelper: NotificationHelper
    private var lastProcessedSmsId: String? = null
    private var lastProcessedTimestamp: Long = 0
    private var isProcessingSms = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        workManager = WorkManager.getInstance(applicationContext)
        startForeground()
        scheduleServiceCheck()
    }

    private fun scheduleServiceCheck() {
        val serviceCheckRequest = PeriodicWorkRequestBuilder<ServiceCheckWorker>(
            15, TimeUnit.MINUTES,  // Minimum interval allowed by Android
            5, TimeUnit.MINUTES    // Flex interval
        ).build()

        workManager.enqueueUniquePeriodicWork(
            "service_check",
            ExistingPeriodicWorkPolicy.REPLACE,
            serviceCheckRequest
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                username = intent.getStringExtra(EXTRA_USERNAME) ?: ""
                searchString = intent.getStringExtra(EXTRA_SEARCH_STRING) ?: ""
                startMonitoring()
            }
            ACTION_STOP -> stopMonitoring()
            ACTION_RESTART -> if (isRunning) {
                // Restart monitoring without changing parameters
                stopMonitoring()
                startMonitoring()
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun startMonitoring() {
        if (!isRunning) {
            isRunning = true
            registerSmsObserver()
            sendLog("Started monitoring SMS messages")
        }
    }

    private fun stopMonitoring() {
        isRunning = false
        lastProcessedSmsId = null
        lastProcessedTimestamp = 0
        unregisterSmsObserver()
        sendLog("Stopped monitoring SMS messages")
        stopSelf()
    }

    private fun registerSmsObserver() {
        smsObserver = object : ContentObserver(Handler()) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                if (isRunning) {
                    checkNewSms()
                }
            }
        }

        contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI,
            true,
            smsObserver!!
        )
    }

    private fun checkNewSms() {
        if (isProcessingSms) return  // Skip if already processing an SMS
        
        try {
            val cursor = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE
                ),
                null,
                null,
                "date DESC LIMIT 1"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val smsId = it.getString(it.getColumnIndexOrThrow(Telephony.Sms._ID))
                    val smsBody = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY))
                    val timestamp = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE))

                    // Check if we've already processed this SMS
                    if (smsId == lastProcessedSmsId || timestamp <= lastProcessedTimestamp) {
                        return
                    }

                    // Set processing flag
                    isProcessingSms = true
                    
                    try {
                        // Update tracking variables
                        lastProcessedSmsId = smsId
                        lastProcessedTimestamp = timestamp

                        // Process the SMS
                        processTransactionSms(smsBody)
                    } finally {
                        // Clear processing flag when done
                        isProcessingSms = false
                    }
                }
            }
        } catch (e: Exception) {
            isProcessingSms = false  // Make sure to clear the flag on error
            sendLog("Error checking new SMS: ${e.message}")
            Log.e("SmsMonitorService", "Error checking SMS", e)
        }
    }

    private fun processTransactionSms(smsBody: String) {
        try {
            val transactionPattern = Pattern.compile("Nro\\. Transaccion ([A-Z0-9]+)")
            val amountPattern = Pattern.compile("(\\d+\\.\\d{2}) CUP")
            val cardPattern = Pattern.compile("cuenta (\\d+)")

            val transactionMatcher = transactionPattern.matcher(smsBody)
            val amountMatcher = amountPattern.matcher(smsBody)
            val cardMatcher = cardPattern.matcher(smsBody)

            if (transactionMatcher.find() && amountMatcher.find() && cardMatcher.find()) {
                val transactionId = transactionMatcher.group(1)
                val amount = amountMatcher.group(1).toFloat()
                val cardId = cardMatcher.group(1)

                // Format the log message with line breaks
                sendLog("""
                    New transaction detected:
                    ID: $transactionId
                    Amount: $amount CUP
                    Card: $cardId
                """.trimIndent())

                // Show notification
                notificationHelper.showTransactionNotification(transactionId, amount, cardId)

                val voucher = VoucherData(transactionId, cardId, amount)
                
                try {
                    DatabaseHelper.saveVoucher(voucher) { dbLog ->
                        if (isRunning) {
                            sendLog(dbLog)
                        }
                    }
                } catch (e: Exception) {
                    sendLog("Error processing transaction: ${e.message}")
                    Log.e("SmsMonitorService", "Database error", e)
                }
            } else {
                sendLog("SMS received but no valid transaction details found")
            }
        } catch (e: Exception) {
            sendLog("Error processing SMS: ${e.message}")
            Log.e("SmsMonitorService", "Error processing SMS", e)
        }
    }

    private fun startForeground() {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotification(): Notification {
        val channelId = "sms_monitor_channel"
        val channelName = "SMS Monitor Service"
        
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        )
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        return Notification.Builder(this, channelId)
            .setContentTitle("SMS Monitor Active")
            .setContentText("Monitoring SMS messages")
            .build()
    }

    private fun unregisterSmsObserver() {
        smsObserver?.let {
            contentResolver.unregisterContentObserver(it)
            smsObserver = null
        }
    }

    private fun sendLog(message: String) {
        Intent(LOG_ACTION).also { intent ->
            intent.putExtra(EXTRA_LOG_MESSAGE, message)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            stopMonitoring()
        } catch (e: Exception) {
            Log.e("SmsMonitorService", "Error during service destruction", e)
        }
    }

    // Add this method to restart the service if it gets killed
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (isRunning) {
            // Restart the service
            val restartServiceIntent = Intent(applicationContext, SmsMonitorService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_USERNAME, username)
                putExtra(EXTRA_SEARCH_STRING, searchString)
            }
            startService(restartServiceIntent)
        }
    }

    companion object {
        const val ACTION_START = "com.example.smsreader.START_MONITORING"
        const val ACTION_STOP = "com.example.smsreader.STOP_MONITORING"
        const val ACTION_RESTART = "com.example.smsreader.RESTART_MONITORING"
        const val EXTRA_USERNAME = "username"
        const val EXTRA_SEARCH_STRING = "search_string"
        private const val NOTIFICATION_ID = 1
        const val LOG_ACTION = "com.example.smsreader.LOG"
        const val EXTRA_LOG_MESSAGE = "log_message"
    }
} 