package com.example.smsreader.data.room

import androidx.room.*

@Dao
interface VoucherDao {
    @Query("SELECT * FROM pending_vouchers WHERE retryCount < :maxRetries AND lastRetry < :retryTime")
    suspend fun getPendingVouchers(maxRetries: Int, retryTime: Long): List<VoucherEntity>

    @Insert
    suspend fun insertVoucher(voucher: VoucherEntity)

    @Query("UPDATE pending_vouchers SET retryCount = retryCount + 1, lastRetry = :currentTime WHERE id = :id")
    suspend fun updateRetryCount(id: Long, currentTime: Long)

    @Query("DELETE FROM pending_vouchers WHERE id = :id")
    suspend fun deleteVoucher(id: Long)
}
