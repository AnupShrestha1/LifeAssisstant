package com.anujstha.lifeassistant.data.export

import com.anujstha.lifeassistant.data.entity.ExpenseCategoryEntity
import com.anujstha.lifeassistant.data.entity.TransactionEntity
import com.anujstha.lifeassistant.util.Money
import java.io.Writer

/**
 * CSV export of transactions (RFC 4180: comma-separated, CRLF line ends, fields quoted when
 * needed). Contains only transaction data — no journal, tasks or settings.
 *
 * Columns: date, time, type, amount, currency, category, payment_method, description, id,
 * created_at, updated_at. Amounts are plain decimals ("1234.50") so spreadsheets read them as
 * numbers; timestamps are ISO-8601 UTC.
 */
object TransactionCsv {

    val HEADER = listOf(
        "date", "time", "type", "amount", "currency", "category", "payment_method", "description",
        "id", "created_at", "updated_at",
    )

    fun write(out: Writer, transactions: List<TransactionEntity>, categories: List<ExpenseCategoryEntity>) {
        val names = categories.associate { it.id to it.name }
        out.write(row(HEADER))
        transactions.forEach { t ->
            out.write(
                row(
                    listOf(
                        t.date.toString(),
                        t.time?.toString().orEmpty(),
                        t.type.name,
                        Money.plain(t.amountMinor, t.currencyCode),
                        t.currencyCode,
                        text(names[t.categoryId].orEmpty()),
                        t.paymentMethod.name,
                        text(t.note.orEmpty()),
                        t.id,
                        t.createdAt.toString(),
                        t.updatedAt.toString(),
                    ),
                ),
            )
        }
        out.flush()
    }

    private fun row(fields: List<String>): String = fields.joinToString(",") { escape(it) } + "\r\n"

    /** Quote fields containing a comma, quote, or line break; double embedded quotes. */
    fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }

    /**
     * Free-text fields that start with =, +, -, @ (or tab/CR) could be run as formulas when the
     * file is opened in a spreadsheet; prefix them with an apostrophe so they stay plain text.
     */
    fun text(value: String): String =
        if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value
}
