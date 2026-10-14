package com.anujstha.lifeassistant.domain.model

import java.time.LocalDate

/**
 * Repeat every [interval] [frequency] units, optionally until [endDate] (inclusive).
 * Stored as embedded columns on tasks and reminders; a null rule means "does not repeat".
 */
data class RecurrenceRule(
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val endDate: LocalDate? = null,
) {
    init {
        require(interval in 1..MAX_INTERVAL) { "interval must be between 1 and $MAX_INTERVAL" }
    }

    companion object {
        const val MAX_INTERVAL = 365
    }
}
