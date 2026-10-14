package com.anujstha.lifeassistant.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.anujstha.lifeassistant.appContainer
import com.anujstha.lifeassistant.data.preferences.SettingsRepository
import com.anujstha.lifeassistant.data.repository.ReminderInput
import com.anujstha.lifeassistant.data.repository.ReminderRepository
import com.anujstha.lifeassistant.data.repository.TaskRepository
import com.anujstha.lifeassistant.domain.model.RecurrenceFrequency
import com.anujstha.lifeassistant.domain.model.RecurrenceRule
import com.anujstha.lifeassistant.domain.model.TimeFormat
import com.anujstha.lifeassistant.domain.model.ValidationException
import com.anujstha.lifeassistant.ui.navigation.ReminderEditRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class ReminderEditState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val title: String = "",
    val note: String = "",
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.of(9, 0),
    /** The zone the wall-clock time is in: display zone for new reminders, the stored zone when editing. */
    val zoneId: String = "",
    val frequency: RecurrenceFrequency? = null,
    val interval: String = "1",
    val repeatEnd: LocalDate? = null,
    val enabled: Boolean = true,
    val taskId: String? = null,
    val taskTitle: String? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val finished: Boolean = false,
)

class ReminderEditViewModel(
    private val reminderId: String?,
    taskIdForNew: String?,
    private val repository: ReminderRepository,
    taskRepository: TaskRepository,
    settingsRepository: SettingsRepository,
    clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(ReminderEditState(isNew = reminderId == null))
    val state: StateFlow<ReminderEditState> = _state.asStateFlow()

    val timeFormat: StateFlow<TimeFormat> = settingsRepository.settings.map { it.timeFormat }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimeFormat.SYSTEM)

    init {
        viewModelScope.launch {
            val existing = reminderId?.let { repository.observe(it).first() }
            if (reminderId != null && existing == null) {
                _state.update { it.copy(loaded = true, finished = true) }
                return@launch
            }
            val taskId = existing?.taskId ?: taskIdForNew
            val task = taskId?.let { taskRepository.observe(it).first() }
            _state.value = if (existing != null) {
                ReminderEditState(
                    loaded = true,
                    isNew = false,
                    title = existing.title,
                    note = existing.description.orEmpty(),
                    date = existing.startAt.toLocalDate(),
                    time = existing.startAt.toLocalTime(),
                    zoneId = existing.zoneId,
                    frequency = existing.recurrence?.frequency,
                    interval = (existing.recurrence?.interval ?: 1).toString(),
                    repeatEnd = existing.recurrence?.endDate,
                    enabled = existing.enabled,
                    taskId = taskId,
                    taskTitle = task?.title,
                )
            } else {
                val zone = ZoneId.of(settingsRepository.settings.first().displayZoneId)
                // Default: the task's due date/time if any, otherwise the next full hour.
                val nextHour = clock.instant().atZone(zone).truncatedTo(ChronoUnit.HOURS).plusHours(1)
                ReminderEditState(
                    loaded = true,
                    isNew = true,
                    title = task?.title.orEmpty(),
                    date = task?.dueDate ?: nextHour.toLocalDate(),
                    time = task?.dueTime ?: nextHour.toLocalTime(),
                    zoneId = zone.id,
                    taskId = taskId,
                    taskTitle = task?.title,
                )
            }
        }
    }

    private fun edit(block: (ReminderEditState) -> ReminderEditState) = _state.update { block(it).copy(error = null) }

    fun setTitle(v: String) = edit { it.copy(title = v) }
    fun setNote(v: String) = edit { it.copy(note = v) }
    fun setDate(v: LocalDate) = edit { it.copy(date = v) }
    fun setTime(v: LocalTime) = edit { it.copy(time = v) }
    fun setFrequency(v: RecurrenceFrequency?) = edit { it.copy(frequency = v) }
    fun setInterval(v: String) = edit { it.copy(interval = v.filter(Char::isDigit).take(3)) }
    fun setRepeatEnd(v: LocalDate?) = edit { it.copy(repeatEnd = v) }
    fun setEnabled(v: Boolean) = edit { it.copy(enabled = v) }

    fun save() {
        val s = _state.value
        if (s.saving || !s.loaded) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                val recurrence = s.frequency?.let { f ->
                    val interval = s.interval.toIntOrNull()
                    if (interval == null || interval !in 1..RecurrenceRule.MAX_INTERVAL) {
                        throw ValidationException("Repeat interval must be between 1 and ${RecurrenceRule.MAX_INTERVAL}")
                    }
                    RecurrenceRule(f, interval, s.repeatEnd)
                }
                repository.save(
                    reminderId,
                    ReminderInput(
                        title = s.title,
                        description = s.note,
                        taskId = s.taskId,
                        startAt = s.date.atTime(s.time),
                        zoneId = s.zoneId,
                        recurrence = recurrence,
                        enabled = s.enabled,
                    ),
                )
                _state.update { it.copy(saving = false, finished = true) }
            } catch (e: ValidationException) {
                _state.update { it.copy(saving = false, error = e.message) }
            }
        }
    }

    fun delete() {
        val id = reminderId ?: return
        viewModelScope.launch {
            repository.delete(id)
            _state.update { it.copy(finished = true) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val route = createSavedStateHandle().toRoute<ReminderEditRoute>()
                val c = appContainer()
                ReminderEditViewModel(
                    route.reminderId, route.taskId, c.reminderRepository, c.taskRepository, c.settingsRepository, c.clock,
                )
            }
        }
    }
}
