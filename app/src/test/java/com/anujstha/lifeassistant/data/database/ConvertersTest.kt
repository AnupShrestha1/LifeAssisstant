package com.anujstha.lifeassistant.data.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

class ConvertersTest {
    private val c = Converters()

    @Test
    fun roundTrips() {
        val instant = Instant.parse("2026-10-14T06:15:30.123Z")
        assertEquals(instant, c.longToInstant(c.instantToLong(instant)))

        val date = LocalDate.of(2026, 2, 28)
        assertEquals(date, c.stringToLocalDate(c.localDateToString(date)))

        val time = LocalTime.of(23, 59, 59)
        assertEquals(time, c.intToLocalTime(c.localTimeToInt(time)))

        val ldt = LocalDateTime.of(2026, 10, 14, 8, 0)
        assertEquals(ldt, c.stringToLocalDateTime(c.localDateTimeToString(ldt)))

        val ym = YearMonth.of(2026, 1)
        assertEquals(ym, c.stringToYearMonth(c.yearMonthToString(ym)))
    }

    @Test
    fun nullsStayNull() {
        assertNull(c.instantToLong(null))
        assertNull(c.stringToLocalDate(null))
        assertNull(c.intToLocalTime(null))
        assertNull(c.stringToYearMonth(null))
    }

    @Test
    fun storedDateStrings_sortChronologically() {
        val dates = listOf(LocalDate.of(2026, 10, 2), LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 15))
        val asStrings = dates.map { c.localDateToString(it)!! }
        assertEquals(dates.sorted().map { it.toString() }, asStrings.sorted())
    }

    @Test
    fun timesStoreAsSecondsOfDay_soTheySortChronologically() {
        assertEquals(0, c.localTimeToInt(LocalTime.MIDNIGHT))
        assertTrue(c.localTimeToInt(LocalTime.of(9, 5))!! < c.localTimeToInt(LocalTime.of(10, 0))!!)
    }
}
