package com.anujstha.lifeassistant.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Cells for a month calendar: whole weeks starting on [firstDayOfWeek], with null for the
 * padding days before the 1st and after the last day. Always a multiple of 7 cells.
 */
fun monthCells(month: YearMonth, firstDayOfWeek: DayOfWeek): List<LocalDate?> {
    val first = month.atDay(1)
    val leading = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
    val cells = List<LocalDate?>(leading) { null } + days
    val trailing = (7 - cells.size % 7) % 7
    return cells + List(trailing) { null }
}

/** Day-of-week order for the header row, starting on [firstDayOfWeek]. */
fun weekOrder(firstDayOfWeek: DayOfWeek): List<DayOfWeek> = List(7) { firstDayOfWeek.plus(it.toLong()) }
