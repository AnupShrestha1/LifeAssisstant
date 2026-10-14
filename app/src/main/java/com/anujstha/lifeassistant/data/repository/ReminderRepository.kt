package com.anujstha.lifeassistant.data.repository

import androidx.room.withTransaction
import com.anujstha.lifeassistant.data.database.AppDatabase
import com.anujstha.lifeassistant.data.entity.ReminderEntity
import com.anujstha.lifeassistant.data.entity.ReminderEventEntity
import com.anujstha.lifeassistant.data.entity.ReminderEventWithReminder
import com.anujstha.lifeassistant.domain.model.RecurrenceRule
import com.anujstha.lifeassistant.domain.model.validate
import com.anujstha.lifeassistant.domain.usecase.ReminderSchedule
import com.anujstha.lifeassistant.notifications.ReminderScheduler
import com.anujstha.lifeassistant.util.IdGenerator
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

data class ReminderInput(
    val title: String,
    val description: String? = null,
    val taskId: String? = null,
    /** Wall-clock start in [zoneId]. */
    val startAt: LocalDateTime,
    val zoneId: String,
    val recurrence: RecurrenceRule? = null,
    val enabled: Boolean = true,
)

/** What to do with an alarm that just fired. */
sealed interface AlarmOutcome {
    /** Record created: show [event] for [reminder]. */
    data class Deliver(val reminder: ReminderEntity, val event: ReminderEventEntity) : AlarmOutcome

    /** This occurrence was already recorded (duplicate alarm delivery): do not notify again. */
    data object Duplicate : AlarmOutcome

    /** Reminder deleted, disabled, or rescheduled since the alarm was set: nothing to show. */
    data object Ignored : AlarmOutcome
}

/**
 * Reminder storage plus alarm scheduling. Every write keeps the database and the OS alarm in
 * step: [ReminderEntity.nextTriggerAt] is exactly the alarm that is registered (or null = none).
 */
