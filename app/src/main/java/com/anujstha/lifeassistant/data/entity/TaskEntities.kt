package com.anujstha.lifeassistant.data.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.anujstha.lifeassistant.domain.model.Priority
import com.anujstha.lifeassistant.domain.model.RecurrenceRule
import com.anujstha.lifeassistant.domain.model.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Entity(
    tableName = "task_categories",
    indices = [Index(value = ["name"], unique = true)],
)
data class TaskCategoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val archivedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * A task. Recurring tasks form a series: every occurrence is its own row sharing [seriesId],
 * with [occurrenceDate] identifying which occurrence it is. The unique (seriesId, occurrenceDate)
 * index guarantees an occurrence is never generated twice; completed rows remain as history.
 */
@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("status"),
        Index("dueDate"),
        Index("categoryId"),
        Index(value = ["seriesId", "occurrenceDate"], unique = true),
    ],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String?,
    val status: TaskStatus,
    val priority: Priority,
    val dueDate: LocalDate?,
    val dueTime: LocalTime?,
    val categoryId: String?,
    val completedAt: Instant?,
    val seriesId: String?,
    val occurrenceDate: LocalDate?,
    @Embedded(prefix = "recurrence_") val recurrence: RecurrenceRule?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
