package com.anujstha.lifeassistant.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.anujstha.lifeassistant.LifeAssistantApp
import com.anujstha.lifeassistant.MainActivity
import com.anujstha.lifeassistant.data.repository.JournalFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Write an entry, read it back with line breaks intact, delete it with confirmation. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class JournalFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val title = "Journal UI test ${System.currentTimeMillis()}"
    private val repo
        get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as LifeAssistantApp)
            .container.journalRepository

    @Before
    fun openJournal() {
        composeRule.waitUntilAtLeastOneExists(hasText("Today"), timeoutMillis = 5_000)
        composeRule.onAllNodesWithText("Journal").filterToOne(isSelectable()).performClick()
    }

    @After
    fun cleanUp() {
        runBlocking { repo.observeEntries(JournalFilter(query = title)).first().forEach { repo.deleteEntry(it.entry.id) } }
    }

    @Test
    fun writeReadAndDeleteEntry() {
        composeRule.onNodeWithText("New entry", useUnmergedTree = true).performClick()
        composeRule.waitUntilAtLeastOneExists(hasSetTextAction(), timeoutMillis = 5_000)
        val fields = composeRule.onAllNodes(hasSetTextAction())
        fields[0].performTextInput(title)
        fields[1].performTextInput("First line\n\nThird line")
        composeRule.onNodeWithText("Save").performClick()

        // Back on the list, the entry is shown; open it.
        composeRule.waitUntilAtLeastOneExists(hasText(title), timeoutMillis = 5_000)
        composeRule.onNodeWithText(title).performClick()

        composeRule.waitUntilAtLeastOneExists(hasText("First line\n\nThird line"), timeoutMillis = 5_000)
        val stored = runBlocking { repo.observeEntries(JournalFilter(query = title)).first().single().entry }
        assertEquals("First line\n\nThird line", stored.body)

        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("Delete entry?").assertIsDisplayed()
        composeRule.onAllNodesWithText("Delete").filterToOne(hasText("Delete") and androidx.compose.ui.test.hasClickAction()).performClick()

        composeRule.waitUntil(5_000) {
            runBlocking { repo.observeEntries(JournalFilter(query = title)).first().isEmpty() }
        }
    }

    @Test
    fun leavingWithUnsavedText_asksBeforeDiscarding() {
        composeRule.onNodeWithText("New entry", useUnmergedTree = true).performClick()
        composeRule.waitUntilAtLeastOneExists(hasSetTextAction(), timeoutMillis = 5_000)
        composeRule.onAllNodes(hasSetTextAction())[1].performTextInput("Unsaved thoughts")

        Espresso.closeSoftKeyboard()
        Espresso.pressBack()

        composeRule.waitUntilAtLeastOneExists(hasText("Discard changes?"), timeoutMillis = 5_000)
        composeRule.onNodeWithText("Keep editing").performClick()
        // Still on the editor with the text intact (it may be scrolled under the keyboard).
        composeRule.onNodeWithText("Unsaved thoughts").assertExists()
        assertTrue(runBlocking { repo.observeEntries(JournalFilter(query = "Unsaved thoughts")).first().isEmpty() })
    }
}
