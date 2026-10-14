package com.anujstha.lifeassistant.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.anujstha.lifeassistant.LifeAssistantApp
import com.anujstha.lifeassistant.MainActivity
import com.anujstha.lifeassistant.data.repository.TransactionFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Record an expense through the editor and see it stored exactly and listed. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ExpenseFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val note = "UI expense ${System.currentTimeMillis()}"
    private val repo
        get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as LifeAssistantApp)
            .container.transactionRepository

    @Before
    fun openExpenses() {
        composeRule.waitUntilAtLeastOneExists(hasText("Today"), timeoutMillis = 5_000)
        composeRule.onAllNodesWithText("Expenses").filterToOne(isSelectable()).performClick()
    }

    @After
    fun cleanUp() {
        runBlocking { repo.observeFiltered(TransactionFilter(query = note)).first().forEach { repo.delete(it.id) } }
    }

    @Test
    fun recordExpense_storedInMinorUnits_andListed() {
        composeRule.onNodeWithText("Add", useUnmergedTree = true).performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Choose a category"), timeoutMillis = 5_000)

        composeRule.onNode(hasSetTextAction() and hasText("Amount", substring = true)).performTextInput("1,250.50")
        composeRule.onNodeWithText("Choose a category").performClick()
        composeRule.onNodeWithText("Groceries").performClick()
        composeRule.onNode(hasSetTextAction() and hasText("Description", substring = true)).performTextInput(note)
        composeRule.onNodeWithText("Save").performClick()

        composeRule.waitUntilAtLeastOneExists(hasText(note, substring = true), timeoutMillis = 5_000)
        val stored = runBlocking { repo.observeFiltered(TransactionFilter(query = note)).first().single() }
        assertEquals(125_050L, stored.amountMinor)
        assertEquals("NPR", stored.currencyCode)
        // The transaction row (category, details incl. description, signed amount) is one merged node.
        composeRule.onNode(hasText(note, substring = true) and hasText("−", substring = true) and hasText("1,250.50", substring = true))
            .assertIsDisplayed()
    }

    @Test
    fun invalidAmount_showsError_andDoesNotSave() {
        composeRule.onNodeWithText("Add", useUnmergedTree = true).performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Choose a category"), timeoutMillis = 5_000)
        composeRule.onNode(hasSetTextAction() and hasText("Amount", substring = true)).performTextInput("12.345")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Enter an amount like", substring = true), timeoutMillis = 5_000)
    }
}
