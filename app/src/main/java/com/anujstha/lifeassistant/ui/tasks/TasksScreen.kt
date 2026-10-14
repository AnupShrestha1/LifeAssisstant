package com.anujstha.lifeassistant.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anujstha.lifeassistant.R
import com.anujstha.lifeassistant.domain.model.Priority
import com.anujstha.lifeassistant.domain.model.TaskStatus
import com.anujstha.lifeassistant.domain.model.TimeFormat
import com.anujstha.lifeassistant.domain.usecase.TaskSort
import com.anujstha.lifeassistant.domain.usecase.TaskView
import com.anujstha.lifeassistant.ui.components.Choice
import com.anujstha.lifeassistant.ui.components.FilterDropdownChip
import com.anujstha.lifeassistant.ui.components.ScreenScaffold
import com.anujstha.lifeassistant.ui.components.dueLabel
import com.anujstha.lifeassistant.ui.components.rememberDateFormatter
import java.time.LocalDate

@Composable
fun TasksScreen(
    onOpenTask: (String) -> Unit,
    onNewTask: () -> Unit,
    viewModel: TasksViewModel = viewModel(factory = TasksViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val dateFormatter = rememberDateFormatter()
    var searchOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                is TasksMessage.NextOccurrenceCreated ->
                    context.getString(R.string.tasks_next_created, dateFormatter.format(message.date))
                is TasksMessage.Error -> message.text
            }
            snackbar.showSnackbar(text)
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.nav_tasks),
        snackbarHostState = snackbar,
        actions = {
            IconButton(onClick = {
                if (searchOpen) viewModel.setQuery("")
                searchOpen = !searchOpen
            }) {
                Icon(
                    if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                    contentDescription = stringResource(if (searchOpen) R.string.action_close_search else R.string.action_search),
                )
            }
            SortMenu(selected = state.filters.sort, onSelect = viewModel::setSort)
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewTask,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.tasks_new)) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (searchOpen) {
                OutlinedTextField(
                    value = state.filters.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text(stringResource(R.string.tasks_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            ViewTabs(selected = state.view, overdueCount = state.overdueCount, onSelect = viewModel::setView)
            FilterRow(state, viewModel)
            HorizontalDivider()
            when {
                state.loading -> Unit
                state.items.isEmpty() -> EmptyState(
                    stringResource(if (state.filters.isFiltering) R.string.tasks_empty_filtered else state.view.emptyRes()),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 88.dp), // keep last row clear of the FAB
                ) {
                    items(state.items, key = { it.task.id }) { item ->
                        TaskRow(
                            item = item,
                            today = state.today ?: LocalDate.now(),
                            timeFormat = state.timeFormat,
                            onToggle = { viewModel.toggleComplete(item.task) },
                            onClick = { onOpenTask(item.task.id) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewTabs(selected: TaskView, overdueCount: Int, onSelect: (TaskView) -> Unit) {
    PrimaryScrollableTabRow(selectedTabIndex = selected.ordinal, edgePadding = 8.dp) {
        TaskView.entries.forEach { view ->
            Tab(
                selected = view == selected,
                onClick = { onSelect(view) },
                text = {
                    if (view == TaskView.OVERDUE && overdueCount > 0) {
                        BadgedBox(badge = { Badge { Text(overdueCount.toString()) } }) {
                            Text(stringResource(view.labelRes()), modifier = Modifier.padding(end = 8.dp))
                        }
                    } else {
                        Text(stringResource(view.labelRes()))
                    }
                },
            )
        }
    }
}

@Composable
private fun FilterRow(state: TasksUiState, viewModel: TasksViewModel) {
    val any = stringResource(R.string.filter_any)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterDropdownChip(
            label = stringResource(R.string.tasks_filter_priority),
            options = listOf(Choice<Priority?>(null, any)) +
                Priority.entries.reversed().map { Choice<Priority?>(it, stringResource(it.labelRes())) },
            selected = state.filters.priority,
            anyValue = null,
            onSelect = viewModel::setPriority,
        )
        FilterDropdownChip(
            label = stringResource(R.string.tasks_filter_category),
            options = listOf(Choice<String?>(null, any)) + state.categories.map { Choice<String?>(it.id, it.name) },
            selected = state.filters.categoryId,
            anyValue = null,
            onSelect = viewModel::setCategory,
        )
        if (state.view == TaskView.ALL) {
            FilterDropdownChip(
                label = stringResource(R.string.tasks_filter_status),
                options = listOf(Choice<TaskStatus?>(null, any)) +
                    TaskStatus.entries.map { Choice<TaskStatus?>(it, stringResource(it.labelRes())) },
                selected = state.filters.status,
                anyValue = null,
                onSelect = viewModel::setStatus,
            )
        }
    }
}

@Composable
private fun SortMenu(selected: TaskSort, onSelect: (TaskSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.tasks_sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            TaskSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(stringResource(sort.labelRes())) },
                    leadingIcon = { RadioButton(selected = sort == selected, onClick = null) },
                    onClick = {
                        open = false
                        onSelect(sort)
                    },
                )
            }
        }
    }
}

@Composable
private fun TaskRow(
    item: TaskListItem,
    today: LocalDate,
    timeFormat: TimeFormat,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    val task = item.task
    val closed = !task.status.isOpen
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .toggleable(
                    value = task.status == TaskStatus.COMPLETED,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Checkbox(checked = task.status == TaskStatus.COMPLETED, onCheckedChange = null)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (closed) TextDecoration.LineThrough else null,
                color = if (closed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            TaskMetaLine(item, today, timeFormat)
        }
        if (task.priority >= Priority.HIGH && !closed) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(task.priority.labelRes()),
                style = MaterialTheme.typography.labelMedium,
                color = task.priority.color(),
            )
        }
    }
}

@Composable
private fun TaskMetaLine(item: TaskListItem, today: LocalDate, timeFormat: TimeFormat) {
    val task = item.task
    val parts = buildList {
        if (task.status == TaskStatus.IN_PROGRESS || task.status == TaskStatus.CANCELLED) {
            add(stringResource(task.status.labelRes()))
        }
        task.dueDate?.let { date ->
            val due = dueLabel(date, task.dueTime, today, timeFormat)
            add(if (item.isOverdue) stringResource(R.string.tasks_overdue_label, due) else due)
        }
        item.categoryName?.let(::add)
    }
    if (parts.isEmpty() && task.recurrence == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (task.recurrence != null) {
            Icon(
                Icons.Filled.Repeat,
                contentDescription = stringResource(R.string.tasks_repeats),
                modifier = Modifier.size(14.dp).padding(end = 2.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = parts.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = if (item.isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
