package com.anujstha.lifeassistant.domain.usecase

import com.anujstha.lifeassistant.data.entity.BudgetEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

enum class BudgetLevel { OK, WARNING, EXCEEDED }

/** How much of a budget is used. All values in minor units; no floating point. */
data class BudgetStatus(
    val limitMinor: Long,
    val spentMinor: Long,
    val warnAtPercent: Int,
) {
    /** Negative when over budget. */
    val remainingMinor: Long get() = limitMinor - spentMinor

    /** Whole percent used, rounded down; can exceed 100. */
    val percentUsed: Int
        get() = if (limitMinor <= 0) 0 else BigDecimal.valueOf(spentMinor).multiply(BigDecimal(100))
            .divide(BigDecimal.valueOf(limitMinor), 0, RoundingMode.DOWN)
            .min(BigDecimal(9_999)).toInt()

    /** EXCEEDED only when strictly over the limit; WARNING from the threshold up to the limit. */
    val level: BudgetLevel
        get() = when {
            spentMinor > limitMinor -> BudgetLevel.EXCEEDED
            percentUsed >= warnAtPercent -> BudgetLevel.WARNING
            else -> BudgetLevel.OK
        }
}

object FinanceRules {

    /** Inclusive [start, end] of the week containing [date]. */
    fun weekOf(date: LocalDate, firstDayOfWeek: DayOfWeek): Pair<LocalDate, LocalDate> {
        val start = date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        return start to start.plusDays(6)
    }

    fun monthOf(date: LocalDate): Pair<LocalDate, LocalDate> {
        val month = YearMonth.from(date)
        return month.atDay(1) to month.atEndOfMonth()
    }

    /**
     * Budgets in force for [month]: for each scope (overall or a category), the most recent budget
     * set in or before that month. A budget therefore carries forward until changed. A row with a
     * limit of 0 means "no budget from this month on".
     */
    fun effectiveBudgets(budgetsUpToMonth: List<BudgetEntity>, month: YearMonth): List<BudgetEntity> =
        budgetsUpToMonth
            .filter { it.yearMonth <= month }
            .sortedByDescending { it.yearMonth }
            .distinctBy { it.categoryId }
            .filter { it.limitMinor > 0 }
}
