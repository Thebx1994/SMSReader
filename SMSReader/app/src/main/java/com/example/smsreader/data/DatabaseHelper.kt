package com.example.smsreader.data

import android.util.Log
import kotlinx.coroutines.*
import java.sql.DriverManager
import java.sql.SQLException

class DatabaseHelper {
    companion object {
        private const val DB_URL = "jdbc:postgresql://191.243.196.93:5432/tutor"
        private const val DB_USER = "webadmin"
        private const val DB_PASSWORD = "SERczr74532"
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        init {
            try {
                Class.forName("org.postgresql.Driver")
            } catch (e: ClassNotFoundException) {
                Log.e("DatabaseHelper", "PostgreSQL JDBC Driver not found.", e)
            }
        }
        
        fun saveVoucher(voucher: VoucherData, onLog: (String) -> Unit) {
            // Validate data before attempting connection
            if (voucher.cardId.isBlank()) {
                onLog("Error: Invalid card ID - Cannot be empty")
                return
            }

            scope.launch(Dispatchers.IO) {
                var connection: java.sql.Connection? = null
                try {
                    connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)
                    
                    // First check if transaction already exists
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
                    
                    // If not exists, proceed with insert
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
                    // If it's a duplicate key error, treat it as a success
                    if (e.message?.contains("duplicate key value") == true) {
                        onLog("Transaction already exists in database - ID: ${voucher.transactionId}")
                    } else {
                        onLog("Database error: ${e.message}")
                        Log.e("DatabaseHelper", "Database error", e)
                    }
                } catch (e: Exception) {
                    onLog("Error: ${e.message}")
                    Log.e("DatabaseHelper", "Save error", e)
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
}

data class VoucherResponse(
    val success: Boolean,
    val message: String
) 