package com.anujstha.lifeassistant.ui.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.anujstha.lifeassistant.appContainer
import com.anujstha.lifeassistant.data.entity.ReminderEntity
import com.anujstha.lifeassistant.data.entity.TaskCategoryEntity
import com.anujstha.lifeassistant.data.repository.ReminderRepository
import com.anujstha.lifeassistant.data.repository.TaskInput
import com.anujstha.lifeassistant.data.repository.TaskRepository
import com.anujstha.lifeassistant.domain.model.Priority
import com.anujstha.lifeassistant.domain.model.RecurrenceFrequency
import com.anujstha.lifeassistant.domain.model.RecurrenceRule
import com.anujstha.lifeassistant.domain.model.TaskStatus
import com.anujstha.lifeassistant.domain.model.TimeFormat
import com.anujstha.lifeassistant.domain.model.ValidationException
import com.anujstha.lifeassistant.data.preferences.SettingsRepository
import com.anujstha.lifeassistant.ui.navigation.TaskEditRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class TaskEditState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val title: String = "",
    val description: String = "",
    val status: TaskStatus = TaskStatus.PENDING,
    val priority: Priority = Priority.MEDIUM,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val categoryId: String? = null,
    val frequency: RecurrenceFrequency? = null,
    /** Kept as text so the field can be edited freely; validated on save. */
    val interval: String = "1",
    val repeatEnd: LocalDate? = null,
    val wasRecurring: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    /** True once saved or deleted; the screen then navigates back. */
    val finished: Boolean = false,
)

class TaskEditViewModel(
    /** Null while creating a new task. */
    val taskId: String?,
    private val repository: TaskRepository,
    reminderRepository: ReminderRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TaskEditState(isNew = taskId == null, loaded = taskId == null))
    val state: StateFlow<TaskEditState> = _state.asStateFlow()

    val categories: StateFlow<List<TaskCategoryEntity>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reminders: StateFlow<List<ReminderEntity>> =
        (if (taskId != null) reminderRepository.observeForTask(taskId) else flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val timeFormat: StateFlow<TimeFormat> = settingsRepository.settings.map { it.timeFormat }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimeFormat.SYSTEM)

    init {
        if (taskId != null) {
            viewModelScope.launch {
                val task = repository.observe(taskId).first()
                if (task == null) {
                    _state.update { it.copy(loaded = true, finished = true) }
                    return@launch
                }
                _state.value = TaskEditState(
                    loaded = true,
                    isNew = false,
                    title = task.title,
                    description = task.description.orEmpty(),
                    status = task.status,
                    priority = task.priority,
                    dueDate = task.dueDate,
                    dueTime = task.dueTime,
                    categoryId = task.categoryId,
                    frequency = task.recurrence?.frequency,
                    interval = (task.recurrence?.interval ?: 1).toString(),
                    repeatEnd = task.recurrence?.endDate,
                    wasRecurring = task.recurrence != null,
                )
            }
        }
    }

    private fun edit(block: (TaskEditState) -> TaskEditState) = _state.update { block(it).copy(error = null) }

    fun setTitle(value: String) = edit { it.copy(title = value) }
    fun setDescription(value: String) = edit { it.copy(description = value) }
    fun setStatus(value: TaskStatus) = edit { it.copy(status = value) }
    fun setPriority(value: Priority) = edit { it.copy(priority = value) }
    fun setCategory(value: String?) = edit { it.copy(categoryId = value) }
    fun setDueTime(value: LocalTime?) = edit { it.copy(dueTime = value) }
    fun setInterval(value: String) = edit { it.copy(interval = value.filter(Char::isDigit).take(3)) }
    fun setRepeatEnd(value: LocalDate?) = edit { it.copy(repeatEnd = value) }

    /** Clearing the date also clears the time and repetition, which both depend on it. */
    fun setDueDate(value: LocalDate?) = edit {
        if (value == null) it.copy(dueDate = null, dueTime = null, frequency = null, repeatEnd = null) else it.copy(dueDate = value)
    }

    /** Choosing a repeat without a due date starts it today. */
    fun setFrequency(value: RecurrenceFrequency?, today: LocalDate) = edit {
        it.copy(frequency = value, dueDate = if (value != null) it.dueDate ?: today else it.dueDate)
    }

    fun createCategory(name: String) {
        viewModelScope.launch {
            runCatching { repository.createCategory(name) }
                .onSuccess { category -> edit { it.copy(categoryId = category.id) } }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                val recurrence = s.frequency?.let { frequency ->
                    val interval = s.interval.toIntOrNull()
                    if (interval == null || interval !in 1..RecurrenceRule.MAX_INTERVAL) {
                        throw ValidationException("Repeat interval must be between 1 and ${RecurrenceRule.MAX_INTERVAL}")
                    }
                    RecurrenceRule(frequency, interval, s.repeatEnd)
                }
                repository.save(
                    taskId,
                    TaskInput(
                        title = s.title,
                        description = s.description,
                        status = s.status,
                        priority = s.priority,
                        dueDate = s.dueDate,
                        dueTime = s.dueTime,
                        categoryId = s.categoryId,
                        recurrence = recurrence,
                    ),
                )
                _state.update { it.copy(saving = false, finished = true) }
            } catch (e: ValidationException) {
                _state.update { it.copy(saving = false, error = e.message) }
            } catch (e: NoSuchElementException) {
                _state.update { it.copy(saving = false, finished = true) }
            }
        }
    }

    fun delete() {
        val id = taskId ?: return
        viewModelScope.launch {
            repository.delete(id)
            _state.update { it.copy(finished = true) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val handle: SavedStateHandle = createSavedStateHandle()
                val c = appContainer()
                TaskEditViewModel(handle.toRoute<TaskEditRoute>().taskId, c.taskRepository, c.reminderRepository, c.settingsRepository)
            }
        }
    }
}
