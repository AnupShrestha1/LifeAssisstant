package com.anujstha.lifeassistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anujstha.lifeassistant.appContainer
import com.anujstha.lifeassistant.data.preferences.SettingsRepository
import com.anujstha.lifeassistant.domain.model.ThemeMode
import com.anujstha.lifeassistant.domain.model.TimeFormat
import com.anujstha.lifeassistant.domain.model.UserSettings
import com.anujstha.lifeassistant.ui.components.Choice
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Currency
import java.util.Locale

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Loaded(val settings: UserSettings) : SettingsUiState
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
    private val locale: Locale = Locale.getDefault(),
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = settingsRepository.settings
        .map<UserSettings, SettingsUiState> { SettingsUiState.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)

    /** All ISO currencies, default currency first, then by code. */
    val currencyChoices: List<Choice<String>> by lazy {
        Currency.getAvailableCurrencies()
            .sortedWith(compareBy<Currency> { it.currencyCode != UserSettings.DEFAULT_CURRENCY }.thenBy { it.currencyCode })
            .map { Choice(it.currencyCode, it.currencyCode, it.getDisplayName(locale)) }
    }

    /** Region-based IANA zones (skips legacy aliases like "Etc/GMT+5"), sorted by id. */
    val zoneChoices: List<Choice<String>> by lazy {
        val now = clock.instant()
        ZoneId.getAvailableZoneIds()
            .filter { '/' in it && !it.startsWith("Etc/") && !it.startsWith("SystemV/") }
            .sorted()
            .map { id -> Choice(id, id, formatOffset(ZoneId.of(id).rules.getOffset(now))) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setCurrency(code: String) {
        viewModelScope.launch { settingsRepository.setCurrency(code) }
    }

    fun setDisplayZone(zoneId: String) {
        viewModelScope.launch { settingsRepository.setDisplayZone(zoneId) }
    }

    fun setTimeFormat(format: TimeFormat) {
        viewModelScope.launch { settingsRepository.setTimeFormat(format) }
    }

    companion object {
        fun formatOffset(offset: ZoneOffset): String =
            if (offset == ZoneOffset.UTC) "UTC" else "UTC${offset.id}"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                SettingsViewModel(container.settingsRepository, container.clock)
            }
        }
    }
}
