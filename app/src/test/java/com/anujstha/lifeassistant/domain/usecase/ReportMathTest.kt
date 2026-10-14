package com.anujstha.lifeassistant.domain.usecase

import com.anujstha.lifeassistant.data.entity.MonthTotal
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class ReportMathTest {

    @Test
    fun fillMonths_padsMissingMonthsWithZero_acrossYearEnd() {
        val filled = ReportMath.fillMonths(
            listOf(MonthTotal("2025-11", 500), MonthTotal("2026-01", 700)),
            lastMonth = YearMonth.of(2026, 2),
            count = 4,
        )
        assertEquals(
            listOf(YearMonth.of(2025, 11) to 500L, YearMonth.of(2025, 12) to 0L, YearMonth.of(2026, 1) to 700L, YearMonth.of(2026, 2) to 0L),
            filled,
        )
    }

    @Test
    fun percentages_alwaysSumTo100() {
        assertEquals(listOf(34, 33, 33), ReportMath.percentages(listOf(1, 1, 1)))
        assertEquals(100, ReportMath.percentages(listOf(7, 11, 13, 17, 19)).sum())
        assertEquals(listOf(100), ReportMath.percentages(listOf(5)))
        assertEquals(listOf(0, 0), ReportMath.percentages(listOf(0, 0)))
        assertEquals(emptyList<Int>(), ReportMath.percentages(emptyList()))
    }

    @Test
    fun percentages_largestRemainderGetsTheExtraPoint() {
        // 2/3 = 66.67, 1/3 = 33.33 -> 67 + 33
        assertEquals(listOf(67, 33), ReportMath.percentages(listOf(2, 1)))
    }

    @Test
    fun sweepAngles_sumTo360() {
        val angles = ReportMath.sweepAngles(listOf(1, 2, 3))
        assertEquals(360f, angles.sum(), 0.01f)
        assertEquals(60f, angles[0], 0.01f)
        assertEquals(listOf(0f, 0f), ReportMath.sweepAngles(listOf(0, 0)))
    }
}
