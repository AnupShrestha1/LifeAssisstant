package com.anujstha.lifeassistant.data.repository

import androidx.room.withTransaction
import com.anujstha.lifeassistant.data.database.AppDatabase
import com.anujstha.lifeassistant.data.entity.TaskCategoryEntity
import com.anujstha.lifeassistant.data.entity.TaskEntity
import com.anujstha.lifeassistant.domain.model.Priority
import com.anujstha.lifeassistant.domain.model.RecurrenceRule
import com.anujstha.lifeassistant.domain.model.TaskStatus
import com.anujstha.lifeassistant.domain.model.validate
import com.anujstha.lifeassistant.domain.usecase.Recurrence
import com.anujstha.lifeassistant.notifications.ReminderScheduler
import com.anujstha.lifeassistant.util.IdGenerator
import com.anujstha.lifeassistant.util.escapeLike
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

data class TaskInput(
    val title: String,
    val description: String? = null,
    val status: TaskStatus = TaskStatus.PENDING,
    val priority: Priority = Priority.MEDIUM,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val categoryId: String? = null,
    val recurrence: RecurrenceRule? = null,
)

/**
 * Task persistence, validation and status rules.
 *
 * Recurring tasks: each occurrence is a row. When an open occurrence is completed **or cancelled**
 * (cancel = skip this one), the next occurrence is generated — once, guarded by the unique
 * (seriesId, occurrenceDate) index. Reopening a closed occurrence never deletes or duplicates the
 * next one. Changing an occurrence's due date or repeat pattern starts a new series from it, so
 * earlier occurrences stay untouched as history.
 */
