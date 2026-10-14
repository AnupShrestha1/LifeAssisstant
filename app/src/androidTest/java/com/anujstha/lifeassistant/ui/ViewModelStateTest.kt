package com.anujstha.lifeassistant.ui

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anujstha.lifeassistant.data.MutableClock
import com.anujstha.lifeassistant.data.SequentialIds
import com.anujstha.lifeassistant.data.database.AppDatabase
import com.anujstha.lifeassistant.data.inMemoryDatabase
import com.anujstha.lifeassistant.data.preferences.SettingsRepository
import com.anujstha.lifeassistant.data.repository.ExpenseCategoryRepository
import com.anujstha.lifeassistant.data.repository.JournalRepository
import com.anujstha.lifeassistant.data.repository.TransactionRepository
import com.anujstha.lifeassistant.domain.model.Mood
import com.anujstha.lifeassistant.domain.model.TransactionType
import com.anujstha.lifeassistant.ui.expenses.TransactionEditViewModel
import com.anujstha.lifeassistant.ui.journal.JournalEditViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/** ViewModel state transitions against a real in-memory database. */
@RunWith(AndroidJUnit4::class)
class ViewModelStateTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = MutableClock()
    private val ids = SequentialIds()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        db = inMemoryDatabase(clock, ids)
        settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { File(context.cacheDir, "vm-${System.nanoTime()}.preferences_pb") })
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    /** ViewModels use viewModelScope (main thread), so create and drive them there. */
    private suspend fun <T> onMain(block: () -> T): T = withContext(Dispatchers.Main) { block() }

    @Test
    fun transactionEditor_invalidAmount_typeSwitch_andSave() = runBlocking {
        val transactions = TransactionRepository(db, clock, ids)
        val categories = ExpenseCategoryRepository(db, clock, ids)
        val vm = onMain { TransactionEditViewModel(null, transactions, categories, settings, clock, Locale.US) }
        withTimeout(5_000) { vm.state.first { it.loaded } }
        assertEquals("NPR", vm.state.value.currency)

        onMain { vm.setAmount("12.345"); vm.save() }
        assertTrue(vm.state.value.amountInvalid)
        assertFalse(vm.state.value.finished)

        val food = categories.observeActive(TransactionType.EXPENSE).first().first { it.name == "Groceries" }
        onMain { vm.setAmount("1,200.50"); vm.setCategory(food.id) }
        assertFalse(vm.state.value.amountInvalid)

        onMain { vm.setType(TransactionType.INCOME) } // categories belong to a type
        assertNull(vm.state.value.categoryId)

        onMain { vm.setType(TransactionType.EXPENSE); vm.setCategory(food.id); vm.save() }
        withTimeout(5_000) { vm.state.first { it.finished } }
        assertEquals(120_050L, transactions.observeFiltered().first().single().amountMinor)
    }

    @Test
    fun journalEditor_draftSurvivesProcessDeath() = runBlocking {
        val journal = JournalRepository(db.journalDao(), clock, ids)
        // The same SavedStateHandle is what Android hands a recreated ViewModel after process death.
        val handle = SavedStateHandle()
        val first = onMain { JournalEditViewModel(null, null, journal, settings, clock, handle) }
        withTimeout(5_000) { first.state.first { it.loaded } }
        onMain { first.setTitle("Draft title"); first.setBody("Half-written\nentry"); first.toggleMood(Mood.SAD) }

        val recreated = onMain { JournalEditViewModel(null, null, journal, settings, clock, handle) }
        val restored = withTimeout(5_000) { recreated.state.first { it.loaded } }

        assertEquals("Draft title", restored.draft.title)
        assertEquals("Half-written\nentry", restored.draft.body)
        assertEquals(Mood.SAD, restored.draft.mood)
        assertTrue(restored.isDirty) // still counts as unsaved, so leaving still asks first
        assertTrue(journal.observeEntries().first().isEmpty()) // nothing was saved behind the user's back
    }

    @Test
    fun journalEditor_tracksUnsavedChanges_andSaves() = runBlocking {
        val journal = JournalRepository(db.journalDao(), clock, ids)
        val vm = onMain { JournalEditViewModel(null, null, journal, settings, clock) }
        withTimeout(5_000) { vm.state.first { it.loaded } }
        assertFalse(vm.state.value.isDirty)

        onMain { vm.setBody("First thoughts") }
        assertTrue(vm.state.value.isDirty)
        onMain { vm.setBody("") }
        assertFalse(vm.state.value.isDirty) // back to the original -> nothing to lose

        onMain { vm.save() } // empty entry
        withTimeout(5_000) { vm.state.first { it.error != null } }
        assertFalse(vm.state.value.finished)

        onMain { vm.setBody("Real entry"); vm.toggleMood(Mood.HAPPY); vm.toggleMood(Mood.HAPPY) }
        assertNull(vm.state.value.draft.mood) // tapping the same mood twice clears it
        onMain { vm.save() }
        withTimeout(5_000) { vm.state.first { it.finished } }
        assertEquals("Real entry", journal.observeEntries().first().single().entry.body)
    }
}
