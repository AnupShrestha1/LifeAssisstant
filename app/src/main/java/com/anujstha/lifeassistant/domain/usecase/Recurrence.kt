package com.anujstha.lifeassistant.domain.usecase

import com.anujstha.lifeassistant.domain.model.RecurrenceFrequency
import com.anujstha.lifeassistant.domain.model.RecurrenceRule
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Date arithmetic for repeating series.
 *
 * Occurrences are always computed from the series start ("anchor"), never from the previous
 * occurrence, so monthly series don't drift: a series starting Jan 31 goes Jan 31, Feb 28 (or 29),
 * Mar 31, Apr 30 — not Jan 31, Feb 28, Mar 28, Apr 28.
 */
object Recurrence {

    /** The [n]th occurrence (0 = the anchor itself). */
    fun occurrence(anchor: LocalDate, rule: RecurrenceRule, n: Long): LocalDate {
        val steps = n * rule.interval
        return when (rule.frequency) {
            RecurrenceFrequency.DAILY -> anchor.plusDays(steps)
            RecurrenceFrequency.WEEKLY -> anchor.plusWeeks(steps)
            RecurrenceFrequency.MONTHLY -> anchor.plusMonths(steps) // clamps to month end
        }
    }

    /**
     * The first occurrence strictly after [after], or null if it would fall after the rule's
     * end date. Dates before the anchor return the anchor itself.
     */
    fun nextAfter(anchor: LocalDate, rule: RecurrenceRule, after: LocalDate): LocalDate? {
        val unit = when (rule.frequency) {
            RecurrenceFrequency.DAILY -> ChronoUnit.DAYS
            RecurrenceFrequency.WEEKLY -> ChronoUnit.WEEKS
            RecurrenceFrequency.MONTHLY -> ChronoUnit.MONTHS
        }
        // Start from an estimate just below the answer, then step forward (at most a couple of steps).
        var n = maxOf(0L, unit.between(anchor, after) / rule.interval)
        var candidate = occurrence(anchor, rule, n)
        while (!candidate.isAfter(after)) {
            n++
            candidate = occurrence(anchor, rule, n)
        }
        val end = rule.endDate
        return if (end != null && candidate.isAfter(end)) null else candidate
    }
}
