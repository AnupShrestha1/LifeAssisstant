package com.anujstha.lifeassistant.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anujstha.lifeassistant.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SettingsFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun openSettings() {
        composeRule.waitUntilAtLeastOneExists(hasText("Today"), timeoutMillis = 5_000)
        composeRule.onAllNodesWithText("Settings").filterToOne(isSelectable()).performClick()
    }

    @Test
    fun clearAllData_requiresTypingDelete_thenCanBeCancelled() {
        composeRule.onNodeWithText("Clear all data").performScrollTo().performClick()
        composeRule.onNodeWithText("Delete all data?").assertExists()
        val button = composeRule.onNodeWithText("Delete everything")
        button.assertIsNotEnabled()

        composeRule.onNode(hasSetTextAction()).performTextInput("delete") // wrong case
        button.assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("DELETE")
        button.assertIsEnabled()

        composeRule.onNodeWithText("Cancel").performClick() // never actually wipe the device under test
        composeRule.onNodeWithText("Delete all data?").assertDoesNotExist()
    }

    @Test
    fun aboutAndPrivacy_explainsUninstallAndLostDevice() {
        composeRule.onNodeWithText("About and privacy").performScrollTo().performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("If you uninstall the app"), timeoutMillis = 5_000)
        composeRule.onNodeWithText("If you lose your phone or reset it").performScrollTo()
    }
}
