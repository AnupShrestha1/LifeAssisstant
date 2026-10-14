package com.anujstha.lifeassistant.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.anujstha.lifeassistant.domain.model.ThemeMode
import com.anujstha.lifeassistant.domain.model.TimeFormat
import com.anujstha.lifeassistant.domain.model.UserSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.newDataStore(scope: CoroutineScope = backgroundScope) =
        PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("settings.preferences_pb") }

    @Test
    fun emptyStore_returnsDefaults() = runTest(UnconfinedTestDispatcher()) {
        val repo = SettingsRepository(newDataStore())

        val settings = repo.settings.first()

        assertEquals(UserSettings(), settings)
        assertEquals("NPR", settings.currencyCode)
        assertEquals("Asia/Kathmandu", settings.displayZoneId)
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
    }

    @Test
    fun setters_persistValues() = runTest(UnconfinedTestDispatcher()) {
        val repo = SettingsRepository(newDataStore())

        repo.setThemeMode(ThemeMode.DARK)
        repo.setCurrency("USD")
        repo.setDisplayZone("Europe/London")
        repo.setTimeFormat(TimeFormat.H24)

        assertEquals(
            UserSettings(ThemeMode.DARK, "USD", "Europe/London", TimeFormat.H24),
            repo.settings.first(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun setCurrency_rejectsUnknownCode() = runTest(UnconfinedTestDispatcher()) {
        SettingsRepository(newDataStore()).setCurrency("XYZ1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun setDisplayZone_rejectsUnknownZone() = runTest(UnconfinedTestDispatcher()) {
        SettingsRepository(newDataStore()).setDisplayZone("Mars/Olympus_Mons")
    }

    @Test
    fun corruptStoredValues_fallBackToDefaults() = runTest(UnconfinedTestDispatcher()) {
        val store = newDataStore()
        store.edit {
            it[stringPreferencesKey("theme_mode")] = "PURPLE"
            it[stringPreferencesKey("currency_code")] = "not-a-currency"
            it[stringPreferencesKey("display_zone_id")] = "Nowhere/Land"
            it[stringPreferencesKey("time_format")] = ""
        }

        assertEquals(UserSettings(), SettingsRepository(store).settings.first())
    }
}
