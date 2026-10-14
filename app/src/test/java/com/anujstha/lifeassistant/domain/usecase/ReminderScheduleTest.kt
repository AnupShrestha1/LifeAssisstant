package com.anujstha.lifeassistant.domain.usecase

import com.anujstha.lifeassistant.domain.model.RecurrenceFrequency.DAILY
import com.anujstha.lifeassistant.domain.model.RecurrenceFrequency.MONTHLY
import com.anujstha.lifeassistant.domain.model.RecurrenceFrequency.WEEKLY
import com.anujstha.lifeassistant.domain.model.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReminderScheduleTest {

    private val kathmandu = ZoneId.of("Asia/Kathmandu") // UTC+05:45, no DST
    private val newYork = ZoneId.of("America/New_York") // has DST

    private fun at(text: String) = Instant.parse(text)
    private fun next(start: LocalDateTime, zone: ZoneId, rule: RecurrenceRule?, after: Instant) =
        ReminderSchedule.nextOccurrenceAfter(start, zone, rule, after)

    @Test
    fun oneTime_futureFiresOnce_pastHasNoOccurrence() {
        val start = LocalDateTime.of(2026, 10, 14, 18, 0) // 12:15Z in Kathmandu
        assertEquals(at("2026-10-14T12:15:00Z"), next(start, kathmandu, null, at("2026-10-14T06:00:00Z")))
        assertNull(next(start, kathmandu, null, at("2026-10-14T12:15:00Z"))) // strictly after
        assertNull(next(start, kathmandu, null, at("2026-10-15T00:00:00Z")))
    }

    @Test
    fun daily_keepsLocalWallClockAcrossDstChange() {
        // 08:00 daily in New York. DST ends Sun 2026-11-01: UTC offset goes from -4 to -5.
        val start = LocalDateTime.of(2026, 10, 30, 8, 0)
        val rule = RecurrenceRule(DAILY)
        val sat = next(start, newYork, rule, at("2026-10-30T13:00:00Z"))!! // after Friday's
        val sun = next(start, newYork, rule, sat)!!
        assertEquals(at("2026-10-31T12:00:00Z"), sat) // 08:00 EDT
        assertEquals(at("2026-11-01T13:00:00Z"), sun) // 08:00 EST — still 08:00 local
        assertEquals(LocalTime.of(8, 0), sun.atZone(newYork).toLocalTime())
    }

    @Test
    fun nonexistentLocalTime_inDstGap_movesForward() {
        // 02:30 doesn't exist on 2027-03-14 in New York (clocks jump 02:00 → 03:00).
        val start = LocalDateTime.of(2027, 3, 13, 2, 30)
        val gapDay = next(start, newYork, RecurrenceRule(DAILY), at("2027-03-13T08:00:00Z"))!!
        assertEquals(LocalDateTime.of(2027, 3, 14, 3, 30), gapDay.atZone(newYork).toLocalDateTime())
    }

    @Test
    fun weekly_andMonthly() {
        val start = LocalDateTime.of(2026, 1, 31, 9, 0)
        val monthly = next(start, kathmandu, RecurrenceRule(MONTHLY), at("2026-02-01T00:00:00Z"))!!
        assertEquals(LocalDate.of(2026, 2, 28), monthly.atZone(kathmandu).toLocalDate())
        val weekly = next(start, kathmandu, RecurrenceRule(WEEKLY, interval = 2), at("2026-02-01T00:00:00Z"))!!
        assertEquals(LocalDate.of(2026, 2, 14), weekly.atZone(kathmandu).toLocalDate())
    }

    @Test
    fun longDowntime_jumpsToNextFutureOccurrence() {
        val start = LocalDateTime.of(2025, 1, 1, 7, 0)
        val after = at("2026-10-14T06:00:00Z") // 11:45 in Kathmandu → today's 07:00 has passed
        val result = next(start, kathmandu, RecurrenceRule(DAILY), after)!!
        assertEquals(LocalDateTime.of(2026, 10, 15, 7, 0), result.atZone(kathmandu).toLocalDateTime())
    }

    @Test
    fun endDate_stopsRepeating() {
        val start = LocalDateTime.of(2026, 10, 14, 9, 0)
        val rule = RecurrenceRule(DAILY, endDate = LocalDate.of(2026, 10, 15))
        val last = next(start, kathmandu, rule, at("2026-10-14T05:00:00Z"))!!
        assertEquals(LocalDate.of(2026, 10, 15), last.atZone(kathmandu).toLocalDate())
        assertNull(next(start, kathmandu, rule, last))
    }

    @Test
    fun sameWallClockInDifferentZones_givesDifferentInstants() {
        // A reminder's zone is part of its definition; the display-zone setting is not an input at all.
        val start = LocalDateTime.of(2026, 10, 20, 9, 0)
        val now = at("2026-10-14T00:00:00Z")
        assertEquals(at("2026-10-20T03:15:00Z"), next(start, kathmandu, null, now))
        assertEquals(at("2026-10-20T13:00:00Z"), next(start, newYork, null, now))
    }
}
