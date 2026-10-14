package com.anujstha.lifeassistant.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class CalendarGridTest {

    @Test
    fun october2026_startingSunday() {
        // Oct 1, 2026 is a Thursday.
        val cells = monthCells(YearMonth.of(2026, 10), DayOfWeek.SUNDAY)
        assertEquals(0, cells.size % 7)
        assertEquals(4, cells.indexOfFirst { it != null }) // Sun, Mon, Tue, Wed padded
        assertEquals(LocalDate.of(2026, 10, 1), cells[4])
        assertEquals(31, cells.count { it != null })
        // Oct 31 is a Saturday, the last column, so there is no trailing padding.
        assertEquals(35, cells.size)
        assertEquals(LocalDate.of(2026, 10, 31), cells.last())
    }

    @Test
    fun october2026_startingMonday_padsTheEnd() {
        val cells = monthCells(YearMonth.of(2026, 10), DayOfWeek.MONDAY)
        assertEquals(3, cells.indexOfFirst { it != null }) // Mon, Tue, Wed padded
        assertEquals(35, cells.size)
        assertEquals(LocalDate.of(2026, 10, 31), cells[33]) // Saturday column
        assertNull(cells.last()) // Sunday padding
    }

    @Test
    fun february2026_fitsExactlyFourWeeks_whenStartingSunday() {
        // Feb 1, 2026 is a Sunday and February 2026 has 28 days.
        val cells = monthCells(YearMonth.of(2026, 2), DayOfWeek.SUNDAY)
        assertEquals(28, cells.size)
        assertEquals(LocalDate.of(2026, 2, 1), cells.first())
        assertEquals(LocalDate.of(2026, 2, 28), cells.last())
    }

    @Test
    fun weekOrder_wrapsAround() {
        assertEquals(
            listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY),
            weekOrder(DayOfWeek.SATURDAY).take(3),
        )
    }
}
