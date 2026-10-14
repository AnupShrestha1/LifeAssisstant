package com.anujstha.lifeassistant.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.anujstha.lifeassistant.LifeAssistantApp
import com.anujstha.lifeassistant.MainActivity
import com.anujstha.lifeassistant.domain.model.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end: create a task through the editor, see it listed, tick it, find it in Completed. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TaskFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val title = "UI test task ${System.currentTimeMillis()}"
    private val repo
        get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as LifeAssistantApp)
            .container.taskRepository

    @Before
    fun openTasks() {
        composeRule.waitUntilAtLeastOneExists(hasText("Today"), timeoutMillis = 5_000)
        composeRule.onAllNodesWithText("Tasks").filterToOne(isSelectable()).performClick()
    }

    @After
    fun cleanUp() = runBlocking {
        repo.observeTasks(query = title).first().forEach { repo.delete(it.id) }
    }

    private fun tab(label: String) = composeRule.onAllNodesWithText(label).filterToOne(isSelectable())

    @Test
    fun createTickAndFindInCompleted() {
        tab("All").performClick()
        composeRule.onNodeWithText("New task", useUnmergedTree = true).performClick()

        composeRule.onNode(hasSetTextAction() and hasText("Title")).performTextInput(title)
        composeRule.onNodeWithText("Save").performClick()

        composeRule.waitUntilAtLeastOneExists(hasText(title), timeoutMillis = 5_000)
        val checkbox = composeRule.onNode(isToggleable() and hasParent(hasText(title)))
        checkbox.performClick()

        // The change goes through the database and back to the list asynchronously.
        composeRule.waitUntil(5_000) {
            runBlocking { repo.observeTasks(query = title).first().single().status == TaskStatus.COMPLETED }
        }
        // Room notifies the list on a background thread, so wait for the row to re-render.
        composeRule.waitUntil(5_000) { runCatching { checkbox.assertIsOn() }.isSuccess }
        tab("Completed").performClick()
        composeRule.waitUntilAtLeastOneExists(hasText(title), timeoutMillis = 5_000)
        composeRule.onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun emptyTitle_showsValidationError_andStaysOnEditor() {
        composeRule.onNodeWithText("New task", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Title can't be empty"), timeoutMillis = 5_000)
        composeRule.onNodeWithText("Title can't be empty").assertIsDisplayed()
    }
}
