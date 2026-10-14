package com.anujstha.lifeassistant.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anujstha.lifeassistant.data.database.AppDatabase
import com.anujstha.lifeassistant.data.database.DefaultData
import com.anujstha.lifeassistant.data.preferences.SettingsRepository
import com.anujstha.lifeassistant.data.repository.BudgetRepository
import com.anujstha.lifeassistant.data.repository.DataResetter
import com.anujstha.lifeassistant.data.repository.ExpenseCategoryRepository
import com.anujstha.lifeassistant.data.repository.JournalRepository
import com.anujstha.lifeassistant.data.repository.ReminderInput
import com.anujstha.lifeassistant.data.repository.ReminderRepository
import com.anujstha.lifeassistant.data.repository.TaskInput
import com.anujstha.lifeassistant.data.repository.TaskRepository
import com.anujstha.lifeassistant.data.repository.TransactionInput
import com.anujstha.lifeassistant.data.repository.TransactionRepository
import com.anujstha.lifeassistant.domain.model.ThemeMode
import com.anujstha.lifeassistant.domain.model.TransactionType
import com.anujstha.lifeassistant.domain.model.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class DataResetterTest {

    private val clock = MutableClock()
    private val ids = SequentialIds()
    private val scheduler = FakeScheduler()
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsRepository
    private var notificationsCancelled = false

    @Before
    fun setUp() {
        db = inMemoryDatabase(clock, ids)
        val file = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "reset-test-${System.nanoTime()}.preferences_pb")
        settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = storeScope) { file })
    }

    @After
    fun tearDown() {
        db.close()
        storeScope.cancel()
    }

    @Test
    fun clearAll_wipesEverything_restoresDefaults_cancelsAlarms_resetsSettings() = runBlocking {
        val today = LocalDate.of(2026, 10, 14)
        // Populate every module.
        val journal = JournalRepository(db.journalDao(), clock, ids)
        val tag = journal.createTag("Private")
        journal.saveEntry(null, "Secret", "text", today, null, setOf(tag.id))
        val tasks = TaskRepository(db, scheduler, clock, ids)
        val taskId = tasks.save(null, TaskInput("Task", dueDate = today))
        tasks.createCategory("Custom task category")
        val reminders = ReminderRepository(db, scheduler, clock, ids)
        val reminderId = reminders.save(null, ReminderInput("Call", taskId = taskId, startAt = LocalDateTime.of(2026, 10, 20, 9, 0), zoneId = "Asia/Kathmandu"))
        val categories = ExpenseCategoryRepository(db, clock, ids)
        val custom = categories.create("Pets", TransactionType.EXPENSE)
        TransactionRepository(db, clock, ids).save(null, TransactionInput(500, "NPR", TransactionType.EXPENSE, custom.id, today))
        BudgetRepository(db, clock, ids).setBudget(YearMonth.of(2026, 10), null, 10_000, "NPR")
        settings.setThemeMode(ThemeMode.DARK)
        settings.setCurrency("USD")
        assertTrue(scheduler.scheduled.containsKey(reminderId))

        DataResetter(db, scheduler, { notificationsCancelled = true }, settings, clock, ids).clearAll()

        assertTrue(journal.observeEntries().first().isEmpty())
        assertTrue(journal.observeTags().first().isEmpty())
        assertTrue(tasks.observeTasks().first().isEmpty())
        assertTrue(reminders.observeAll().first().isEmpty())
        assertTrue(TransactionRepository(db, clock, ids).observeFiltered().first().isEmpty())
        assertTrue(db.budgetDao().observeUpTo(YearMonth.of(2030, 1)).first().isEmpty())
        // Only the defaults remain.
        assertEquals(
            (DefaultData.expenseCategories + DefaultData.incomeCategories).sorted(),
            categories.observeAll().first().map { it.name }.sorted(),
        )
        assertEquals(DefaultData.taskCategories.sorted(), tasks.observeCategories().first().map { it.name }.sorted())
        // OS side effects.
        assertTrue(scheduler.scheduled.isEmpty())
        assertTrue(reminderId in scheduler.cancelled)
        assertTrue(notificationsCancelled)
        assertEquals(UserSettings(), settings.settings.first())
    }
}
