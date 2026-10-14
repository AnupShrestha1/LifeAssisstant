package com.anujstha.lifeassistant.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.anujstha.lifeassistant.LifeAssistantApp
import com.anujstha.lifeassistant.MainActivity
import com.anujstha.lifeassistant.data.repository.ReminderInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ReminderFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val repo
        get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as LifeAssistantApp)
            .container.reminderRepository
    private val title = "UI reminder ${System.currentTimeMillis()}"
    private var createdId: String? = null

    @After
    fun cleanUp() {
        runBlocking { createdId?.let { repo.delete(it) } }
    }

    @Test
    fun homeOpensReminders_listShowsReminder_switchTurnsItOff() {
        createdId = runBlocking {
            repo.save(null, ReminderInput(title, startAt = LocalDateTime.now().plusDays(1), zoneId = "Asia/Kathmandu"))
        }
        composeRule.waitUntilAtLeastOneExists(hasText("Today"), timeoutMillis = 5_000)
        composeRule.onNodeWithText("Reminders").performClick()

        composeRule.waitUntilAtLeastOneExists(hasText(title), timeoutMillis = 5_000)
        composeRule.onNodeWithText("New reminder", useUnmergedTree = true).assertIsDisplayed()

        // The row's switch is merged into the list item; clicking the switch toggles it off.
        composeRule.onNode(
            androidx.compose.ui.test.isToggleable() and androidx.compose.ui.test.hasAnySibling(hasText(title)),
            useUnmergedTree = true,
        ).performClick()
        composeRule.waitUntil(5_000) { runBlocking { repo.observe(createdId!!).first()?.enabled == false } }
        assertFalse(runBlocking { repo.observe(createdId!!).first()!!.enabled })
    }
}
