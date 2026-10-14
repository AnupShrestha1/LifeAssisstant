package com.anujstha.lifeassistant.domain.usecase

import com.anujstha.lifeassistant.data.entity.MonthTotal
import java.time.YearMonth

/** Pure helpers behind the reports and charts. */
object ReportMath {

    /** [count] consecutive months ending at [lastMonth], oldest first, with 0 for months without data. */
    fun fillMonths(totals: List<MonthTotal>, lastMonth: YearMonth, count: Int): List<Pair<YearMonth, Long>> {
        val byMonth = totals.associate { YearMonth.parse(it.yearMonth) to it.totalMinor }
        return (count - 1 downTo 0).map { back ->
            val m = lastMonth.minusMonths(back.toLong())
            m to (byMonth[m] ?: 0L)
        }
    }

    /**
     * Whole-number percentages that always add up to exactly 100 (largest-remainder method), so a
     * breakdown list never shows 33 + 33 + 33 = 99. Empty or all-zero input gives zeros.
     */
    fun percentages(values: List<Long>): List<Int> {
        val total = values.sum()
        if (total <= 0) return values.map { 0 }
        // Explicit scale: Kotlin's `/` on BigDecimal would keep scale 0 and round away the remainders.
        val exact = values.map {
            (it.toBigDecimal() * 100.toBigDecimal()).divide(total.toBigDecimal(), 6, java.math.RoundingMode.DOWN)
        }
        val floors = exact.map { it.toInt() }.toMutableList()
        var remaining = 100 - floors.sum()
        exact.withIndex()
            .sortedByDescending { (i, e) -> e - floors[i].toBigDecimal() }
            .forEach { (i, _) -> if (remaining > 0) { floors[i]++; remaining-- } }
        return floors
    }

    /** Donut slice angles (degrees) proportional to [values], summing to 360 when any value is positive. */
    fun sweepAngles(values: List<Long>): List<Float> {
        val total = values.sum()
        if (total <= 0) return values.map { 0f }
        return values.map { (it.toDouble() / total * 360.0).toFloat() }
    }
}
