package com.anujstha.lifeassistant.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Money conversion. Amounts are stored as integer **minor units** (e.g. paisa for NPR, cents for
 * USD) in a Long; BigDecimal is used only to convert to/from text. Float/Double are never used.
 */
object Money {

    private val DIGITS = Regex("^\\p{Nd}+(\\.\\p{Nd}+)?$")

    /** Decimal places for the currency (NPR 2, JPY 0). Pseudo-currencies with -1 are treated as 2. */
    fun fractionDigits(currencyCode: String): Int =
        Currency.getInstance(currencyCode).defaultFractionDigits.let { if (it < 0) 2 else it }

    /**
     * Parses user input such as "1,234.50" or "250" into minor units. Accepts the locale's decimal
     * separator, ignores grouping separators and spaces. Returns null for anything that isn't a
     * plain non-negative number, or that has more decimal places than the currency allows.
     */
    fun parse(input: String, currencyCode: String, locale: Locale = Locale.getDefault()): Long? {
        val decimalSeparator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        var text = input.trim().filterNot { it.isWhitespace() || it == ' ' || it == ' ' || it == '\'' }
        text = if (decimalSeparator == ',') {
            // "1.234,5" -> "1234.5"
            text.replace(".", "").replace(',', '.')
        } else {
            text.replace(",", "")
        }
        if (!DIGITS.matches(text)) return null
        val value = try {
            BigDecimal(text)
        } catch (e: NumberFormatException) {
            return null
        }
        val digits = fractionDigits(currencyCode)
        if (value.stripTrailingZeros().scale() > digits) return null
        return try {
            value.movePointRight(digits).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        } catch (e: ArithmeticException) {
            null // too large for a Long
        }
    }

    fun toDecimal(minor: Long, currencyCode: String): BigDecimal =
        BigDecimal.valueOf(minor, fractionDigits(currencyCode))

    /** Plain machine-readable amount, e.g. "1234.50" (used in the edit field and CSV). */
    fun plain(minor: Long, currencyCode: String): String = toDecimal(minor, currencyCode).toPlainString()

    /** Locale-aware display, e.g. "NPR 1,234.50" (en) or "रू १,२३४.५०" (ne-NP). */
    fun format(minor: Long, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val currency = Currency.getInstance(currencyCode)
        val digits = fractionDigits(currencyCode)
        val format = NumberFormat.getCurrencyInstance(locale).apply {
            this.currency = currency
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }
        return format.format(toDecimal(minor, currencyCode))
    }
}
