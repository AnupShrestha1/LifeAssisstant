package com.anujstha.lifeassistant.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anujstha.lifeassistant.data.database.AppDatabase
import com.anujstha.lifeassistant.data.repository.JournalFilter
import com.anujstha.lifeassistant.data.repository.JournalRepository
import com.anujstha.lifeassistant.domain.model.Mood
import com.anujstha.lifeassistant.domain.model.ValidationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class JournalRepositoryTest {

    private val clock = MutableClock()
    private val ids = SequentialIds()
    private lateinit var db: AppDatabase
    private lateinit var repo: JournalRepository

    private val oct14 = LocalDate.of(2026, 10, 14)

    @Before
    fun setUp() {
        db = inMemoryDatabase(clock, ids)
        repo = JournalRepository(db.journalDao(), clock, ids)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun all(filter: JournalFilter = JournalFilter()) = repo.observeEntries(filter).first()

    @Test
    fun emptyDatabase_hasNoEntriesOrTags() = runTest {
        assertTrue(all().isEmpty())
        assertTrue(repo.observeTags().first().isEmpty())
    }

    @Test
    fun create_storesEntryAndPreservesLineBreaks() = runTest {
        val body = "Line one\n\n  Line three with spaces  "
        val id = repo.saveEntry(null, "  Morning  ", body, oct14, Mood.HAPPY, emptySet())

        val saved = repo.observeEntry(id).first()!!.entry
        assertEquals("Morning", saved.title)
        assertEquals(body, saved.body)
        assertEquals(Mood.HAPPY, saved.mood)
        assertEquals(saved.createdAt, saved.updatedAt)
    }

    @Test
    fun edit_keepsCreatedAt_andUpdatesModifiedAt() = runTest {
        val id = repo.saveEntry(null, "Draft", "text", oct14, null, emptySet())
        val created = repo.observeEntry(id).first()!!.entry.createdAt

        clock.advance(Duration.ofHours(3))
        repo.saveEntry(id, "Final", "text", oct14, Mood.NEUTRAL, emptySet())

        val edited = repo.observeEntry(id).first()!!.entry
        assertEquals("Final", edited.title)
        assertEquals(created, edited.createdAt)
        assertEquals(clock.now, edited.updatedAt)
        assertNotEquals(edited.createdAt, edited.updatedAt)
    }

    @Test
    fun multipleEntriesPerDay_newestFirst() = runTest {
        repo.saveEntry(null, "Older day", "a", oct14.minusDays(1), null, emptySet())
        repo.saveEntry(null, "First today", "b", oct14, null, emptySet())
        clock.advance(Duration.ofMinutes(5))
        repo.saveEntry(null, "Second today", "c", oct14, null, emptySet())

        assertEquals(listOf("Second today", "First today", "Older day"), all().map { it.entry.title })
    }

    @Test
    fun search_matchesTitleOrBody_caseInsensitive_andTreatsWildcardsLiterally() = runTest {
        repo.saveEntry(null, "Groceries", "spent 500 rupees", oct14, null, emptySet())
        repo.saveEntry(null, "Sale", "got 50% off", oct14, null, emptySet())

        assertEquals(listOf("Groceries"), all(JournalFilter(query = "GROC")).map { it.entry.title })
        assertEquals(listOf("Groceries"), all(JournalFilter(query = "rupees")).map { it.entry.title })
        // "%" must not act as a wildcard: "50%" matches only the literal text.
        assertEquals(listOf("Sale"), all(JournalFilter(query = "50%")).map { it.entry.title })
        assertTrue(all(JournalFilter(query = "_")).isEmpty())
    }

    @Test
    fun filters_byDateRange_mood_andTag() = runTest {
        val work = repo.createTag("Work")
        repo.saveEntry(null, "A", "x", oct14.minusDays(10), Mood.SAD, emptySet())
        repo.saveEntry(null, "B", "x", oct14, Mood.HAPPY, setOf(work.id))
        repo.saveEntry(null, "C", "x", oct14, Mood.SAD, emptySet())

        assertEquals(setOf("B", "C"), all(JournalFilter(from = oct14.minusDays(1))).map { it.entry.title }.toSet())
        assertEquals(setOf("A", "C"), all(JournalFilter(mood = Mood.SAD)).map { it.entry.title }.toSet())
        assertEquals(listOf("B"), all(JournalFilter(tagId = work.id)).map { it.entry.title })
        assertEquals(listOf("C"), all(JournalFilter(mood = Mood.SAD, from = oct14)).map { it.entry.title })
    }

    @Test
    fun tags_areUniqueIgnoringCase() = runTest {
        val first = repo.createTag("Travel")
        val again = repo.createTag("  travel ")
        assertEquals(first.id, again.id)
        assertEquals(1, repo.observeTags().first().size)
    }

    @Test(expected = ValidationException::class)
    fun renameTag_toExistingName_isRejected() = runTest {
        repo.createTag("Home")
        val work = repo.createTag("Work")
        repo.renameTag(work.id, "HOME")
    }

    @Test
    fun deletingEntry_removesLinks_butKeepsTags() = runTest {
        val tag = repo.createTag("Ideas")
        val id = repo.saveEntry(null, "T", "x", oct14, null, setOf(tag.id))

        repo.deleteEntry(id)

        assertNull(repo.observeEntry(id).first())
        assertEquals(listOf("Ideas"), repo.observeTags().first().map { it.name })
    }

    @Test
    fun deletingTag_removesItFromEntries_butKeepsEntries() = runTest {
        val tag = repo.createTag("Ideas")
        val id = repo.saveEntry(null, "T", "x", oct14, null, setOf(tag.id))

        repo.deleteTag(tag.id)

        val entry = repo.observeEntry(id).first()!!
        assertTrue(entry.tags.isEmpty())
    }

    @Test
    fun editingTags_replacesThePreviousSet() = runTest {
        val a = repo.createTag("A")
        val b = repo.createTag("B")
        val id = repo.saveEntry(null, "T", "x", oct14, null, setOf(a.id))
        repo.saveEntry(id, "T", "x", oct14, null, setOf(b.id))

        assertEquals(listOf("B"), repo.observeEntry(id).first()!!.tags.map { it.name })
    }

    @Test(expected = ValidationException::class)
    fun emptyEntry_isRejected() = runTest {
        repo.saveEntry(null, "   ", "  \n ", oct14, null, emptySet())
    }

    @Test(expected = NoSuchElementException::class)
    fun editingMissingEntry_fails() = runTest {
        repo.saveEntry("does-not-exist", "T", "x", oct14, null, emptySet())
    }
}
