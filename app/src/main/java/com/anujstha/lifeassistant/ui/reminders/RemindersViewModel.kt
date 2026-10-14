package com.anujstha.lifeassistant.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anujstha.lifeassistant.appContainer
import com.anujstha.lifeassistant.data.entity.ReminderEntity
import com.anujstha.lifeassistant.data.preferences.SettingsRepository
import com.anujstha.lifeassistant.data.repository.ReminderRepository
import com.anujstha.lifeassistant.data.repository.TaskRepository
import com.anujstha.lifeassistant.domain.model.TimeFormat
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

data class FiredItem(
    val eventId: String,
    val reminderId: String,
    val title: String,
    /** When the occurrence was due, in the display zone. */
    val dueAt: ZonedDateTime,
    val missed: Boolean,
)

data class ReminderRow(
    val reminder: ReminderEntity,
    /** Next alert in the display zone, or null if off / finished. */
    val nextAt: ZonedDateTime?,
    val taskTitle: String?,
)

data class RemindersUiState(
    val loading: Boolean = true,
    val fired: List<FiredItem> = emptyList(),
    val upcoming: List<ReminderRow> = emptyList(),
    val inactive: List<ReminderRow> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
)

class RemindersViewModel(
    private val repository: ReminderRepository,
    taskRepository: TaskRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<RemindersUiState> = combine(
        repository.observeAll(),
        repository.observeActiveEvents(),
        taskRepository.observeTasks(),
        settingsRepository.settings,
    ) { reminders, events, tasks, settings ->
        val zone = ZoneId.of(settings.displayZoneId)
        val taskTitles = tasks.associate { it.id to it.title }
        val rows = reminders.map { r ->
            ReminderRow(r, r.nextTriggerAt?.takeIf { r.enabled }?.atZone(zone), r.taskId?.let(taskTitles::get))
        }
        RemindersUiState(
            loading = false,
            fired = events.map {
                FiredItem(it.event.id, it.reminder.id, it.reminder.title, it.event.occurrenceAt.atZone(zone), it.event.missed)
            },
            upcoming = rows.filter { it.nextAt != null }.sortedBy { it.nextAt },
            inactive = rows.filter { it.nextAt == null },
            today = LocalDate.now(zone),
            timeFormat = settings.timeFormat,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersUiState())

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(id, enabled) }
    }

    fun dismiss(eventId: String) {
        viewModelScope.launch { repository.dismissEvent(eventId) }
    }

    fun dismissAll() {
        viewModelScope.launch { repository.dismissAllEvents() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                RemindersViewModel(c.reminderRepository, c.taskRepository, c.settingsRepository)
            }
        }
    }
}
