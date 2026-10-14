package com.anujstha.lifeassistant

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.anujstha.lifeassistant.domain.model.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun waitForSettingsLoad() {
        // Start from a known theme even if an earlier, interrupted run left it changed.
        resetTheme()
        composeRule.waitUntilAtLeastOneExists(hasText("Today"), timeoutMillis = 5_000)
    }

    @After
    fun resetTheme() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as LifeAssistantApp
        app.container.settingsRepository.setThemeMode(ThemeMode.SYSTEM)
    }

    /** Bottom-bar item; the same text can also appear as the screen title. */
    private fun navItem(label: String): SemanticsNodeInteraction =
        composeRule.onAllNodesWithText(label).filterToOne(isSelectable())

    @Test
    fun launch_showsHomeSelectedWithAppTitle() {
        composeRule.onNodeWithText("Life Assistant").assertIsDisplayed()
        navItem("Home").assertIsSelected()
    }

    @Test
    fun bottomNav_switchesBetweenTopLevelScreens() {
        navItem("Expenses").performClick()
        navItem("Expenses").assertIsSelected()
        composeRule.onNodeWithText("Monthly budget").assertIsDisplayed()

        navItem("Journal").performClick()
        navItem("Journal").assertIsSelected()
        composeRule.onNodeWithText("New entry", useUnmergedTree = true).assertIsDisplayed()

        navItem("Tasks").performClick()
        navItem("Tasks").assertIsSelected()
        composeRule.onNodeWithText("New task", useUnmergedTree = true).assertIsDisplayed()

        navItem("Settings").performClick()
        composeRule.onNodeWithText("Theme").assertIsDisplayed()

        navItem("Home").performClick()
        composeRule.onNodeWithText("Quick add").assertIsDisplayed() // unique on Home ("Today" can also label a journal entry)
    }

    @Test
    fun settings_changingThemeUpdatesDisplayedValue() {
        navItem("Settings").performClick()

        composeRule.onNodeWithText("Theme").performClick()
        // The radio option in the dialog (selectable), not the current-value label.
        composeRule.onAllNodesWithText("Dark").filterToOne(isSelectable()).performClick()

        composeRule.waitUntilAtLeastOneExists(hasText("Dark"), timeoutMillis = 5_000)
        composeRule.onNodeWithText("Dark").assertIsDisplayed()
    }
}
