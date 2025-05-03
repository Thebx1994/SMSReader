package com.example.smsreader.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_vouchers")
data class VoucherEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transactionId: String,
    val cardId: String,
    val amount: Float,
    val retryCount: Int = 0,
    val lastRetry: Long = System.currentTimeMillis()
)
