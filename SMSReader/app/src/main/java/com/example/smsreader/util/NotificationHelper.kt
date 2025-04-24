package com.example.smsreader.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.smsreader.MainActivity
import com.example.smsreader.R

class NotificationHelper(private val context: Context) {
    companion object {
        private const val CHANNEL_ID = "sms_notifications"
        private const val NOTIFICATION_ID = 2 // Different from service notification
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "SMS Notifications"
            val descriptionText = "Notifications for matched SMS messages"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showTransactionNotification(transactionId: String, amount: Float, cardId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // You'll need to add this icon
            .setContentTitle("New Transaction Detected")
            .setContentText("Transaction: $transactionId - Amount: $amount CUP")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Transaction ID: $transactionId\nAmount: $amount CUP\nCard: $cardId"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_ID, notification)
        }
    }
} 