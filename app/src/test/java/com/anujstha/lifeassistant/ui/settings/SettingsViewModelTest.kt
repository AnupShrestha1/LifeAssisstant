package com.anujstha.lifeassistant.ui.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.anujstha.lifeassistant.data.preferences.SettingsRepository
import com.anujstha.lifeassistant.domain.model.ThemeMode
import com.anujstha.lifeassistant.domain.model.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-01-15T00:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun uiState_startsLoading_thenLoadsDefaults_thenReflectsThemeChange() = runTest(dispatcher) {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("s.preferences_pb") }
        val vm = SettingsViewModel(SettingsRepository(store), clock, Locale.US)
        assertEquals(SettingsUiState.Loading, vm.uiState.value)
        backgroundScope.launch { vm.uiState.collect {} }

        val loaded = vm.uiState.filterIsInstance<SettingsUiState.Loaded>().first()
        assertEquals(UserSettings(), loaded.settings)

        vm.setThemeMode(ThemeMode.DARK)

        val updated = vm.uiState.filterIsInstance<SettingsUiState.Loaded>()
            .first { it.settings.themeMode == ThemeMode.DARK }
        assertEquals(ThemeMode.DARK, updated.settings.themeMode)
    }

    @Test
    fun currencyChoices_listDefaultCurrencyFirst() {
        val store = PreferenceDataStoreFactory.create { tmp.newFile("c.preferences_pb") }
        val vm = SettingsViewModel(SettingsRepository(store), clock, Locale.US)

        assertEquals("NPR", vm.currencyChoices.first().key)
        assertTrue(vm.currencyChoices.any { it.key == "USD" })
    }

    @Test
    fun zoneChoices_includeKathmanduWithOffset_andSkipLegacyIds() {
        val store = PreferenceDataStoreFactory.create { tmp.newFile("z.preferences_pb") }
        val vm = SettingsViewModel(SettingsRepository(store), clock, Locale.US)

        val ktm = vm.zoneChoices.single { it.key == "Asia/Kathmandu" }
        assertEquals("UTC+05:45", ktm.supportingText)
        assertFalse(vm.zoneChoices.any { it.key.startsWith("Etc/") })
    }

    @Test
    fun formatOffset_rendersUtcPlainly() {
        assertEquals("UTC", SettingsViewModel.formatOffset(ZoneOffset.UTC))
        assertEquals("UTC-05:00", SettingsViewModel.formatOffset(ZoneOffset.ofHours(-5)))
    }
}
