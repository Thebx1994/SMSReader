package com.example.server.controller

import org.springframework.web.bind.annotation.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.http.ResponseEntity
import org.springframework.beans.factory.annotation.Autowired

@RestController
@RequestMapping("/vouchers")
class VoucherController(@Autowired private val jdbcTemplate: JdbcTemplate) {

    @PostMapping
    fun saveVoucher(@RequestBody voucher: VoucherData): ResponseEntity<VoucherResponse> {
        return try {
            val sql = """
                INSERT INTO Vouchers (transaction_id, card_id, amount, used)
                VALUES (?, ?, ?, false)
            """.trimIndent()

            jdbcTemplate.update(sql,
                voucher.transactionId,
                voucher.cardId,
                voucher.amount
            )

            ResponseEntity.ok(VoucherResponse(true, "Voucher saved successfully"))
        } catch (e: Exception) {
            ResponseEntity.ok(VoucherResponse(false, "Error: ${e.message}"))
        }
    }
}

data class VoucherData(
    val transactionId: String,
    val cardId: String,
    val amount: Float
)

data class VoucherResponse(
    val success: Boolean,
    val message: String
) 