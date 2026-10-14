package com.anujstha.lifeassistant.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anujstha.lifeassistant.appContainer
import com.anujstha.lifeassistant.data.entity.TaskCategoryEntity
import com.anujstha.lifeassistant.data.entity.TaskEntity
import com.anujstha.lifeassistant.data.preferences.SettingsRepository
import com.anujstha.lifeassistant.data.repository.TaskRepository
import com.anujstha.lifeassistant.domain.model.Priority
import com.anujstha.lifeassistant.domain.model.TaskStatus
import com.anujstha.lifeassistant.domain.model.TimeFormat
import com.anujstha.lifeassistant.domain.usecase.TaskRules
import com.anujstha.lifeassistant.domain.usecase.TaskSort
import com.anujstha.lifeassistant.domain.usecase.TaskView
import com.anujstha.lifeassistant.util.minuteTicks
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
import java.time.LocalDateTime
import java.time.ZoneId

data class TaskFilters(
    val query: String = "",
    val priority: Priority? = null,
    val categoryId: String? = null,
    /** Only applied in the "All" view. */
    val status: TaskStatus? = null,
    val sort: TaskSort = TaskSort.DUE_DATE,
) {
    val isFiltering: Boolean get() = query.isNotBlank() || priority != null || categoryId != null || status != null
}

data class TaskListItem(
    val task: TaskEntity,
    val isOverdue: Boolean,
    val categoryName: String?,
)

data class TasksUiState(
    val loading: Boolean = true,
    val view: TaskView = TaskView.TODAY,
    val filters: TaskFilters = TaskFilters(),
    val items: List<TaskListItem> = emptyList(),
    val overdueCount: Int = 0,
    val categories: List<TaskCategoryEntity> = emptyList(),
    val today: LocalDate? = null,
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
)

/** One-off messages for a snackbar. */
sealed interface TasksMessage {
    data class NextOccurrenceCreated(val date: LocalDate) : TasksMessage
    data class Error(val text: String) : TasksMessage
}

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModel(
    private val repository: TaskRepository,
    settingsRepository: SettingsRepository,
    clock: Clock,
) : ViewModel() {

    private val view = MutableStateFlow(TaskView.TODAY)
    private val filters = MutableStateFlow(TaskFilters())
    private val messageChannel = Channel<TasksMessage>(Channel.BUFFERED)
    val messages: Flow<TasksMessage> = messageChannel.receiveAsFlow()

    private val settings = settingsRepository.settings

    /** Wall-clock "now" in the display zone, refreshed every minute. */
    private val now: Flow<LocalDateTime> = settings
        .map { ZoneId.of(it.displayZoneId) }
        .distinctUntilChanged()
        .flatMapLatest { zone -> minuteTicks(clock).map { LocalDateTime.ofInstant(it, zone) } }

    private val tasks: Flow<List<TaskEntity>> = filters
        .map { Triple(it.query, it.priority, it.categoryId) }
        .distinctUntilChanged()
        .flatMapLatest { (query, priority, categoryId) -> repository.observeTasks(query, priority, categoryId) }

    val uiState: StateFlow<TasksUiState> = combine(
        combine(view, filters, ::Pair),
        tasks,
        now,
        repository.observeAllCategories(),
        settings,
    ) { (view, filters), tasks, now, categories, settings ->
        val categoryNames = categories.associate { it.id to it.name }
        val items = tasks
            .filter { TaskRules.matchesView(it, view, now) }
            .filter { view != TaskView.ALL || filters.status == null || it.status == filters.status }
            .sortedWith(TaskRules.comparator(filters.sort, view))
            .map { TaskListItem(it, TaskRules.isOverdue(it, now), it.categoryId?.let(categoryNames::get)) }
        TasksUiState(
            loading = false,
            view = view,
            filters = filters,
            items = items,
            overdueCount = tasks.count { TaskRules.isOverdue(it, now) },
            categories = categories.filter { it.archivedAt == null },
            today = now.toLocalDate(),
            timeFormat = settings.timeFormat,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    fun setView(value: TaskView) = view.update { value }
    fun setQuery(value: String) = filters.update { it.copy(query = value) }
    fun setPriority(value: Priority?) = filters.update { it.copy(priority = value) }
    fun setCategory(value: String?) = filters.update { it.copy(categoryId = value) }
    fun setStatus(value: TaskStatus?) = filters.update { it.copy(status = value) }
    fun setSort(value: TaskSort) = filters.update { it.copy(sort = value) }

    /** Checkbox: complete an open task, or reopen a closed one. */
    fun toggleComplete(task: TaskEntity) {
        val target = if (task.status == TaskStatus.COMPLETED) TaskStatus.PENDING else TaskStatus.COMPLETED
        viewModelScope.launch {
            runCatching { repository.setStatus(task.id, target) }
                .onSuccess { next -> next?.dueDate?.let { messageChannel.send(TasksMessage.NextOccurrenceCreated(it)) } }
                .onFailure { messageChannel.send(TasksMessage.Error(it.message ?: "Couldn't update the task")) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                TasksViewModel(c.taskRepository, c.settingsRepository, c.clock)
            }
        }
    }
}
