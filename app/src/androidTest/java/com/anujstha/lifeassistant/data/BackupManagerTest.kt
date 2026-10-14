package com.anujstha.lifeassistant.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anujstha.lifeassistant.data.backup.BackupData
import com.anujstha.lifeassistant.data.backup.BackupManager
import com.anujstha.lifeassistant.data.backup.DecodeResult
import com.anujstha.lifeassistant.data.backup.RestoreMode
import com.anujstha.lifeassistant.data.database.AppDatabase
import com.anujstha.lifeassistant.data.entity.TransactionEntity
import com.anujstha.lifeassistant.data.preferences.SettingsRepository
import com.anujstha.lifeassistant.data.repository.ExpenseCategoryRepository
import com.anujstha.lifeassistant.data.repository.JournalRepository
import com.anujstha.lifeassistant.data.repository.ReminderInput
import com.anujstha.lifeassistant.data.repository.ReminderRepository
import com.anujstha.lifeassistant.data.repository.TaskInput
import com.anujstha.lifeassistant.data.repository.TaskRepository
import com.anujstha.lifeassistant.data.repository.TransactionInput
import com.anujstha.lifeassistant.data.repository.TransactionRepository
import com.anujstha.lifeassistant.domain.model.PaymentMethod
import com.anujstha.lifeassistant.domain.model.RecurrenceFrequency
import com.anujstha.lifeassistant.domain.model.RecurrenceRule
import com.anujstha.lifeassistant.domain.model.ThemeMode
import com.anujstha.lifeassistant.domain.model.TransactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class BackupManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = MutableClock(Instant.parse("2026-10-14T06:00:00Z"))
    private val ids = SequentialIds()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val today = LocalDate.of(2026, 10, 14)

    /** One "device": its own database, settings, alarm scheduler and backup manager. */
    private inner class Device {
        val db: AppDatabase = inMemoryDatabase(clock, ids)
        val scheduler = FakeScheduler()
        val settings = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = scope) { File(context.cacheDir, "bk-${System.nanoTime()}.preferences_pb") },
        )
        val manager = BackupManager(db, scheduler, {}, settings, clock, context.contentResolver, "test")
        val journal = JournalRepository(db.journalDao(), clock, ids)
        val tasks = TaskRepository(db, scheduler, clock, ids)
        val reminders = ReminderRepository(db, scheduler, clock, ids)
        val categories = ExpenseCategoryRepository(db, clock, ids)
        val transactions = TransactionRepository(db, clock, ids)
    }

    private lateinit var a: Device
    private lateinit var b: Device

    @Before
    fun setUp() {
        a = Device()
        b = Device()
    }

    @After
    fun tearDown() {
        a.db.close()
        b.db.close()
        scope.cancel()
    }

    private suspend fun Device.populate() {
        val tag = journal.createTag("Travel")
        journal.saveEntry(null, "Trip", "Line 1\nLine 2", today, null, setOf(tag.id))
        val pets = categories.create("Pets", TransactionType.EXPENSE)
        transactions.save(null, TransactionInput(12_345, "NPR", TransactionType.EXPENSE, pets.id, today))
        val taskId = tasks.save(null, TaskInput("Vet visit", dueDate = today, recurrence = RecurrenceRule(RecurrenceFrequency.MONTHLY)))
        reminders.save(null, ReminderInput("Vet", taskId = taskId, startAt = LocalDateTime.of(2026, 10, 20, 9, 0), zoneId = "Asia/Kathmandu"))
        settings.setThemeMode(ThemeMode.DARK)
    }

    private fun tempUri(): Uri = Uri.fromFile(File(context.cacheDir, "backup-${System.nanoTime()}.json"))

    @Test
    fun exportReadAndReplace_reproducesTheData() = runBlocking {
        a.populate()
        val uri = tempUri()
        assertTrue(a.manager.export(uri) > 0)

        val read = b.manager.read(uri) as DecodeResult.Valid
        val report = b.manager.restore(read.data, RestoreMode.REPLACE)

        val before = a.manager.snapshot()
        val after = b.manager.snapshot()
        assertEquals(before.journalEntries, after.journalEntries)
        assertEquals(before.journalEntryTags, after.journalEntryTags)
        assertEquals(before.transactions, after.transactions)
        assertEquals(before.tasks, after.tasks)
        assertEquals(before.expenseCategories.sortedBy { it.id }, after.expenseCategories.sortedBy { it.id })
        assertEquals(before.reminders.map { it.id to it.startAt }, after.reminders.map { it.id to it.startAt })
        assertEquals(ThemeMode.DARK, b.settings.settings.first().themeMode) // settings restored in REPLACE
        assertEquals(1, report.remindersScheduled)
        assertEquals(1, b.scheduler.scheduled.size)
    }

    @Test
    fun merge_skipsExisting_keepsLocalEdits_andReusesCategoriesByName() = runBlocking {
        a.populate()
        val backup = (b.manager.read(tempUri().also { a.manager.export(it) }) as DecodeResult.Valid).data

        // Device B already has the same task (edited) and its own "pets" category with a different id.
        b.manager.restore(BackupData(tasks = backup.tasks, taskCategories = backup.taskCategories), RestoreMode.MERGE)
        val taskId = backup.tasks.single().id
        b.tasks.save(taskId, TaskInput("Vet visit (edited here)", dueDate = today, recurrence = RecurrenceRule(RecurrenceFrequency.MONTHLY)))
        val localPets = b.categories.create("pets", TransactionType.EXPENSE)

        val preview = b.manager.preview(backup, RestoreMode.MERGE)
        assertEquals(1, preview.alreadyPresent)
        val report = b.manager.restore(backup, RestoreMode.MERGE)

        assertTrue(report.added > 0)
        assertEquals("Vet visit (edited here)", b.tasks.observe(taskId).first()!!.title) // local edit kept
        assertEquals(1, b.tasks.observeTasks().first().size) // no duplicate
        val txn = b.transactions.observeFiltered().first().single()
        assertEquals(localPets.id, txn.categoryId) // remapped to the existing same-name category
        assertEquals(ThemeMode.SYSTEM, b.settings.settings.first().themeMode) // merge keeps current settings

        // Merging the same backup again adds nothing.
        assertEquals(0, b.manager.restore(backup, RestoreMode.MERGE).added)
    }

    @Test
    fun restoredReminders_areRescheduledFromNow_pastOneTimeOnesTurnOff() = runBlocking {
        a.reminders.save(null, ReminderInput("Future", startAt = LocalDateTime.of(2026, 10, 20, 9, 0), zoneId = "Asia/Kathmandu"))
        a.reminders.save(null, ReminderInput("Daily", startAt = LocalDateTime.of(2026, 10, 15, 7, 0), zoneId = "Asia/Kathmandu", recurrence = RecurrenceRule(RecurrenceFrequency.DAILY)))
        val backup = a.manager.snapshot()
        clock.now = Instant.parse("2026-11-01T00:00:00Z") // restore much later

        b.manager.restore(backup, RestoreMode.REPLACE)

        val restored = b.reminders.observeAll().first().associateBy { it.title }
        assertFalse(restored.getValue("Future").enabled)
        assertNull(restored.getValue("Future").nextTriggerAt)
        assertNotNull(restored.getValue("Daily").nextTriggerAt)
        assertTrue(restored.getValue("Daily").nextTriggerAt!!.isAfter(clock.now))
        assertEquals(setOf(restored.getValue("Daily").id), b.scheduler.scheduled.keys)
        assertEquals(2, restored.values.map { it.requestCode }.toSet().size) // fresh, unique codes
    }

    @Test
    fun failedRestore_changesNothing_evenInReplaceMode() = runBlocking {
        b.populate()
        val before = b.manager.snapshot()
        // Bypasses file validation on purpose: a transaction pointing at a category that doesn't exist.
        val broken = BackupData(
            journalEntries = listOf(before.journalEntries.first().copy(id = "new-entry")),
            transactions = listOf(
                TransactionEntity("bad", 1, "NPR", TransactionType.EXPENSE, "no-such-category", today, null, PaymentMethod.CASH, null, clock.now, clock.now),
            ),
        )
        try {
            b.manager.restore(broken, RestoreMode.REPLACE)
            fail("restore should have failed")
        } catch (expected: Exception) {
            // expected
        }
        val after = b.manager.snapshot()
        assertEquals(before.copy(settings = null), after.copy(settings = null))
        assertEquals(1, b.scheduler.scheduled.size) // existing alarm untouched
    }

    @Test
    fun invalidFile_isReportedAndNothingChanges() = runBlocking {
        b.populate()
        val before = b.manager.snapshot()
        val uri = tempUri()
        File(uri.path!!).writeText("""{"format":"life-assistant-backup","formatVersion":1,"transactions":[{"id":"x"}]}""")
        assertTrue(b.manager.read(uri) is DecodeResult.Invalid)
        assertEquals(before, b.manager.snapshot())
    }
}
