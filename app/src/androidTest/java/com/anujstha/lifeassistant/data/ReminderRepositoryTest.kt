package com.anujstha.lifeassistant.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anujstha.lifeassistant.data.database.AppDatabase
import com.anujstha.lifeassistant.data.repository.AlarmOutcome
import com.anujstha.lifeassistant.data.repository.ReminderInput
import com.anujstha.lifeassistant.data.repository.ReminderRepository
import com.anujstha.lifeassistant.data.repository.TaskInput
import com.anujstha.lifeassistant.data.repository.TaskRepository
import com.anujstha.lifeassistant.domain.model.RecurrenceFrequency
import com.anujstha.lifeassistant.domain.model.RecurrenceRule
import com.anujstha.lifeassistant.domain.model.ValidationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

/** Reminder scheduling rules with a fake AlarmManager and a controllable clock. */
@RunWith(AndroidJUnit4::class)
class ReminderRepositoryTest {

    // 2026-10-14 06:00Z = 11:45 in Kathmandu.
    private val clock = MutableClock(Instant.parse("2026-10-14T06:00:00Z"))
    private val ids = SequentialIds()
    private val scheduler = FakeScheduler()
    private lateinit var db: AppDatabase
    private lateinit var repo: ReminderRepository

    private val ktm = "Asia/Kathmandu"
    private val today18 = LocalDateTime.of(2026, 10, 14, 18, 0) // = 12:15Z
    private val today18Instant = Instant.parse("2026-10-14T12:15:00Z")

