package com.anujstha.lifeassistant.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MoneyTest {

    private val us = Locale.US

    @Test
    fun parse_wholeAndDecimalAmounts_toMinorUnits() {
        assertEquals(25_000L, Money.parse("250", "NPR", us))
        assertEquals(123_450L, Money.parse("1,234.50", "NPR", us))
        assertEquals(123_450L, Money.parse("1234.5", "NPR", us))
        assertEquals(1L, Money.parse("0.01", "NPR", us))
        assertEquals(10_000L, Money.parse(" 100.00 ", "NPR", us))
    }

    @Test
    fun parse_rejectsInvalidInput() {
        assertNull(Money.parse("", "NPR", us))
        assertNull(Money.parse("abc", "NPR", us))
        assertNull(Money.parse("-5", "NPR", us))
        assertNull(Money.parse("1.2.3", "NPR", us))
        assertNull(Money.parse("0.005", "NPR", us)) // more decimals than the currency has
        assertNull(Money.parse("1e5", "NPR", us))
        assertNull(Money.parse("99999999999999999999", "NPR", us)) // overflows Long
    }

    @Test
    fun parse_trailingZerosBeyondScaleAreFine() {
        assertEquals(150L, Money.parse("1.500", "NPR", us))
    }

    @Test
    fun parse_respectsCurrencyDecimals() {
        assertEquals(1_500L, Money.parse("1500", "JPY", us)) // JPY has no minor unit
        assertNull(Money.parse("1500.5", "JPY", us))
        assertEquals(1_500L, Money.parse("1.500", "KWD", us)) // 3 decimals
    }

    @Test
    fun parse_commaDecimalLocales() {
        assertEquals(123_450L, Money.parse("1.234,50", "EUR", Locale.GERMANY))
        assertEquals(1_050L, Money.parse("10,5", "EUR", Locale.GERMANY))
    }

    @Test
    fun parse_acceptsDevanagariDigits() {
        assertEquals(25_000L, Money.parse("२५०", "NPR", Locale.forLanguageTag("ne-NP")))
    }

    @Test
    fun noFloatingPointDrift() {
        // 0.1 + 0.2 style sums stay exact in minor units.
        val total = listOf("0.10", "0.20").sumOf { Money.parse(it, "NPR", us)!! }
        assertEquals("0.30", Money.plain(total, "NPR"))
    }

    @Test
    fun plain_andFormat() {
        assertEquals("1234.50", Money.plain(123_450, "NPR"))
        assertEquals("1500", Money.plain(1_500, "JPY"))
        val formatted = Money.format(123_450, "NPR", us)
        assertTrue(formatted, formatted.contains("1,234.50"))
        assertTrue(formatted, formatted.contains("NPR"))
    }
}
