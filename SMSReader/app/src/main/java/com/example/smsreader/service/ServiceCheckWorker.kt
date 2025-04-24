package com.example.smsreader.service

import android.content.Context
import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters
import android.app.ActivityManager

class ServiceCheckWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        // Check if service is running
        val isServiceRunning = isServiceRunning(SmsMonitorService::class.java)
        
        if (!isServiceRunning) {
            // Restart service
            Intent(context, SmsMonitorService::class.java).apply {
                action = SmsMonitorService.ACTION_RESTART
                context.startService(this)
            }
        }
        
        return Result.success()
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == serviceClass.name }
    }
} 