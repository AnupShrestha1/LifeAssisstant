package com.anujstha.lifeassistant.domain.usecase

import com.anujstha.lifeassistant.data.entity.TaskEntity
import com.anujstha.lifeassistant.domain.model.TaskStatus
import java.time.LocalDate
import java.time.LocalDateTime

/** Which slice of tasks a list shows. */
enum class TaskView { TODAY, UPCOMING, OVERDUE, ALL, COMPLETED }

enum class TaskSort { DUE_DATE, PRIORITY, CREATED }

object TaskRules {

    /**
     * Overdue = still open (pending / in progress) and past due. A task due today with no time is
     * not overdue until the day ends; a task due today at a time is overdue once that time passes.
     * Completed and cancelled tasks are never overdue.
     */
    fun isOverdue(task: TaskEntity, now: LocalDateTime): Boolean {
        if (!task.status.isOpen) return false
        val due = task.dueDate ?: return false
        val today = now.toLocalDate()
        return when {
            due.isBefore(today) -> true
            due.isAfter(today) -> false
            else -> task.dueTime?.let { now.toLocalTime().isAfter(it) } ?: false
        }
    }

    fun matchesView(task: TaskEntity, view: TaskView, now: LocalDateTime): Boolean {
        val today: LocalDate = now.toLocalDate()
        return when (view) {
            // Everything due today, including ones already finished, so the day's progress is visible.
            TaskView.TODAY -> task.dueDate == today && task.status != TaskStatus.CANCELLED
            TaskView.UPCOMING -> task.status.isOpen && task.dueDate?.isAfter(today) == true
            TaskView.OVERDUE -> isOverdue(task, now)
            TaskView.ALL -> true
            TaskView.COMPLETED -> task.status == TaskStatus.COMPLETED
        }
    }

    /**
     * Sort order. Within every option, open tasks come before closed ones, and ties fall back to
     * due date then creation time so the order is stable.
     */
    fun comparator(sort: TaskSort, view: TaskView): Comparator<TaskEntity> {
        if (view == TaskView.COMPLETED) {
            return compareByDescending<TaskEntity> { it.completedAt }.thenByDescending { it.createdAt }
        }
        val byDue = compareBy<TaskEntity, LocalDate?>(nullsLast()) { it.dueDate }
            .thenBy(nullsLast()) { it.dueTime }
        val primary: Comparator<TaskEntity> = when (sort) {
            TaskSort.DUE_DATE -> byDue.thenByDescending { it.priority }
            TaskSort.PRIORITY -> compareByDescending<TaskEntity> { it.priority }.then(byDue)
            TaskSort.CREATED -> compareByDescending { it.createdAt }
        }
        return compareBy<TaskEntity> { !it.status.isOpen }.then(primary).thenByDescending { it.createdAt }
    }
}
