package com.example.smsreader.data

data class LogEntry(
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
) 