package com.anujstha.lifeassistant.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.anujstha.lifeassistant.LifeAssistantApp
import com.anujstha.lifeassistant.MainActivity
import com.anujstha.lifeassistant.data.repository.TaskInput
import com.anujstha.lifeassistant.domain.model.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/** The dashboard reflects database changes live and its quick actions open the right editors. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DashboardTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val container
        get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as LifeAssistantApp).container
    private val title = "Dashboard task ${System.currentTimeMillis()}"

    @After
    fun cleanUp() {
        runBlocking { container.taskRepository.observeTasks(query = title).first().forEach { container.taskRepository.delete(it.id) } }
    }

    private fun today(): LocalDate = runBlocking {
        LocalDate.now(ZoneId.of(container.settingsRepository.settings.first().displayZoneId))
    }

    @Test
    fun taskDueToday_appearsLive_andCountsUpdateWhenCompleted() {
        composeRule.waitUntilAtLeastOneExists(hasText("Tasks today"), timeoutMillis = 5_000)
        val id = runBlocking { container.taskRepository.save(null, TaskInput(title, dueDate = today())) }

        composeRule.waitUntilAtLeastOneExists(hasText(title), timeoutMillis = 5_000)
        composeRule.onNodeWithText("pending", substring = true).assertExists()

        runBlocking { container.taskRepository.setStatus(id, TaskStatus.COMPLETED) }
        // "N done · M pending" with N >= 1 once the task is completed.
        val atLeastOneDone = SemanticsMatcher("done count >= 1") { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { Regex("^[1-9]\\d* done").containsMatchIn(it.text) }
        }
        composeRule.waitUntilAtLeastOneExists(atLeastOneDone, timeoutMillis = 5_000)
    }

    @Test
    fun quickAddExpense_opensTransactionEditor() {
        composeRule.waitUntilAtLeastOneExists(hasText("Quick add"), timeoutMillis = 5_000)
        composeRule.onNodeWithText("Expense").performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("New transaction"), timeoutMillis = 5_000)
    }

    @Test
    fun moneyCard_isShown() {
        composeRule.waitUntilAtLeastOneExists(hasText("Money this month"), timeoutMillis = 5_000)
        composeRule.onNodeWithText("Money this month").performScrollTo()
        composeRule.onNodeWithText("Spent today").assertExists()
    }
}
