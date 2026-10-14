package com.anujstha.lifeassistant.data.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.anujstha.lifeassistant.domain.model.Mood
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "journal_entries",
    indices = [Index("entryDate")],
)
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    /** The day the entry is about (date-only, independent of time zone). */
    val entryDate: LocalDate,
    val mood: Mood?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Entity(
    tableName = "journal_tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class JournalTagEntity(
    @PrimaryKey val id: String,
    /** Unique ignoring case, so "Work" and "work" are the same tag. */
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val createdAt: Instant,
)

/** Many-to-many link. Deleting an entry or a tag removes only the link rows. */
@Entity(
    tableName = "journal_entry_tags",
    primaryKeys = ["entryId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = JournalEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = JournalTagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")],
)
data class JournalEntryTagCrossRef(
    val entryId: String,
    val tagId: String,
)

data class JournalEntryWithTags(
    @Embedded val entry: JournalEntryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = JournalEntryTagCrossRef::class,
            parentColumn = "entryId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<JournalTagEntity>,
)