class ReminderRepository(
    private val db: AppDatabase,
    private val scheduler: ReminderScheduler,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    private val dao = db.reminderDao()
    private val eventDao = db.reminderEventDao()

    fun observe(id: String): Flow<ReminderEntity?> = dao.observe(id)
    fun observeAll(): Flow<List<ReminderEntity>> = dao.observeAll()
    fun observeForTask(taskId: String): Flow<List<ReminderEntity>> = dao.observeForTask(taskId)
    fun observeUpcoming(limit: Int): Flow<List<ReminderEntity>> = dao.observeUpcoming(clock.instant(), limit)

    /** Fired reminders not yet dismissed — the in-app inbox (works without notification permission). */
    fun observeActiveEvents(): Flow<List<ReminderEventWithReminder>> = eventDao.observeActive()
    fun observeActiveEventCount(): Flow<Int> = eventDao.observeActiveCount()

    fun canScheduleExact(): Boolean = scheduler.canScheduleExact()

    /** Creates ([id] == null) or updates a reminder and (re)schedules its alarm. Returns the id. */
    suspend fun save(id: String?, input: ReminderInput): String {
        val title = input.title.trim()
        validate(title.isNotEmpty()) { "Title can't be empty" }
        validate(title.length <= TaskRepository.MAX_TITLE) { "Title is too long" }
        val description = input.description?.trim()?.takeIf { it.isNotEmpty() }
        validate((description?.length ?: 0) <= MAX_DESCRIPTION) { "Description is too long" }
        val zone = try {
            ZoneId.of(input.zoneId)
        } catch (e: DateTimeException) {
            throw com.anujstha.lifeassistant.domain.model.ValidationException("Unknown time zone")
        }
        val endDate = input.recurrence?.endDate
        validate(endDate == null || !endDate.isBefore(input.startAt.toLocalDate())) { "Repeat end date is before the start" }

        val now = clock.instant()
        val next = ReminderSchedule.nextOccurrenceAfter(input.startAt, zone, input.recurrence, now)
        validate(next != null || !input.enabled) { "That time is in the past" }

        val saved = db.withTransaction {
            val existing = id?.let { dao.get(it) ?: throw NoSuchElementException("Reminder not found") }
            if (input.taskId != null) validate(db.taskDao().get(input.taskId) != null) { "Task not found" }
            ReminderEntity(
                id = existing?.id ?: ids.newId(),
                requestCode = existing?.requestCode ?: dao.nextRequestCode(),
                title = title,
                description = description,
                taskId = input.taskId,
                startAt = input.startAt,
                zoneId = input.zoneId,
                recurrence = input.recurrence,
                nextTriggerAt = if (input.enabled) next else null,
                enabled = input.enabled,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ).also { dao.upsert(it) }
        }
        applyAlarm(saved)
        return saved.id
    }

    /** Turning a reminder off cancels its alarm; turning it on schedules the next future occurrence. */
    suspend fun setEnabled(id: String, enabled: Boolean) {
        val updated = db.withTransaction {
            val reminder = dao.get(id) ?: return@withTransaction null
            val next = if (enabled) nextAfterNow(reminder) else null
            reminder.copy(enabled = enabled && next != null, nextTriggerAt = next, updatedAt = clock.instant())
                .also { dao.upsert(it) }
        } ?: return
        applyAlarm(updated)
    }

    suspend fun delete(id: String) {
        val reminder = dao.get(id) ?: return
        scheduler.cancel(reminder)
        dao.delete(id)
    }

    suspend fun dismissEvent(eventId: String) {
        eventDao.dismiss(eventId, clock.instant())
    }

    suspend fun dismissAllEvents() {
        eventDao.dismissAll(clock.instant())
    }

    /**
     * Handles an alarm for [reminderId] at [occurrence]. Records the occurrence exactly once,
     * advances the reminder to its next future occurrence and schedules that alarm.
     */
    suspend fun onAlarmFired(reminderId: String, occurrence: Instant): AlarmOutcome {
        val now = clock.instant()
        var toSchedule: ReminderEntity? = null
        val outcome = db.withTransaction {
            val reminder = dao.get(reminderId) ?: return@withTransaction AlarmOutcome.Ignored
            if (!reminder.enabled || reminder.nextTriggerAt != occurrence) {
                // Stale alarm (edited/disabled since it was set). The current alarm is already correct.
                return@withTransaction AlarmOutcome.Ignored
            }
            val event = ReminderEventEntity(
                id = ids.newId(),
                reminderId = reminder.id,
                occurrenceAt = occurrence,
                firedAt = now,
                missed = Duration.between(occurrence, now) > MISSED_AFTER,
                dismissedAt = null,
            )
            val inserted = eventDao.insertIfAbsent(event) > 0
            val next = ReminderSchedule.nextOccurrenceAfter(
                reminder.startAt, ZoneId.of(reminder.zoneId), reminder.recurrence, maxOf(occurrence, now),
            )
            val advanced = reminder.copy(nextTriggerAt = next, enabled = next != null, updatedAt = now)
            dao.upsert(advanced)
            toSchedule = advanced
            if (inserted) AlarmOutcome.Deliver(advanced, event) else AlarmOutcome.Duplicate
        }
        toSchedule?.let { applyAlarm(it) }
        return outcome
    }

    /**
     * Re-registers every alarm (after reboot, app update, clock/time-zone change, or app start —
     * force-stop and reboot clear alarms). Occurrences that should already have fired are handled
     * as missed (one notification per reminder, not one per skipped repeat).
     */
    suspend fun rescheduleAll(): List<AlarmOutcome.Deliver> {
        val now = clock.instant()
        val delivered = mutableListOf<AlarmOutcome.Deliver>()
        for (reminder in dao.getScheduled()) {
            val due = reminder.nextTriggerAt ?: continue
            if (!due.isAfter(now)) {
                (onAlarmFired(reminder.id, due) as? AlarmOutcome.Deliver)?.let(delivered::add)
            } else {
                scheduler.schedule(reminder, due)
            }
        }
        return delivered
    }

    private fun nextAfterNow(reminder: ReminderEntity): Instant? = ReminderSchedule.nextOccurrenceAfter(
        reminder.startAt, ZoneId.of(reminder.zoneId), reminder.recurrence, clock.instant(),
    )

    private fun applyAlarm(reminder: ReminderEntity) {
        val at = reminder.nextTriggerAt
        if (reminder.enabled && at != null) scheduler.schedule(reminder, at) else scheduler.cancel(reminder)
    }

    companion object {
        const val MAX_DESCRIPTION = 1_000

        /** An occurrence delivered later than this is labelled "missed". */
        val MISSED_AFTER: Duration = Duration.ofMinutes(15)
    }
}