    @Before
    fun setUp() {
        db = inMemoryDatabase(clock, ids)
        repo = ReminderRepository(db, scheduler, clock, ids)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun create(start: LocalDateTime = today18, rule: RecurrenceRule? = null, taskId: String? = null) =
        repo.save(null, ReminderInput("Call mom", startAt = start, zoneId = ktm, recurrence = rule, taskId = taskId))

    private suspend fun reminder(id: String) = repo.observe(id).first()!!

    @Test
    fun save_schedulesAlarmAtTheRightInstant() = runTest {
        val id = create()
        assertEquals(today18Instant, scheduler.scheduled[id])
        assertEquals(today18Instant, reminder(id).nextTriggerAt)
    }

    @Test
    fun pastOneTimeReminder_isRejected() = runTest {
        assertThrows(ValidationException::class.java) {
            runBlocking { create(start = LocalDateTime.of(2026, 10, 14, 9, 0)) }
        }
    }

    @Test
    fun pastStart_withRepeat_schedulesNextFutureOccurrence() = runTest {
        val id = create(start = LocalDateTime.of(2026, 10, 1, 9, 0), rule = RecurrenceRule(RecurrenceFrequency.DAILY))
        assertEquals(Instant.parse("2026-10-15T03:15:00Z"), scheduler.scheduled[id]) // tomorrow 09:00 KTM
    }

    @Test
    fun editing_reschedulesSameReminder_withSameRequestCode() = runTest {
        val id = create()
        val code = reminder(id).requestCode
        repo.save(id, ReminderInput("Call mom", startAt = today18.plusHours(1), zoneId = ktm))
        assertEquals(today18Instant.plusSeconds(3600), scheduler.scheduled[id])
        assertEquals(code, reminder(id).requestCode)
    }

    @Test
    fun disable_cancels_enable_reschedules() = runTest {
        val id = create()
        repo.setEnabled(id, false)
        assertFalse(scheduler.scheduled.containsKey(id))
        assertNull(reminder(id).nextTriggerAt)

        repo.setEnabled(id, true)
        assertEquals(today18Instant, scheduler.scheduled[id])
    }

    @Test
    fun delete_cancelsAlarm() = runTest {
        val id = create()
        repo.delete(id)
        assertFalse(scheduler.scheduled.containsKey(id))
        assertTrue(id in scheduler.cancelled)
    }

    @Test
    fun deletingTask_cancelsItsReminderAlarms() = runTest {
        val tasks = TaskRepository(db, scheduler, clock, ids)
        val taskId = tasks.save(null, TaskInput("Dentist"))
        val a = create(taskId = taskId)
        val b = create(start = today18.plusDays(1), taskId = taskId)

        tasks.delete(taskId)

        assertTrue(scheduler.scheduled.isEmpty())
        assertTrue(scheduler.cancelled.containsAll(listOf(a, b)))
        assertTrue(repo.observeAll().first().isEmpty())
    }

    @Test
    fun alarmFired_recordsEventOnce_duplicateIsSuppressed() = runTest {
        val id = create()
        clock.now = today18Instant

        val first = repo.onAlarmFired(id, today18Instant)
        val second = repo.onAlarmFired(id, today18Instant)

        assertTrue(first is AlarmOutcome.Deliver)
        // The one-time reminder is finished, so the repeat delivery is stale -> nothing shown.
        assertTrue(second !is AlarmOutcome.Deliver)
        assertEquals(1, db.reminderEventDao().getForReminder(id).size)
        assertNull(reminder(id).nextTriggerAt)
        assertFalse(scheduler.scheduled.containsKey(id))
    }

    @Test
    fun alreadyRecordedOccurrence_isDuplicate_notDeliveredAgain_butStillAdvances() = runTest {
        val id = create(rule = RecurrenceRule(RecurrenceFrequency.DAILY))
        // E.g. the reboot worker recorded this occurrence while the alarm was also in flight.
        db.reminderEventDao().insertIfAbsent(
            com.anujstha.lifeassistant.data.entity.ReminderEventEntity(
                "pre", id, today18Instant, today18Instant, missed = false, dismissedAt = null,
            ),
        )
        clock.now = today18Instant

        assertEquals(AlarmOutcome.Duplicate, repo.onAlarmFired(id, today18Instant))
        assertEquals(1, db.reminderEventDao().getForReminder(id).size)
        assertEquals(today18Instant.plus(Duration.ofDays(1)), scheduler.scheduled[id])
    }

    @Test
    fun recurringAlarm_advancesToNextOccurrence() = runTest {
        val id = create(rule = RecurrenceRule(RecurrenceFrequency.DAILY))
        clock.now = today18Instant.plusSeconds(2)

        val outcome = repo.onAlarmFired(id, today18Instant) as AlarmOutcome.Deliver

        assertFalse(outcome.event.missed)
        val tomorrow = today18Instant.plus(Duration.ofDays(1))
        assertEquals(tomorrow, reminder(id).nextTriggerAt)
        assertEquals(tomorrow, scheduler.scheduled[id])
    }

    @Test
    fun staleAlarm_afterEdit_isIgnored() = runTest {
        val id = create()
        repo.save(id, ReminderInput("Call mom", startAt = today18.plusHours(2), zoneId = ktm))
        clock.now = today18Instant
        assertEquals(AlarmOutcome.Ignored, repo.onAlarmFired(id, today18Instant))
        assertTrue(db.reminderEventDao().getForReminder(id).isEmpty())
    }

    @Test
    fun disabledReminder_alarmIsIgnored() = runTest {
        val id = create()
        repo.setEnabled(id, false)
        clock.now = today18Instant
        assertEquals(AlarmOutcome.Ignored, repo.onAlarmFired(id, today18Instant))
    }

    @Test
    fun rescheduleAfterDowntime_marksMissedOnce_andSchedulesFuture() = runTest {
        val id = create(rule = RecurrenceRule(RecurrenceFrequency.DAILY))
        scheduler.scheduled.clear() // simulate reboot: OS alarms gone
        clock.now = today18Instant.plus(Duration.ofDays(3)).plusSeconds(60) // phone off for 3 days

        val missed = repo.rescheduleAll()

        assertEquals(1, missed.size) // one "missed" alert, not one per skipped day
        assertTrue(missed.single().event.missed)
        val nextExpected = today18Instant.plus(Duration.ofDays(4))
        assertEquals(nextExpected, scheduler.scheduled[id])
        assertEquals(1, repo.observeActiveEventCount().first())
    }

    @Test
    fun clockMovedBackward_keepsTheSameOccurrence_noDuplicateOrLoss() = runTest {
        val id = create(rule = RecurrenceRule(RecurrenceFrequency.DAILY))
        scheduler.scheduled.clear()
        clock.now = clock.now.minus(Duration.ofDays(2)) // user sets the clock back two days (TIME_SET)

        assertTrue(repo.rescheduleAll().isEmpty()) // nothing "missed"
        assertEquals(today18Instant, scheduler.scheduled[id]) // still the same, not-yet-delivered occurrence
        assertTrue(db.reminderEventDao().getForReminder(id).isEmpty())
    }

    @Test
    fun displayTimeZoneSetting_neverMovesAReminder() = runTest {
        // Reminders are stored with their own zone; the display preference isn't an input to scheduling.
        val id = create()
        val before = reminder(id)
        repo.rescheduleAll()
        assertEquals(before.nextTriggerAt, reminder(id).nextTriggerAt)
        assertEquals("Asia/Kathmandu", reminder(id).zoneId)
        assertEquals(today18Instant, before.nextTriggerAt)
    }

    @Test
    fun rescheduleAll_reRegistersFutureAlarmsWithoutEvents() = runTest {
        val id = create()
        scheduler.scheduled.clear()
        assertTrue(repo.rescheduleAll().isEmpty())
        assertEquals(today18Instant, scheduler.scheduled[id])
    }

    @Test
    fun dismiss_clearsInbox() = runTest {
        val id = create(rule = RecurrenceRule(RecurrenceFrequency.DAILY))
        clock.now = today18Instant
        repo.onAlarmFired(id, today18Instant)
        assertEquals(1, repo.observeActiveEvents().first().size)
        repo.dismissAllEvents()
        assertEquals(0, repo.observeActiveEventCount().first())
    }
}
