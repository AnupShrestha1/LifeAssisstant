package com.anujstha.lifeassistant.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anujstha.lifeassistant.data.database.AppDatabase
import com.anujstha.lifeassistant.data.entity.ReminderEntity
import com.anujstha.lifeassistant.data.entity.ReminderEventEntity
import com.anujstha.lifeassistant.data.repository.TaskInput
import com.anujstha.lifeassistant.data.repository.TaskRepository
import com.anujstha.lifeassistant.domain.model.Priority
import com.anujstha.lifeassistant.domain.model.RecurrenceFrequency
import com.anujstha.lifeassistant.domain.model.RecurrenceRule
import com.anujstha.lifeassistant.domain.model.TaskStatus
import com.anujstha.lifeassistant.domain.model.ValidationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class TaskAndReminderDataTest {

    private val clock = MutableClock()
    private val ids = SequentialIds()
    private lateinit var db: AppDatabase
    private lateinit var tasks: TaskRepository

    private val today = LocalDate.of(2026, 10, 14)

    @Before
    fun setUp() {
        db = inMemoryDatabase(clock, ids)
        tasks = TaskRepository(db, FakeScheduler(), clock, ids)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun task(title: String, due: LocalDate? = null, status: TaskStatus = TaskStatus.PENDING) =
        tasks.save(null, TaskInput(title = title, dueDate = due, status = status))

    // ---- Tasks ----

    @Test
    fun save_setsAndClearsCompletedAt_andKeepsCreatedAt() = runTest {
        val id = task("Pay rent", due = today)
        val created = db.taskDao().get(id)!!.createdAt
        assertNull(db.taskDao().get(id)!!.completedAt)

        clock.advance(Duration.ofHours(1))
        tasks.save(id, TaskInput("Pay rent", dueDate = today, status = TaskStatus.COMPLETED))
        val completed = db.taskDao().get(id)!!
        assertEquals(clock.now, completed.completedAt)
        assertEquals(created, completed.createdAt)

        tasks.save(id, TaskInput("Pay rent", dueDate = today, status = TaskStatus.PENDING))
        assertNull(db.taskDao().get(id)!!.completedAt)
    }

    @Test
    fun cancelledTask_isNotCompleted() = runTest {
        val id = task("Old plan", status = TaskStatus.CANCELLED)
        assertNull(db.taskDao().get(id)!!.completedAt)
        assertTrue(db.taskDao().observeCompleted().first().isEmpty())
    }

    @Test
    fun overdue_includesOnlyOpenTasksDueBeforeToday() = runTest {
        task("Late pending", due = today.minusDays(2))
        task("Late in progress", due = today.minusDays(1), status = TaskStatus.IN_PROGRESS)
        task("Late but done", due = today.minusDays(1), status = TaskStatus.COMPLETED)
        task("Late but cancelled", due = today.minusDays(1), status = TaskStatus.CANCELLED)
        task("Due today", due = today)
        task("No due date")

        val overdue = db.taskDao().observeOverdue(today).first().map { it.title }
        assertEquals(listOf("Late pending", "Late in progress"), overdue)
    }

    @Test
    fun dueToday_andUpcoming() = runTest {
        task("Today", due = today)
        task("Tomorrow", due = today.plusDays(1))
        task("Next week", due = today.plusDays(7))

        assertEquals(listOf("Today"), db.taskDao().observeDueOn(today).first().map { it.title })
        assertEquals(listOf("Tomorrow", "Next week"), db.taskDao().observeUpcoming(today, 10).first().map { it.title })
    }

    @Test
    fun filtered_byStatusPriorityAndSearch() = runTest {
        tasks.save(null, TaskInput("Buy milk", priority = Priority.LOW))
        tasks.save(null, TaskInput("File taxes", priority = Priority.URGENT))
        tasks.save(null, TaskInput("Done thing", status = TaskStatus.COMPLETED))

        val open = listOf(TaskStatus.PENDING, TaskStatus.IN_PROGRESS)
        val dao = db.taskDao()
        assertEquals(2, dao.observeFiltered(open, null, null, null, null, null).first().size)
        assertEquals(listOf("File taxes"), dao.observeFiltered(open, Priority.URGENT, null, null, null, null).first().map { it.title })
        assertEquals(listOf("Buy milk"), dao.observeFiltered(open, null, null, null, null, "milk").first().map { it.title })
    }

    @Test
    fun recurringOccurrence_cannotBeInsertedTwice() = runTest {
        val id = tasks.save(
            null,
            TaskInput("Water plants", dueDate = today, recurrence = RecurrenceRule(RecurrenceFrequency.DAILY)),
        )
        val first = db.taskDao().get(id)!!
        assertNotNull(first.seriesId)
        assertEquals(today, first.occurrenceDate)

        val next = first.copy(id = "occurrence-2", dueDate = today.plusDays(1), occurrenceDate = today.plusDays(1), status = TaskStatus.PENDING)
        assertTrue(db.taskDao().insertIfAbsent(next) > 0)
        // Same series + same occurrence date under a different id: ignored.
        assertEquals(-1L, db.taskDao().insertIfAbsent(next.copy(id = "occurrence-2-dup")))
        assertEquals(2, db.taskDao().getSeries(first.seriesId!!).size)
    }

    @Test
    fun renameTaskCategory_rejectsDuplicates() = runTest {
        val a = tasks.createCategory("Garden")
        tasks.renameCategory(a.id, "Yard")
        assertEquals("Yard", db.taskCategoryDao().get(a.id)!!.name)
        assertThrows(ValidationException::class.java) { runBlocking { tasks.renameCategory(a.id, "work") } } // default "Work"
    }

    @Test
    fun deletingTaskCategory_keepsTasksUncategorized() = runTest {
        val category = tasks.createCategory("Garden")
        val id = tasks.save(null, TaskInput("Mow", categoryId = category.id))

        tasks.deleteCategory(category.id)

        assertNull(db.taskDao().get(id)!!.categoryId)
    }

    @Test
    fun invalidTasks_areRejected() = runTest {
        assertThrows(ValidationException::class.java) { runBlocking { tasks.save(null, TaskInput("  ")) } }
        assertThrows(ValidationException::class.java) {
            runBlocking { tasks.save(null, TaskInput("T", dueTime = java.time.LocalTime.NOON)) }
        }
        assertThrows(ValidationException::class.java) {
            runBlocking { tasks.save(null, TaskInput("T", recurrence = RecurrenceRule(RecurrenceFrequency.WEEKLY))) }
        }
        assertThrows(ValidationException::class.java) {
            runBlocking {
                tasks.save(
                    null,
                    TaskInput("T", dueDate = today, recurrence = RecurrenceRule(RecurrenceFrequency.DAILY, endDate = today.minusDays(1))),
                )
            }
        }
    }

    // ---- Reminders ----

    private suspend fun reminder(id: String, taskId: String? = null, next: Instant? = clock.now.plusSeconds(3600), enabled: Boolean = true) =
        ReminderEntity(
            id = id,
            requestCode = db.reminderDao().nextRequestCode(),
            title = "Reminder $id",
            description = null,
            taskId = taskId,
            startAt = LocalDateTime.of(2026, 10, 14, 12, 0),
            zoneId = "Asia/Kathmandu",
            recurrence = null,
            nextTriggerAt = next,
            enabled = enabled,
            createdAt = clock.now,
            updatedAt = clock.now,
        ).also { db.reminderDao().upsert(it) }

    @Test
    fun requestCodes_areUniqueAndIncreasing() = runTest {
        val a = reminder("r1")
        val b = reminder("r2")
        assertEquals(1, a.requestCode)
        assertEquals(2, b.requestCode)
    }

    @Test
    fun deletingTask_deletesItsReminders_only() = runTest {
        val taskId = task("Dentist", due = today)
        reminder("r1", taskId = taskId)
        reminder("r2", taskId = taskId)
        reminder("standalone")

        tasks.delete(taskId)

        assertEquals(listOf("standalone"), db.reminderDao().observeAll().first().map { it.id })
    }

    @Test
    fun reminderEvent_sameOccurrenceIsRecordedOnce() = runTest {
        val r = reminder("r1")
        val event = ReminderEventEntity("e1", r.id, occurrenceAt = r.nextTriggerAt!!, firedAt = clock.now, missed = false, dismissedAt = null)

        assertTrue(db.reminderEventDao().insertIfAbsent(event) > 0)
        assertEquals(-1L, db.reminderEventDao().insertIfAbsent(event.copy(id = "e2")))
        assertEquals(1, db.reminderEventDao().observeActiveCount().first())

        db.reminderEventDao().dismiss("e1", clock.now)
        assertEquals(0, db.reminderEventDao().observeActiveCount().first())
    }

    @Test
    fun upcomingAndScheduled_excludeDisabledAndFinished() = runTest {
        reminder("soon", next = clock.now.plusSeconds(60))
        reminder("later", next = clock.now.plusSeconds(7200))
        reminder("disabled", enabled = false)
        reminder("finished", next = null)
        reminder("past", next = clock.now.minusSeconds(60))

        assertEquals(listOf("soon", "later"), db.reminderDao().observeUpcoming(clock.now, 10).first().map { it.id })
        assertEquals(setOf("soon", "later", "past"), db.reminderDao().getScheduled().map { it.id }.toSet())
    }
}
