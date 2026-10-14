package com.anujstha.lifeassistant.domain.usecase

import com.anujstha.lifeassistant.domain.model.RecurrenceRule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * When a reminder should fire.
 *
 * A reminder is defined by a wall-clock start ([LocalDateTime]) in a specific [ZoneId], plus an
 * optional repeat rule. Each occurrence keeps the same wall-clock time in that zone, so a daily
 * 08:00 reminder fires at 08:00 local time on both sides of a daylight-saving change (the UTC
 * instant shifts by an hour). If a wall-clock time doesn't exist on a given day (DST gap), the
 * standard java.time rule applies: it moves forward by the gap length.
 *
 * The display time zone preference is never involved, so changing it can't move a reminder.
 */
object ReminderSchedule {

    fun instantOf(date: LocalDate, start: LocalDateTime, zone: ZoneId): Instant =
        date.atTime(start.toLocalTime()).atZone(zone).toInstant()

    /**
     * The first occurrence strictly after [after], or null if there is none (a one-time reminder
     * already in the past, or a repeating one past its end date). Long gaps (e.g. the phone was
     * off for weeks) jump straight to the next future occurrence.
     */
    fun nextOccurrenceAfter(
        start: LocalDateTime,
        zone: ZoneId,
        rule: RecurrenceRule?,
        after: Instant,
    ): Instant? {
        val first = start.atZone(zone).toInstant()
        if (first.isAfter(after)) return first
        if (rule == null) return null

        val anchor = start.toLocalDate()
        // First occurrence on or after the local date of `after` (one day of slack for zone offsets)...
        val afterDate = after.atZone(zone).toLocalDate().minusDays(1)
        var date: LocalDate = if (afterDate.isBefore(anchor)) anchor else Recurrence.nextAfter(anchor, rule, afterDate) ?: return null
        // ...then step forward until the instant is in the future.
        while (!instantOf(date, start, zone).isAfter(after)) {
            date = Recurrence.nextAfter(anchor, rule, date) ?: return null
        }
        return instantOf(date, start, zone)
    }
}
