package com.anujstha.lifeassistant.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Emits the current date in [zone], then re-emits at each following midnight in that zone,
 * so screens showing "today" stay correct if the app stays open overnight.
 */
fun currentDateFlow(clock: Clock, zone: ZoneId): Flow<LocalDate> = flow {
    while (true) {
        val now = clock.instant()
        val today = now.atZone(zone).toLocalDate()
        emit(today)
        val nextMidnight = today.plusDays(1).atStartOfDay(zone).toInstant()
        // +1 ms so we land on the new day rather than exactly at the boundary.
        delay(Duration.between(now, nextMidnight).toMillis().coerceAtLeast(0) + 1)
    }
}

/**
 * Emits the current instant now and then at the start of every following minute, so time-based
 * states (e.g. "overdue since 14:30") update while a screen is open.
 */
fun minuteTicks(clock: Clock): Flow<Instant> = flow {
    while (true) {
        val now = clock.instant()
        emit(now)
        val millisIntoMinute = now.toEpochMilli() % 60_000
        delay(60_000 - millisIntoMinute)
    }
}
