package com.anujstha.lifeassistant.data.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.anujstha.lifeassistant.domain.model.RecurrenceRule
import java.time.Instant
import java.time.LocalDateTime

/**
 * A reminder. The schedule is defined in wall-clock terms ([startAt] in [zoneId]) so that a
 * daily 08:00 reminder stays at 08:00 across DST changes. [nextTriggerAt] is the UTC instant of
 * the next occurrence to deliver (null once a non-repeating or ended reminder has fired).
 */
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            // Deleting a task deletes its reminders; the scheduler cancels their alarms.
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["requestCode"], unique = true),
        Index("taskId"),
        Index(value = ["enabled", "nextTriggerAt"]),
    ],
)
data class ReminderEntity(
    @PrimaryKey val id: String,
    /** Stable PendingIntent request code, so edits and cancels target the same alarm. */
    val requestCode: Int,
    val title: String,
    val description: String?,
    val taskId: String?,
    val startAt: LocalDateTime,
    val zoneId: String,
    @Embedded(prefix = "recurrence_") val recurrence: RecurrenceRule?,
    val nextTriggerAt: Instant?,
    val enabled: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * One delivered (or missed) occurrence of a reminder. The unique (reminderId, occurrenceAt)
 * index is the duplicate-notification guard: an occurrence can only be recorded once.
 * These rows also back the in-app reminder list when notification permission is denied.
 */
@Entity(
    tableName = "reminder_events",
    foreignKeys = [
        ForeignKey(
            entity = ReminderEntity::class,
            parentColumns = ["id"],
            childColumns = ["reminderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["reminderId", "occurrenceAt"], unique = true), Index("firedAt")],
)
data class ReminderEventEntity(
    @PrimaryKey val id: String,
    val reminderId: String,
    val occurrenceAt: Instant,
    val firedAt: Instant,
    /** True if the occurrence was detected late (device off, alarm deferred) rather than on time. */
    val missed: Boolean,
    val dismissedAt: Instant?,
)

data class ReminderEventWithReminder(
    @Embedded val event: ReminderEventEntity,
    @Relation(parentColumn = "reminderId", entityColumn = "id")
    val reminder: ReminderEntity,
)
