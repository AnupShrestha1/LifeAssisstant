package com.anujstha.lifeassistant.data.repository

import com.anujstha.lifeassistant.data.dao.JournalDao
import com.anujstha.lifeassistant.data.entity.JournalEntryEntity
import com.anujstha.lifeassistant.data.entity.JournalEntryWithTags
import com.anujstha.lifeassistant.data.entity.JournalTagEntity
import com.anujstha.lifeassistant.domain.model.Mood
import com.anujstha.lifeassistant.domain.model.validate
import com.anujstha.lifeassistant.util.IdGenerator
import com.anujstha.lifeassistant.util.escapeLike
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate

data class JournalFilter(
    val query: String? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val mood: Mood? = null,
    val tagId: String? = null,
)

class JournalRepository(
    private val dao: JournalDao,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    fun observeEntries(filter: JournalFilter = JournalFilter()): Flow<List<JournalEntryWithTags>> =
        dao.observeEntries(
            query = filter.query?.trim()?.takeIf { it.isNotEmpty() }?.let(::escapeLike),
            from = filter.from,
            to = filter.to,
            mood = filter.mood,
            tagId = filter.tagId,
        )

    fun observeEntry(id: String): Flow<JournalEntryWithTags?> = dao.observeEntry(id)

    fun observeRecent(limit: Int): Flow<List<JournalEntryEntity>> = dao.observeRecent(limit)

    fun observeEntryDates(from: LocalDate, to: LocalDate): Flow<List<LocalDate>> = dao.observeEntryDates(from, to)

    fun observeTags(): Flow<List<JournalTagEntity>> = dao.observeTags()

    /**
     * Creates ([id] == null) or updates an entry and its tags. On update the original
     * createdAt is kept and updatedAt is set to now. The body is stored exactly as typed
     * (line breaks and spacing preserved). Returns the entry id.
     */
    suspend fun saveEntry(
        id: String?,
        title: String,
        body: String,
        entryDate: LocalDate,
        mood: Mood?,
        tagIds: Set<String>,
    ): String {
        val cleanTitle = title.trim()
        validate(cleanTitle.isNotEmpty() || body.isNotBlank()) { "Add a title or some text" }
        validate(cleanTitle.length <= MAX_TITLE) { "Title is too long (max $MAX_TITLE characters)" }
        validate(body.length <= MAX_BODY) { "Entry is too long (max $MAX_BODY characters)" }

        val existing = id?.let { dao.getEntry(it) ?: throw NoSuchElementException("Entry not found") }
        val now = clock.instant()
        val entry = JournalEntryEntity(
            id = existing?.id ?: ids.newId(),
            title = cleanTitle,
            body = body,
            entryDate = entryDate,
            mood = mood,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        dao.saveEntryWithTags(entry, tagIds)
        return entry.id
    }

    suspend fun deleteEntry(id: String): Boolean = dao.deleteEntry(id) > 0

    /** Returns the existing tag if one with the same name (ignoring case) already exists. */
    suspend fun createTag(name: String): JournalTagEntity {
        val clean = normalizeTagName(name)
        dao.findTagByName(clean)?.let { return it }
        val tag = JournalTagEntity(id = ids.newId(), name = clean, createdAt = clock.instant())
        dao.insertTag(tag)
        return tag
    }

    suspend fun renameTag(id: String, name: String) {
        val clean = normalizeTagName(name)
        val clash = dao.findTagByName(clean)
        validate(clash == null || clash.id == id) { "A tag named \"$clean\" already exists" }
        dao.renameTag(id, clean)
    }

    /** Removes the tag from all entries; the entries themselves are kept. */
    suspend fun deleteTag(id: String): Boolean = dao.deleteTag(id) > 0

    private fun normalizeTagName(name: String): String {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        validate(clean.isNotEmpty()) { "Tag name can't be empty" }
        validate(clean.length <= MAX_TAG) { "Tag name is too long (max $MAX_TAG characters)" }
        return clean
    }

    companion object {
        const val MAX_TITLE = 200
        const val MAX_BODY = 100_000
        const val MAX_TAG = 40
    }
}
