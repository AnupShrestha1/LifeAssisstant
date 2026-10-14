package com.anujstha.lifeassistant.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.anujstha.lifeassistant.data.entity.JournalEntryEntity
import com.anujstha.lifeassistant.data.entity.JournalEntryTagCrossRef
import com.anujstha.lifeassistant.data.entity.JournalEntryWithTags
import com.anujstha.lifeassistant.data.entity.JournalTagEntity
import com.anujstha.lifeassistant.domain.model.Mood
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface JournalDao {

    // ---- Entries ----

    @Upsert
    suspend fun upsertEntry(entry: JournalEntryEntity)

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    suspend fun getEntry(id: String): JournalEntryEntity?

    @Transaction
    @Query("SELECT * FROM journal_entries WHERE id = :id")
    fun observeEntry(id: String): Flow<JournalEntryWithTags?>

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun deleteEntry(id: String): Int

    /**
     * Entries newest first, with optional filters (null = no filter). [query] must already be
     * LIKE-escaped (see `escapeLike`) and is matched against title and body.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM journal_entries
        WHERE (:query IS NULL OR title LIKE '%' || :query || '%' ESCAPE '\'
                              OR body LIKE '%' || :query || '%' ESCAPE '\')
          AND (:from IS NULL OR entryDate >= :from)
          AND (:to IS NULL OR entryDate <= :to)
          AND (:mood IS NULL OR mood = :mood)
          AND (:tagId IS NULL OR EXISTS (
                SELECT 1 FROM journal_entry_tags t WHERE t.entryId = journal_entries.id AND t.tagId = :tagId))
        ORDER BY entryDate DESC, createdAt DESC
        """,
    )
    fun observeEntries(
        query: String?,
        from: LocalDate?,
        to: LocalDate?,
        mood: Mood?,
        tagId: String?,
    ): Flow<List<JournalEntryWithTags>>

    @Query("SELECT * FROM journal_entries ORDER BY entryDate DESC, createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<JournalEntryEntity>>

    /** Distinct days that have at least one entry, for the calendar view. */
    @Query("SELECT DISTINCT entryDate FROM journal_entries WHERE entryDate BETWEEN :from AND :to ORDER BY entryDate")
    fun observeEntryDates(from: LocalDate, to: LocalDate): Flow<List<LocalDate>>

    // ---- Tags ----

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTag(tag: JournalTagEntity)

    /** Case-insensitive (the column uses NOCASE collation). */
    @Query("SELECT * FROM journal_tags WHERE name = :name LIMIT 1")
    suspend fun findTagByName(name: String): JournalTagEntity?

    @Query("SELECT * FROM journal_tags ORDER BY name")
    fun observeTags(): Flow<List<JournalTagEntity>>

    @Query("UPDATE journal_tags SET name = :name WHERE id = :id")
    suspend fun renameTag(id: String, name: String): Int

    @Query("DELETE FROM journal_tags WHERE id = :id")
    suspend fun deleteTag(id: String): Int

    @Query("DELETE FROM journal_entry_tags WHERE entryId = :entryId")
    suspend fun clearTagsForEntry(entryId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntryTags(refs: List<JournalEntryTagCrossRef>)

    /** Saves an entry and replaces its tag links atomically. */
    @Transaction
    suspend fun saveEntryWithTags(entry: JournalEntryEntity, tagIds: Collection<String>) {
        upsertEntry(entry)
        clearTagsForEntry(entry.id)
        insertEntryTags(tagIds.map { JournalEntryTagCrossRef(entry.id, it) })
    }
}
