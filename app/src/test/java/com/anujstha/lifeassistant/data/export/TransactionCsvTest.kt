package com.anujstha.lifeassistant.data.export

import com.anujstha.lifeassistant.data.entity.ExpenseCategoryEntity
import com.anujstha.lifeassistant.data.entity.TransactionEntity
import com.anujstha.lifeassistant.domain.model.PaymentMethod
import com.anujstha.lifeassistant.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringWriter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class TransactionCsvTest {

    private val food = ExpenseCategoryEntity("c1", "Food, drinks", TransactionType.EXPENSE, true, null, Instant.EPOCH, Instant.EPOCH)

    private fun txn(note: String?, amount: Long = 123_450, time: LocalTime? = LocalTime.of(9, 30)) = TransactionEntity(
        id = "t1", amountMinor = amount, currencyCode = "NPR", type = TransactionType.EXPENSE, categoryId = "c1",
        date = LocalDate.of(2026, 10, 14), time = time, paymentMethod = PaymentMethod.MOBILE_WALLET, note = note,
        createdAt = Instant.parse("2026-10-14T03:00:00Z"), updatedAt = Instant.parse("2026-10-14T04:00:00Z"),
    )

    private fun csv(vararg t: TransactionEntity) = StringWriter().also { TransactionCsv.write(it, t.toList(), listOf(food)) }.toString()

    @Test
    fun headerAndRow_withCrlfAndPlainAmount() {
        val lines = csv(txn("Lunch")).split("\r\n")
        assertEquals(
            "date,time,type,amount,currency,category,payment_method,description,id,created_at,updated_at",
            lines[0],
        )
        assertEquals(
            "2026-10-14,09:30,EXPENSE,1234.50,NPR,\"Food, drinks\",MOBILE_WALLET,Lunch,t1,2026-10-14T03:00:00Z,2026-10-14T04:00:00Z",
            lines[1],
        )
        assertEquals("", lines[2]) // trailing CRLF
    }

    @Test
    fun quotesAndNewlines_areEscaped() {
        val row = csv(txn("He said \"hi\"\nthen left")).split("\r\n")[1]
        assertEquals(true, row.contains("\"He said \"\"hi\"\"\nthen left\""))
    }

    @Test
    fun formulaLikeText_isNeutralized() {
        assertEquals("'=SUM(A1:A9)", TransactionCsv.text("=SUM(A1:A9)"))
        assertEquals("'+1", TransactionCsv.text("+1"))
        assertEquals("'-2", TransactionCsv.text("-2"))
        assertEquals("'@cmd", TransactionCsv.text("@cmd"))
        assertEquals("Groceries", TransactionCsv.text("Groceries"))
    }

    @Test
    fun emptyOptionalFields() {
        val row = csv(txn(null, time = null)).split("\r\n")[1]
        assertEquals("2026-10-14,,EXPENSE,1234.50,NPR,\"Food, drinks\",MOBILE_WALLET,,t1,2026-10-14T03:00:00Z,2026-10-14T04:00:00Z", row)
    }
}
