package com.anujstha.lifeassistant.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class TimeFlowsTest {

    /** A clock that advances with the coroutine test scheduler's virtual time. */
    private fun TestScope.virtualClock(start: Instant) = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = start.plusMillis(testScheduler.currentTime)
    }

    @Test
    fun emitsTodayThenRollsOverAtLocalMidnight() = runTest {
        val kathmandu = ZoneId.of("Asia/Kathmandu") // UTC+05:45
        // 23:59:00 on 2026-10-14 in Kathmandu == 18:14:00 UTC.
        val start = Instant.parse("2026-10-14T18:14:00Z")

        val dates = currentDateFlow(virtualClock(start), kathmandu).take(3).toList()

        assertEquals(
            listOf(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 16)),
            dates,
        )
        // Second emission must come ~60s in (at local midnight), not after a full UTC day.
        // Third comes one day after that.
        assertEquals(60_001L + 86_400_000L, testScheduler.currentTime)
    }

    @Test
    fun sameInstant_givesDifferentDatesInDifferentZones() = runTest {
        // 2026-10-14T20:00Z is already Oct 15 in Kathmandu but still Oct 14 in New York.
        val clock = virtualClock(Instant.parse("2026-10-14T20:00:00Z"))

        val ktm = currentDateFlow(clock, ZoneId.of("Asia/Kathmandu")).take(1).toList().single()
        val nyc = currentDateFlow(clock, ZoneId.of("America/New_York")).take(1).toList().single()

        assertEquals(LocalDate.of(2026, 10, 15), ktm)
        assertEquals(LocalDate.of(2026, 10, 14), nyc)
    }
}
