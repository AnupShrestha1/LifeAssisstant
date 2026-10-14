package com.anujstha.lifeassistant.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.anujstha.lifeassistant.data.entity.TaskCategoryEntity
import com.anujstha.lifeassistant.data.entity.TaskEntity
import com.anujstha.lifeassistant.domain.model.Priority
import com.anujstha.lifeassistant.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface TaskCategoryDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: TaskCategoryEntity)

    @Update
    suspend fun update(category: TaskCategoryEntity)

    @Query("SELECT * FROM task_categories WHERE id = :id")
    suspend fun get(id: String): TaskCategoryEntity?

    @Query("SELECT * FROM task_categories WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): TaskCategoryEntity?

    @Query("SELECT * FROM task_categories WHERE archivedAt IS NULL ORDER BY name")
    fun observeActive(): Flow<List<TaskCategoryEntity>>

    @Query("SELECT * FROM task_categories ORDER BY name")
    fun observeAll(): Flow<List<TaskCategoryEntity>>

    @Query("UPDATE task_categories SET archivedAt = :archivedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: String, archivedAt: Instant?, updatedAt: Instant): Int

    /** Tasks in the category keep existing with no category (FK is SET NULL). */
    @Query("DELETE FROM task_categories WHERE id = :id")
    suspend fun delete(id: String): Int
}

@Dao
interface TaskDao {

    @Upsert
    suspend fun upsert(task: TaskEntity)

    /**
     * Inserts unless a row with the same id or the same (seriesId, occurrenceDate) exists.
     * Returns -1 when ignored — used to generate each recurring occurrence at most once.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(task: TaskEntity): Long

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: String): Flow<TaskEntity?>

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String): Int

    /**
     * Filtered tasks. [statuses] must be non-empty; other null filters are ignored.
     * [query] must already be LIKE-escaped. Sorting options are applied by the repository.
     */
    @Query(
        """
        SELECT * FROM tasks
        WHERE status IN (:statuses)
          AND (:priority IS NULL OR priority = :priority)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:dueFrom IS NULL OR dueDate >= :dueFrom)
          AND (:dueTo IS NULL OR dueDate <= :dueTo)
          AND (:query IS NULL OR title LIKE '%' || :query || '%' ESCAPE '\'
                              OR description LIKE '%' || :query || '%' ESCAPE '\')
        ORDER BY createdAt DESC
        """,
    )
    fun observeFiltered(
        statuses: List<TaskStatus>,
        priority: Priority?,
        categoryId: String?,
        dueFrom: LocalDate?,
        dueTo: LocalDate?,
        query: String?,
    ): Flow<List<TaskEntity>>

    /** All tasks (any status) due on [date]. */
    @Query("SELECT * FROM tasks WHERE dueDate = :date ORDER BY dueTime IS NULL, dueTime, createdAt")
    fun observeDueOn(date: LocalDate): Flow<List<TaskEntity>>

    /** Open tasks whose due date is before [today]. Same-day time-based overdue is decided in domain logic. */
    @Query(
        """
        SELECT * FROM tasks
        WHERE status IN ('PENDING', 'IN_PROGRESS') AND dueDate < :today
        ORDER BY dueDate, dueTime IS NULL, dueTime
        """,
    )
    fun observeOverdue(today: LocalDate): Flow<List<TaskEntity>>

    /** Open tasks due after [today], soonest first. */
    @Query(
        """
        SELECT * FROM tasks
        WHERE status IN ('PENDING', 'IN_PROGRESS') AND dueDate > :today
        ORDER BY dueDate, dueTime IS NULL, dueTime
        LIMIT :limit
        """,
    )
    fun observeUpcoming(today: LocalDate, limit: Int): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = 'COMPLETED' ORDER BY completedAt DESC")
    fun observeCompleted(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE seriesId = :seriesId ORDER BY occurrenceDate")
    suspend fun getSeries(seriesId: String): List<TaskEntity>
}
