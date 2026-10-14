package com.anujstha.lifeassistant.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.anujstha.lifeassistant.data.entity.ReminderEntity
import com.anujstha.lifeassistant.data.entity.ReminderEventEntity
import com.anujstha.lifeassistant.data.entity.ReminderEventWithReminder
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ReminderDao {

    @Upsert
    suspend fun upsert(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE id = :id")
    fun observe(id: String): Flow<ReminderEntity?>

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("SELECT * FROM reminders ORDER BY nextTriggerAt IS NULL, nextTriggerAt, createdAt")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE taskId = :taskId ORDER BY nextTriggerAt IS NULL, nextTriggerAt")
    fun observeForTask(taskId: String): Flow<List<ReminderEntity>>

    /** Needed before deleting a task, so its alarms can be cancelled before the rows cascade away. */
    @Query("SELECT * FROM reminders WHERE taskId = :taskId")
    suspend fun getForTask(taskId: String): List<ReminderEntity>

    @Query(
        """
        SELECT * FROM reminders
        WHERE enabled = 1 AND nextTriggerAt IS NOT NULL AND nextTriggerAt >= :from
        ORDER BY nextTriggerAt
        LIMIT :limit
        """,
    )
    fun observeUpcoming(from: Instant, limit: Int): Flow<List<ReminderEntity>>

    /** Every reminder that should have an alarm registered (used after reboot / time change). */
    @Query("SELECT * FROM reminders WHERE enabled = 1 AND nextTriggerAt IS NOT NULL")
    suspend fun getScheduled(): List<ReminderEntity>

    @Query("SELECT COALESCE(MAX(requestCode), 0) + 1 FROM reminders")
    suspend fun nextRequestCode(): Int
}

@Dao
interface ReminderEventDao {

    /** Returns -1 if this occurrence was already recorded — the caller must then not notify again. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(event: ReminderEventEntity): Long

    @Transaction
    @Query("SELECT * FROM reminder_events WHERE dismissedAt IS NULL ORDER BY occurrenceAt DESC")
    fun observeActive(): Flow<List<ReminderEventWithReminder>>

    @Query("SELECT COUNT(*) FROM reminder_events WHERE dismissedAt IS NULL")
    fun observeActiveCount(): Flow<Int>

    @Query("UPDATE reminder_events SET dismissedAt = :at WHERE id = :id AND dismissedAt IS NULL")
    suspend fun dismiss(id: String, at: Instant): Int

    @Query("UPDATE reminder_events SET dismissedAt = :at WHERE reminderId = :reminderId AND dismissedAt IS NULL")
    suspend fun dismissAllForReminder(reminderId: String, at: Instant): Int

    @Query("SELECT * FROM reminder_events WHERE reminderId = :reminderId ORDER BY occurrenceAt DESC")
    suspend fun getForReminder(reminderId: String): List<ReminderEventEntity>
}
