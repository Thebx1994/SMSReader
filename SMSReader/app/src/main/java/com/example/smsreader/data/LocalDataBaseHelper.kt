package com.example.smsreader.data

import android.content.Context
import android.util.Log
import com.example.smsreader.data.room.AppDatabase
import com.example.smsreader.data.room.VoucherEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LocalDatabaseHelper(context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val voucherDao = database.voucherDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val MAX_RETRIES = 10
        const val RETRY_DELAY_MS = 30 * 1000L // 30 seconds
    }

    fun savePendingVoucher(voucher: VoucherData) {
        scope.launch {
            try {
                val voucherEntity = VoucherEntity(
                    transactionId = voucher.transactionId,
                    cardId = voucher.cardId,
                    amount = voucher.amount
                )
                voucherDao.insertVoucher(voucherEntity)
                Log.d("LocalDatabaseHelper", "Saved pending voucher: ${voucher.transactionId}")
            } catch (e: Exception) {
                Log.e("LocalDatabaseHelper", "Error saving pending voucher", e)
            }
        }
    }

    suspend fun getPendingVouchers(): List<PendingVoucher> {
        val currentTime = System.currentTimeMillis()
        val retryTime = currentTime - RETRY_DELAY_MS

        return try {
            voucherDao.getPendingVouchers(MAX_RETRIES, retryTime).map { entity ->
                PendingVoucher(
                    id = entity.id,
                    transactionId = entity.transactionId,
                    cardId = entity.cardId,
                    amount = entity.amount,
                    retryCount = entity.retryCount
                )
            }
        } catch (e: Exception) {
            Log.e("LocalDatabaseHelper", "Error getting pending vouchers", e)
            emptyList()
        }
    }

    fun updateRetryCount(id: Long) {
        scope.launch {
            try {
                voucherDao.updateRetryCount(id, System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e("LocalDatabaseHelper", "Error updating retry count", e)
            }
        }
    }

    fun deletePendingVoucher(id: Long) {
        scope.launch {
            try {
                voucherDao.deleteVoucher(id)
            } catch (e: Exception) {
                Log.e("LocalDatabaseHelper", "Error deleting pending voucher", e)
            }
        }
    }
}

data class PendingVoucher(
    val id: Long,
    val transactionId: String,
    val cardId: String,
    val amount: Float,
    val retryCount: Int
)