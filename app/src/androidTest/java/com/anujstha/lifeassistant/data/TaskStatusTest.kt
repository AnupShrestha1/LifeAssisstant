package com.anujstha.lifeassistant.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anujstha.lifeassistant.data.database.AppDatabase
import com.anujstha.lifeassistant.data.repository.TaskInput
import com.anujstha.lifeassistant.data.repository.TaskRepository
import com.anujstha.lifeassistant.domain.model.RecurrenceFrequency
import com.anujstha.lifeassistant.domain.model.RecurrenceRule
import com.anujstha.lifeassistant.domain.model.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Status changes and recurring-occurrence generation, against a real (in-memory) database. */
@RunWith(AndroidJUnit4::class)
class TaskStatusTest {

    private val clock = MutableClock()
    private val ids = SequentialIds()
    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepository

    private val jan31 = LocalDate.of(2026, 1, 31)

    @Before
    fun setUp() {
        db = inMemoryDatabase(clock, ids)
        repo = TaskRepository(db, FakeScheduler(), clock, ids)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun all() = repo.observeTasks().first()
    private suspend fun open() = all().filter { it.status.isOpen }

    private suspend fun recurring(rule: RecurrenceRule, due: LocalDate = jan31) =
        repo.save(null, TaskInput("Pay bills", dueDate = due, recurrence = rule))

    @Test
    fun complete_reopen_complete_nonRecurring() = runTest {
        val id = repo.save(null, TaskInput("Call bank"))
        assertNull(repo.setStatus(id, TaskStatus.COMPLETED))
        assertEquals(TaskStatus.COMPLETED, db.taskDao().get(id)!!.status)

        repo.setStatus(id, TaskStatus.PENDING)
        val reopened = db.taskDao().get(id)!!
        assertEquals(TaskStatus.PENDING, reopened.status)
        assertNull(reopened.completedAt)
        assertEquals(1, all().size)
    }

    @Test
    fun completingRecurring_createsNextOccurrence_keepsHistory() = runTest {
        val id = recurring(RecurrenceRule(RecurrenceFrequency.MONTHLY))

        val next = repo.setStatus(id, TaskStatus.COMPLETED)!!

        assertEquals(LocalDate.of(2026, 2, 28), next.dueDate)
        assertEquals(TaskStatus.PENDING, next.status)
        assertEquals("Pay bills", next.title)
        assertEquals(TaskStatus.COMPLETED, db.taskDao().get(id)!!.status)
        assertEquals(2, all().size)

        // Completing February must give March 31 (anchored), not March 28 (drift).
        val march = repo.setStatus(next.id, TaskStatus.COMPLETED)!!
        assertEquals(LocalDate.of(2026, 3, 31), march.dueDate)
    }

    @Test
    fun reopenAndCompleteAgain_doesNotDuplicateNextOccurrence() = runTest {
        val id = recurring(RecurrenceRule(RecurrenceFrequency.DAILY))
        repo.setStatus(id, TaskStatus.COMPLETED)
        repo.setStatus(id, TaskStatus.PENDING)
        val again = repo.setStatus(id, TaskStatus.COMPLETED)

        assertNull(again) // already exists, not generated twice
        assertEquals(2, all().size)
    }

    @Test
    fun cancellingRecurring_skipsToNextOccurrence_andIsNotCompleted() = runTest {
        val id = recurring(RecurrenceRule(RecurrenceFrequency.WEEKLY))

        val next = repo.setStatus(id, TaskStatus.CANCELLED)!!

        assertEquals(jan31.plusWeeks(1), next.dueDate)
        val cancelled = db.taskDao().get(id)!!
        assertEquals(TaskStatus.CANCELLED, cancelled.status)
        assertNull(cancelled.completedAt)
        assertEquals(0, db.taskDao().observeCompleted().first().size)
    }

    @Test
    fun endDate_stopsGeneration() = runTest {
        val id = recurring(RecurrenceRule(RecurrenceFrequency.DAILY, endDate = jan31.plusDays(1)))
        val second = repo.setStatus(id, TaskStatus.COMPLETED)!!
        assertNull(repo.setStatus(second.id, TaskStatus.COMPLETED))
        assertEquals(0, open().size)
    }

    @Test
    fun completingViaEditorSave_alsoGeneratesNext() = runTest {
        val rule = RecurrenceRule(RecurrenceFrequency.DAILY)
        val id = recurring(rule)
        repo.save(id, TaskInput("Pay bills", dueDate = jan31, recurrence = rule, status = TaskStatus.COMPLETED))
        assertEquals(listOf(jan31.plusDays(1)), open().map { it.dueDate })
    }

    @Test
    fun changingDueDateOfRecurringTask_startsNewSeries() = runTest {
        val rule = RecurrenceRule(RecurrenceFrequency.MONTHLY)
        val id = recurring(rule)
        val oldSeries = db.taskDao().get(id)!!.seriesId

        repo.save(id, TaskInput("Pay bills", dueDate = LocalDate.of(2026, 2, 10), recurrence = rule))

        val moved = db.taskDao().get(id)!!
        assertNotEquals(oldSeries, moved.seriesId)
        assertEquals(LocalDate.of(2026, 2, 10), moved.seriesStartDate)
        assertEquals(LocalDate.of(2026, 3, 10), repo.setStatus(id, TaskStatus.COMPLETED)!!.dueDate)
    }

    @Test
    fun changingOnlyEndDate_keepsSeries() = runTest {
        val id = recurring(RecurrenceRule(RecurrenceFrequency.DAILY))
        val series = db.taskDao().get(id)!!.seriesId
        repo.save(id, TaskInput("Pay bills", dueDate = jan31, recurrence = RecurrenceRule(RecurrenceFrequency.DAILY, endDate = jan31.plusDays(9))))
        assertEquals(series, db.taskDao().get(id)!!.seriesId)
    }

    @Test
    fun removingRecurrence_detachesFromSeries() = runTest {
        val id = recurring(RecurrenceRule(RecurrenceFrequency.DAILY))
        repo.save(id, TaskInput("Pay bills", dueDate = jan31))
        assertNull(db.taskDao().get(id)!!.seriesId)
        assertNull(repo.setStatus(id, TaskStatus.COMPLETED))
    }
}
