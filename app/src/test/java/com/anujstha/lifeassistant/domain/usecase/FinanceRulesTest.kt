package com.anujstha.lifeassistant.domain.usecase

import com.anujstha.lifeassistant.data.entity.BudgetEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class FinanceRulesTest {

    @Test
    fun budgetStatus_percentRoundsDown() {
        val s = BudgetStatus(limitMinor = 30_000, spentMinor = 10_000, warnAtPercent = 80)
        assertEquals(33, s.percentUsed)
        assertEquals(20_000, s.remainingMinor)
        assertEquals(BudgetLevel.OK, s.level)
    }

    @Test
    fun budgetStatus_warningFromThreshold_exceededOnlyWhenOver() {
        assertEquals(BudgetLevel.OK, BudgetStatus(10_000, 7_999, 80).level)
        assertEquals(BudgetLevel.WARNING, BudgetStatus(10_000, 8_000, 80).level)
        assertEquals(BudgetLevel.WARNING, BudgetStatus(10_000, 10_000, 80).level) // exactly at limit
        val over = BudgetStatus(10_000, 10_001, 80)
        assertEquals(BudgetLevel.EXCEEDED, over.level)
        assertEquals(-1, over.remainingMinor)
        assertEquals(100, over.percentUsed)
    }

    @Test
    fun budgetStatus_hugeValuesDoNotOverflow() {
        // spent * 100 would overflow a Long here; BigDecimal keeps it exact.
        val s = BudgetStatus(limitMinor = 4_000_000_000_000_000_000, spentMinor = 2_000_000_000_000_000_000, warnAtPercent = 80)
        assertEquals(50, s.percentUsed)
    }

    @Test
    fun weekOf_respectsFirstDayOfWeek() {
        val wed = LocalDate.of(2026, 10, 14)
        assertEquals(LocalDate.of(2026, 10, 11) to LocalDate.of(2026, 10, 17), FinanceRules.weekOf(wed, DayOfWeek.SUNDAY))
        assertEquals(LocalDate.of(2026, 10, 12) to LocalDate.of(2026, 10, 18), FinanceRules.weekOf(wed, DayOfWeek.MONDAY))
        val sun = LocalDate.of(2026, 10, 11)
        assertEquals(sun, FinanceRules.weekOf(sun, DayOfWeek.SUNDAY).first)
    }

    @Test
    fun monthOf_coversWholeMonth() {
        assertEquals(LocalDate.of(2028, 2, 1) to LocalDate.of(2028, 2, 29), FinanceRules.monthOf(LocalDate.of(2028, 2, 10)))
    }

    private fun budget(month: String, categoryId: String?, limit: Long) = BudgetEntity(
        "$month-$categoryId", YearMonth.parse(month), categoryId, limit, "NPR", 80, Instant.EPOCH, Instant.EPOCH,
    )

    @Test
    fun effectiveBudgets_carryForward_latestWins_zeroMeansRemoved() {
        val all = listOf(
            budget("2026-08", null, 50_000),
            budget("2026-10", null, 60_000),
            budget("2026-08", "food", 10_000),
            budget("2026-09", "fun", 5_000),
            budget("2026-10", "fun", 0), // removed from October
        )
        val sept = FinanceRules.effectiveBudgets(all, YearMonth.of(2026, 9)).associate { it.categoryId to it.limitMinor }
        assertEquals(mapOf(null to 50_000L, "food" to 10_000L, "fun" to 5_000L), sept)

        val oct = FinanceRules.effectiveBudgets(all, YearMonth.of(2026, 10)).associate { it.categoryId to it.limitMinor }
        assertEquals(mapOf(null to 60_000L, "food" to 10_000L), oct)

        assertTrue(FinanceRules.effectiveBudgets(all, YearMonth.of(2026, 7)).isEmpty())
    }
}
