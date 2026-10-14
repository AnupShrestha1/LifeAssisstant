package com.anujstha.lifeassistant.domain.usecase

import com.anujstha.lifeassistant.domain.model.RecurrenceFrequency.DAILY
import com.anujstha.lifeassistant.domain.model.RecurrenceFrequency.MONTHLY
import com.anujstha.lifeassistant.domain.model.RecurrenceFrequency.WEEKLY
import com.anujstha.lifeassistant.domain.model.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RecurrenceTest {

    private fun d(text: String) = LocalDate.parse(text)

    /** Walks a series by repeatedly asking for the next occurrence, like completing each one. */
    private fun series(anchor: LocalDate, rule: RecurrenceRule, count: Int): List<LocalDate> {
        val out = mutableListOf(anchor)
        while (out.size < count) out += Recurrence.nextAfter(anchor, rule, out.last()) ?: break
        return out
    }

    @Test
    fun daily_everyDay_andEveryThreeDays() {
        assertEquals(d("2026-10-15"), Recurrence.nextAfter(d("2026-10-14"), RecurrenceRule(DAILY), d("2026-10-14")))
        assertEquals(
            listOf(d("2026-10-14"), d("2026-10-17"), d("2026-10-20")),
            series(d("2026-10-14"), RecurrenceRule(DAILY, interval = 3), 3),
        )
    }

    @Test
    fun weekly_keepsWeekday_andCrossesYearEnd() {
        assertEquals(
            listOf(d("2026-12-24"), d("2026-12-31"), d("2027-01-07")),
            series(d("2026-12-24"), RecurrenceRule(WEEKLY), 3),
        )
        assertEquals(d("2026-10-28"), Recurrence.nextAfter(d("2026-10-14"), RecurrenceRule(WEEKLY, 2), d("2026-10-14")))
    }

    @Test
    fun monthly_fromThe31st_clampsButDoesNotDrift() {
        assertEquals(
            listOf(d("2026-01-31"), d("2026-02-28"), d("2026-03-31"), d("2026-04-30"), d("2026-05-31")),
            series(d("2026-01-31"), RecurrenceRule(MONTHLY), 5),
        )
    }

    @Test
    fun monthly_handlesLeapYear() {
        assertEquals(d("2028-02-29"), Recurrence.nextAfter(d("2028-01-31"), RecurrenceRule(MONTHLY), d("2028-01-31")))
    }

    @Test
    fun endDate_isInclusive_andStopsTheSeries() {
        val rule = RecurrenceRule(DAILY, endDate = d("2026-10-16"))
        assertEquals(d("2026-10-16"), Recurrence.nextAfter(d("2026-10-14"), rule, d("2026-10-15")))
        assertNull(Recurrence.nextAfter(d("2026-10-14"), rule, d("2026-10-16")))
    }

    @Test
    fun skipsAheadWhenCompletedLate() {
        // Weekly from Oct 1; the Oct 8 occurrence is completed on paper "after" Oct 20 → next is Oct 22.
        assertEquals(d("2026-10-22"), Recurrence.nextAfter(d("2026-10-01"), RecurrenceRule(WEEKLY), d("2026-10-20")))
    }

    @Test
    fun beforeAnchor_returnsAnchor() {
        assertEquals(d("2026-10-14"), Recurrence.nextAfter(d("2026-10-14"), RecurrenceRule(DAILY), d("2026-10-01")))
    }
}
