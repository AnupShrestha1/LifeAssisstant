package com.anujstha.lifeassistant.domain.usecase

import com.anujstha.lifeassistant.data.entity.TaskEntity
import com.anujstha.lifeassistant.domain.model.Priority
import com.anujstha.lifeassistant.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TaskRulesTest {

    private val now = LocalDateTime.of(2026, 10, 14, 15, 30)
    private val today = now.toLocalDate()

    private var seq = 0L
    private fun task(
        title: String = "t",
        status: TaskStatus = TaskStatus.PENDING,
        priority: Priority = Priority.MEDIUM,
        due: LocalDate? = null,
        time: LocalTime? = null,
        completedAt: Instant? = null,
    ) = TaskEntity(
        id = title, title = title, description = null, status = status, priority = priority,
        dueDate = due, dueTime = time, categoryId = null, completedAt = completedAt,
        seriesId = null, seriesStartDate = null, occurrenceDate = null, recurrence = null,
        createdAt = Instant.ofEpochSecond(seq++), updatedAt = Instant.EPOCH,
    )

    @Test
    fun overdue_pastDateIsOverdue_futureIsNot() {
        assertTrue(TaskRules.isOverdue(task(due = today.minusDays(1)), now))
        assertFalse(TaskRules.isOverdue(task(due = today.plusDays(1)), now))
        assertFalse(TaskRules.isOverdue(task(), now))
    }

    @Test
    fun overdue_today_dependsOnTime() {
        assertFalse(TaskRules.isOverdue(task(due = today), now)) // no time: due by end of day
        assertTrue(TaskRules.isOverdue(task(due = today, time = LocalTime.of(15, 0)), now))
        assertFalse(TaskRules.isOverdue(task(due = today, time = LocalTime.of(16, 0)), now))
    }

    @Test
    fun overdue_neverForClosedTasks_butInProgressCounts() {
        val yesterday = today.minusDays(1)
        assertFalse(TaskRules.isOverdue(task(status = TaskStatus.COMPLETED, due = yesterday), now))
        assertFalse(TaskRules.isOverdue(task(status = TaskStatus.CANCELLED, due = yesterday), now))
        assertTrue(TaskRules.isOverdue(task(status = TaskStatus.IN_PROGRESS, due = yesterday), now))
    }

    @Test
    fun views_selectTheRightTasks() {
        val dueToday = task("today", due = today)
        val doneToday = task("doneToday", status = TaskStatus.COMPLETED, due = today)
        val cancelledToday = task("cancelledToday", status = TaskStatus.CANCELLED, due = today)
        val tomorrow = task("tomorrow", due = today.plusDays(1))
        val late = task("late", due = today.minusDays(2))
        val noDate = task("noDate")
        val all = listOf(dueToday, doneToday, cancelledToday, tomorrow, late, noDate)

        fun ids(view: TaskView) = all.filter { TaskRules.matchesView(it, view, now) }.map { it.id }.toSet()
        assertEquals(setOf("today", "doneToday"), ids(TaskView.TODAY))
        assertEquals(setOf("tomorrow"), ids(TaskView.UPCOMING))
        assertEquals(setOf("late"), ids(TaskView.OVERDUE))
        assertEquals(setOf("doneToday"), ids(TaskView.COMPLETED))
        assertEquals(all.map { it.id }.toSet(), ids(TaskView.ALL))
    }

    @Test
    fun sortByPriority_highestFirst_openBeforeClosed() {
        val low = task("low", priority = Priority.LOW)
        val urgent = task("urgent", priority = Priority.URGENT)
        val urgentDone = task("urgentDone", priority = Priority.URGENT, status = TaskStatus.COMPLETED)
        val high = task("high", priority = Priority.HIGH)
        val sorted = listOf(low, urgentDone, high, urgent).sortedWith(TaskRules.comparator(TaskSort.PRIORITY, TaskView.ALL))
        assertEquals(listOf("urgent", "high", "low", "urgentDone"), sorted.map { it.id })
    }

    @Test
    fun sortByDueDate_earliestFirst_undatedLast_timeBreaksTies() {
        val a = task("noDate")
        val b = task("todayLate", due = today, time = LocalTime.of(18, 0))
        val c = task("todayEarly", due = today, time = LocalTime.of(9, 0))
        val d = task("yesterday", due = today.minusDays(1))
        val sorted = listOf(a, b, c, d).sortedWith(TaskRules.comparator(TaskSort.DUE_DATE, TaskView.ALL))
        assertEquals(listOf("yesterday", "todayEarly", "todayLate", "noDate"), sorted.map { it.id })
    }

    @Test
    fun sortByCreated_newestFirst() {
        val first = task("first")
        val second = task("second")
        val sorted = listOf(first, second).sortedWith(TaskRules.comparator(TaskSort.CREATED, TaskView.ALL))
        assertEquals(listOf("second", "first"), sorted.map { it.id })
    }

    @Test
    fun completedView_sortsByCompletionTime_newestFirst() {
        val earlier = task("earlier", status = TaskStatus.COMPLETED, completedAt = Instant.ofEpochSecond(100))
        val later = task("later", status = TaskStatus.COMPLETED, completedAt = Instant.ofEpochSecond(200))
        val sorted = listOf(earlier, later).sortedWith(TaskRules.comparator(TaskSort.DUE_DATE, TaskView.COMPLETED))
        assertEquals(listOf("later", "earlier"), sorted.map { it.id })
    }
}
