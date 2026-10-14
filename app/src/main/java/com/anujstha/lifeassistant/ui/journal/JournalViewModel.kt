package com.anujstha.lifeassistant.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anujstha.lifeassistant.appContainer
import com.anujstha.lifeassistant.data.entity.JournalEntryWithTags
import com.anujstha.lifeassistant.data.entity.JournalTagEntity
import com.anujstha.lifeassistant.data.preferences.SettingsRepository
import com.anujstha.lifeassistant.data.repository.JournalFilter
import com.anujstha.lifeassistant.data.repository.JournalRepository
import com.anujstha.lifeassistant.domain.model.Mood
import com.anujstha.lifeassistant.util.currentDateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class JournalMode { LIST, CALENDAR }

data class JournalFilters(
    val query: String = "",
    val mood: Mood? = null,
    val tagId: String? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
) {
    val isFiltering: Boolean get() = query.isNotBlank() || mood != null || tagId != null || from != null || to != null
}

data class JournalUiState(
    val loading: Boolean = true,
    val mode: JournalMode = JournalMode.LIST,
    val filters: JournalFilters = JournalFilters(),
    val entries: List<JournalEntryWithTags> = emptyList(),
    val tags: List<JournalTagEntity> = emptyList(),
    val month: YearMonth = YearMonth.now(),
    val selectedDay: LocalDate? = null,
    val daysWithEntries: Set<LocalDate> = emptySet(),
    val today: LocalDate = LocalDate.now(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class JournalViewModel(
    private val repository: JournalRepository,
    settingsRepository: SettingsRepository,
    clock: Clock,
) : ViewModel() {

    private val mode = MutableStateFlow(JournalMode.LIST)
    private val filters = MutableStateFlow(JournalFilters())
    private val month = MutableStateFlow(YearMonth.now(clock.withZone(ZoneId.systemDefault())))
    private val selectedDay = MutableStateFlow<LocalDate?>(null)
    private val errors = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = errors.receiveAsFlow()

    private val today: Flow<LocalDate> = settingsRepository.settings
        .map { ZoneId.of(it.displayZoneId) }
        .distinctUntilChanged()
        .flatMapLatest { currentDateFlow(clock, it) }

    /** Calendar mode with a selected day shows that day; otherwise the list filters apply. */
    private val effectiveFilter: Flow<JournalFilter> = combine(mode, filters, selectedDay) { m, f, day ->
        if (m == JournalMode.CALENDAR) {
            JournalFilter(query = f.query, mood = f.mood, tagId = f.tagId, from = day ?: LocalDate.MIN, to = day ?: LocalDate.MIN)
        } else {
            JournalFilter(query = f.query, mood = f.mood, tagId = f.tagId, from = f.from, to = f.to)
        }
    }.distinctUntilChanged()

    private val entries = effectiveFilter.flatMapLatest { repository.observeEntries(it) }

    private val markedDays = month.flatMapLatest { m ->
        repository.observeEntryDates(m.atDay(1), m.atEndOfMonth()).map { it.toSet() }
    }

    val uiState: StateFlow<JournalUiState> = combine(
        combine(mode, filters, month, selectedDay, ::CalendarInputs),
        entries,
        repository.observeTags(),
        markedDays,
        today,
    ) { inputs, entries, tags, marked, today ->
        JournalUiState(
            loading = false,
            mode = inputs.mode,
            filters = inputs.filters,
            entries = entries,
            tags = tags,
            month = inputs.month,
            selectedDay = inputs.selectedDay,
            daysWithEntries = marked,
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalUiState())

    private data class CalendarInputs(
        val mode: JournalMode,
        val filters: JournalFilters,
        val month: YearMonth,
        val selectedDay: LocalDate?,
    )

    fun setMode(value: JournalMode) {
        mode.value = value
        if (value == JournalMode.CALENDAR && selectedDay.value == null) selectDay(uiState.value.today)
    }

    fun setQuery(value: String) = filters.update { it.copy(query = value) }
    fun setMood(value: Mood?) = filters.update { it.copy(mood = value) }
    fun setTag(value: String?) = filters.update { it.copy(tagId = value) }
    fun setDateRange(from: LocalDate?, to: LocalDate?) = filters.update { it.copy(from = from, to = to) }
    fun clearFilters() = filters.update { JournalFilters(query = it.query) }
    fun setMonth(value: YearMonth) = month.update { value }

    fun selectDay(day: LocalDate) {
        selectedDay.value = day
        month.value = YearMonth.from(day)
    }

    fun createTag(name: String) = runCatchingMessage { repository.createTag(name) }
    fun renameTag(id: String, name: String) = runCatchingMessage { repository.renameTag(id, name) }
    fun deleteTag(id: String) = runCatchingMessage {
        repository.deleteTag(id)
        if (filters.value.tagId == id) setTag(null)
    }

    private fun runCatchingMessage(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { errors.send(it.message ?: "Something went wrong") }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                JournalViewModel(c.journalRepository, c.settingsRepository, c.clock)
            }
        }
    }
}
