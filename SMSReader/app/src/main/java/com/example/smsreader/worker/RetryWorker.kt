package com.example.smsreader.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.smsreader.data.DatabaseHelper
import com.example.smsreader.data.LocalDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

class RetryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val localDb = LocalDatabaseHelper(context)
    private val remoteDb = DatabaseHelper(context)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val pendingVouchers = localDb.getPendingVouchers()

            pendingVouchers.forEach { pendingVoucher ->
                try {
                    remoteDb.saveVoucher(
                        voucher = com.example.smsreader.data.VoucherData(
                            transactionId = pendingVoucher.transactionId,
                            cardId = pendingVoucher.cardId,
                            amount = pendingVoucher.amount
                        )
                    ) { message ->
                        if (message.contains("saved successfully") ||
                            message.contains("already exists")) {
                            localDb.deletePendingVoucher(pendingVoucher.id)
                        } else {
                            localDb.updateRetryCount(pendingVoucher.id)
                        }
                        Log.d("RetryWorker", "Retry result: $message")
                    }
                } catch (e: Exception) {
                    Log.e("RetryWorker", "Error retrying voucher", e)
                    localDb.updateRetryCount(pendingVoucher.id)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("RetryWorker", "Worker error", e)
            Result.retry()
        }
    }
}