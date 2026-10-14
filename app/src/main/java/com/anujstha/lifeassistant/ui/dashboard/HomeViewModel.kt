package com.anujstha.lifeassistant.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anujstha.lifeassistant.appContainer
import com.anujstha.lifeassistant.data.preferences.SettingsRepository
import com.anujstha.lifeassistant.util.currentDateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

data class HomeUiState(val today: LocalDate? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    settingsRepository: SettingsRepository,
    clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = settingsRepository.settings
        .map { ZoneId.of(it.displayZoneId) }
        .distinctUntilChanged()
        .flatMapLatest { zone -> currentDateFlow(clock, zone) }
        .map { HomeUiState(today = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                HomeViewModel(container.settingsRepository, container.clock)
            }
        }
    }
}
