package com.anujstha.lifeassistant.data.repository

import androidx.room.withTransaction
import com.anujstha.lifeassistant.data.database.AppDatabase
import com.anujstha.lifeassistant.data.entity.TaskCategoryEntity
import com.anujstha.lifeassistant.data.entity.TaskEntity
import com.anujstha.lifeassistant.domain.model.Priority
import com.anujstha.lifeassistant.domain.model.RecurrenceRule
import com.anujstha.lifeassistant.domain.model.TaskStatus
import com.anujstha.lifeassistant.domain.model.validate
import com.anujstha.lifeassistant.util.IdGenerator
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
 * Task persistence and validation. Status transitions and recurring-occurrence generation
 * are added with the task manager (milestone 4).
 */
class TaskRepository(
    private val db: AppDatabase,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    private val dao = db.taskDao()
    private val categoryDao = db.taskCategoryDao()

    fun observe(id: String): Flow<TaskEntity?> = dao.observe(id)

    fun observeCategories(): Flow<List<TaskCategoryEntity>> = categoryDao.observeActive()

    /**
     * Creates ([id] == null) or updates a task, keeping createdAt on update.
     * completedAt is set when the task becomes COMPLETED and cleared when it is reopened.
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
        val completedAt = when {
            input.status != TaskStatus.COMPLETED -> null
            existing?.status == TaskStatus.COMPLETED -> existing.completedAt
            else -> now
        }
        // A recurring task starts a series; its first occurrence is its own due date.
        val seriesId = existing?.seriesId ?: if (input.recurrence != null) ids.newId() else null
        val entity = TaskEntity(
            id = existing?.id ?: ids.newId(),
            title = title,
            description = description,
            status = input.status,
            priority = input.priority,
            dueDate = input.dueDate,
            dueTime = input.dueTime,
            categoryId = input.categoryId,
            completedAt = completedAt,
            seriesId = seriesId,
            occurrenceDate = if (seriesId != null) existing?.occurrenceDate ?: input.dueDate else null,
            recurrence = input.recurrence,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        dao.upsert(entity)
        entity.id
    }

    /** Deletes the task and (by cascade) its reminders. Alarm cancellation is wired in milestone 5. */
    suspend fun delete(id: String): Boolean = dao.delete(id) > 0

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

    companion object {
        const val MAX_TITLE = 200
        const val MAX_DESCRIPTION = 5_000
    }
}
