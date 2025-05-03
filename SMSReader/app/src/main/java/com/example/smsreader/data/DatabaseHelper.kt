package com.example.smsreader.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.*
import java.sql.DriverManager
import java.sql.SQLException
import androidx.work.*
import com.example.smsreader.worker.RetryWorker
import java.util.concurrent.TimeUnit

class DatabaseHelper(private val context: Context) {
    companion object {
        private const val PREFS_NAME = "DatabasePrefs"
        private const val KEY_DB_URL = "db_url"
        private const val KEY_DB_USER = "db_user"
        private const val KEY_DB_PASSWORD = "db_password"
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        init {
            try {
                Class.forName("org.postgresql.Driver")
            } catch (e: ClassNotFoundException) {
                Log.e("DatabaseHelper", "PostgreSQL JDBC Driver not found.", e)
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val localDb = LocalDatabaseHelper(context)

    private fun getDbUrl(): String = prefs.getString(KEY_DB_URL, "") ?: ""
    private fun getDbUser(): String = prefs.getString(KEY_DB_USER, "") ?: ""
    private fun getDbPassword(): String = prefs.getString(KEY_DB_PASSWORD, "") ?: ""

    fun saveCredentials(url: String, user: String, password: String) {
        prefs.edit().apply {
            putString(KEY_DB_URL, url)
            putString(KEY_DB_USER, user)
            putString(KEY_DB_PASSWORD, password)
            apply()
        }
        scheduleRetryWorker()
    }

    private fun scheduleRetryWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Schedule immediate retry worker
        val immediateRetry = OneTimeWorkRequestBuilder<RetryWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, // Start with 30 seconds
                TimeUnit.SECONDS
            )
            .build()

        // Schedule periodic retry worker for remaining items
        val periodicRetry = PeriodicWorkRequestBuilder<RetryWorker>(
            3, TimeUnit.MINUTES, // Run every 3 minutes
            1, TimeUnit.MINUTES  // Flex period
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).apply {
            enqueueUniqueWork(
                "immediate_retry",
                ExistingWorkPolicy.REPLACE,
                immediateRetry
            )
            enqueueUniquePeriodicWork(
                "periodic_retry",
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRetry
            )
        }
    }

    fun saveVoucher(voucher: VoucherData, onLog: (String) -> Unit) {
        if (voucher.cardId.isBlank()) {
            onLog("Error: Invalid card ID - Cannot be empty")
            return
        }

        val dbUrl = getDbUrl()
        val dbUser = getDbUser()
        val dbPassword = getDbPassword()

        if (dbUrl.isBlank() || dbUser.isBlank() || dbPassword.isBlank()) {
            onLog("Error: Database credentials not set")
            return
        }

        scope.launch(Dispatchers.IO) {
            var connection: java.sql.Connection? = null
            try {
                connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword)

                val checkSql = """
                    SELECT COUNT(*) FROM Vouchers 
                    WHERE transaction_id = ?
                """.trimIndent()

                var exists = false
                connection.prepareStatement(checkSql).use { statement ->
                    statement.setString(1, voucher.transactionId)
                    val resultSet = statement.executeQuery()
                    if (resultSet.next() && resultSet.getInt(1) > 0) {
                        exists = true
                    }
                }

                if (exists) {
                    onLog("Transaction already exists in database - ID: ${voucher.transactionId}")
                    return@launch
                }

                val insertSql = """
                    INSERT INTO Vouchers (transaction_id, card_id, amount, used, created_at)
                    VALUES (?, ?, ?, false, CURRENT_TIMESTAMP)
                """.trimIndent()

                connection.prepareStatement(insertSql).use { statement ->
                    statement.setString(1, voucher.transactionId)
                    statement.setString(2, voucher.cardId)
                    statement.setFloat(3, voucher.amount)

                    statement.executeUpdate()
                    onLog("Transaction saved successfully - ID: ${voucher.transactionId}")
                }
            } catch (e: SQLException) {
                if (e.message?.contains("duplicate key value") == true) {
                    onLog("Transaction already exists in database - ID: ${voucher.transactionId}")
                } else {
                    onLog("Database error: ${e.message}")
                    Log.e("DatabaseHelper", "Database error", e)
                    localDb.savePendingVoucher(voucher)
                    onLog("Transaction saved to local backup - Will retry shortly")
                    scheduleRetryWorker() // Schedule immediate retry
                }
            } catch (e: Exception) {
                onLog("Error: ${e.message}")
                Log.e("DatabaseHelper", "Save error", e)
                localDb.savePendingVoucher(voucher)
                onLog("Transaction saved to local backup - Will retry shortly")
                scheduleRetryWorker() // Schedule immediate retry
            } finally {
                try {
                    connection?.close()
                } catch (e: SQLException) {
                    Log.e("DatabaseHelper", "Error closing connection", e)
                }
            }
        }
    }
}

data class VoucherResponse(
    val success: Boolean,
    val message: String
)