class TaskRepository(
    private val db: AppDatabase,
    private val reminderScheduler: ReminderScheduler,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    private val dao = db.taskDao()
    private val categoryDao = db.taskCategoryDao()

    fun observe(id: String): Flow<TaskEntity?> = dao.observe(id)

    /** All tasks matching the search / filters, any status. Views and sorting are applied by callers. */
    fun observeTasks(query: String? = null, priority: Priority? = null, categoryId: String? = null): Flow<List<TaskEntity>> =
        dao.observeFiltered(
            statuses = TaskStatus.entries,
            priority = priority,
            categoryId = categoryId,
            dueFrom = null,
            dueTo = null,
            query = query?.trim()?.takeIf { it.isNotEmpty() }?.let(::escapeLike),
        )

    fun observeCategories(): Flow<List<TaskCategoryEntity>> = categoryDao.observeActive()

    fun observeAllCategories(): Flow<List<TaskCategoryEntity>> = categoryDao.observeAll()

    /**
     * Creates ([id] == null) or updates a task, keeping createdAt on update. completedAt is set
     * when the task becomes COMPLETED and cleared when it is reopened. Returns the task id.
     */
    suspend fun save(id: String?, input: TaskInput): String = db.withTransaction {
        val title = input.title.trim()
        validate(title.isNotEmpty()) { "Title can't be empty" }
        validate(title.length <= MAX_TITLE) { "Title is too long (max $MAX_TITLE characters)" }
        val description = input.description?.trim()?.takeIf { it.isNotEmpty() }
        validate((description?.length ?: 0) <= MAX_DESCRIPTION) { "Description is too long" }
        validate(input.dueTime == null || input.dueDate != null) { "Set a due date before a due time" }
        validate(input.recurrence == null || input.dueDate != null) { "Recurring tasks need a due date" }
        val repeatEnd = input.recurrence?.endDate
        val due = input.dueDate
        validate(repeatEnd == null || due == null || !repeatEnd.isBefore(due)) {
            "Repeat end date is before the due date"
        }
        if (input.categoryId != null) {
            validate(categoryDao.get(input.categoryId) != null) { "Category not found" }
        }

        val existing = id?.let { dao.get(it) ?: throw NoSuchElementException("Task not found") }
        val now = clock.instant()
        val series = seriesFieldsFor(existing, input)
        val entity = TaskEntity(
            id = existing?.id ?: ids.newId(),
            title = title,
            description = description,
            status = input.status,
            priority = input.priority,
            dueDate = input.dueDate,
            dueTime = input.dueTime,
            categoryId = input.categoryId,
            completedAt = completedAtFor(existing, input.status),
            seriesId = series.seriesId,
            seriesStartDate = series.startDate,
            occurrenceDate = series.occurrenceDate,
            recurrence = input.recurrence,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        dao.upsert(entity)
        if (existing != null && existing.status.isOpen && !entity.status.isOpen) generateNextOccurrence(entity)
        entity.id
    }

    /**
     * Changes only the status (list checkbox, "mark in progress", reopen...). Returns the next
     * occurrence if closing a recurring task generated one, otherwise null.
     */
    suspend fun setStatus(id: String, status: TaskStatus): TaskEntity? = db.withTransaction {
        val task = dao.get(id) ?: throw NoSuchElementException("Task not found")
        if (task.status == status) return@withTransaction null
        val updated = task.copy(
            status = status,
            completedAt = completedAtFor(task, status),
            updatedAt = clock.instant(),
        )
        dao.upsert(updated)
        if (task.status.isOpen && !status.isOpen) generateNextOccurrence(updated) else null
    }

    /** Deletes this task (one occurrence of a series) and its reminders, cancelling their alarms first. */
    suspend fun delete(id: String): Boolean {
        db.reminderDao().getForTask(id).forEach(reminderScheduler::cancel)
        return dao.delete(id) > 0
    }

    suspend fun createCategory(name: String): TaskCategoryEntity = db.withTransaction {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        validate(clean.isNotEmpty()) { "Category name can't be empty" }
        validate(clean.length <= ExpenseCategoryRepository.MAX_NAME) { "Category name is too long" }
        val now = clock.instant()
        val existing = categoryDao.findByName(clean)
        if (existing != null) {
            validate(existing.archivedAt != null) { "A category named \"$clean\" already exists" }
            categoryDao.setArchived(existing.id, archivedAt = null, updatedAt = now)
            return@withTransaction existing.copy(archivedAt = null, updatedAt = now)
        }
        TaskCategoryEntity(id = ids.newId(), name = clean, archivedAt = null, createdAt = now, updatedAt = now)
            .also { categoryDao.insert(it) }
    }

    /** Tasks in the category are kept and become uncategorized. */
    suspend fun deleteCategory(id: String): Boolean = categoryDao.delete(id) > 0

    private fun completedAtFor(existing: TaskEntity?, newStatus: TaskStatus) = when {
        newStatus != TaskStatus.COMPLETED -> null
        existing?.status == TaskStatus.COMPLETED -> existing.completedAt
        else -> clock.instant()
    }

    private data class SeriesFields(val seriesId: String?, val startDate: LocalDate?, val occurrenceDate: LocalDate?)

    private fun seriesFieldsFor(existing: TaskEntity?, input: TaskInput): SeriesFields {
        val rule = input.recurrence ?: return SeriesFields(null, null, null)
        val keepSeries = existing?.seriesId != null &&
            existing.dueDate == input.dueDate &&
            existing.recurrence?.frequency == rule.frequency &&
            existing.recurrence.interval == rule.interval
        return if (keepSeries) {
            SeriesFields(existing!!.seriesId, existing.seriesStartDate, existing.occurrenceDate)
        } else {
            // New series anchored at this task's due date (only end-date edits keep the old series).
            SeriesFields(ids.newId(), input.dueDate, input.dueDate)
        }
    }

    /** Inserts the occurrence after [closed]; returns it, or null if the series ended or it already exists. */
    private suspend fun generateNextOccurrence(closed: TaskEntity): TaskEntity? {
        val rule = closed.recurrence ?: return null
        val seriesId = closed.seriesId ?: return null
        val occurrence = closed.occurrenceDate ?: return null
        val start = closed.seriesStartDate ?: occurrence
        val nextDate = Recurrence.nextAfter(start, rule, occurrence) ?: return null
        val now = clock.instant()
        val next = closed.copy(
            id = ids.newId(),
            status = TaskStatus.PENDING,
            completedAt = null,
            dueDate = nextDate,
            seriesId = seriesId,
            seriesStartDate = start,
            occurrenceDate = nextDate,
            createdAt = now,
            updatedAt = now,
        )
        return if (dao.insertIfAbsent(next) > 0) next else null
    }

    companion object {
        const val MAX_TITLE = 200
        const val MAX_DESCRIPTION = 5_000
    }
}
