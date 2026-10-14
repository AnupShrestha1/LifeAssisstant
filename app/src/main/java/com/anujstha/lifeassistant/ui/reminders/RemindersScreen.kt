package com.anujstha.lifeassistant.ui.reminders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anujstha.lifeassistant.R
import com.anujstha.lifeassistant.domain.model.TimeFormat
import com.anujstha.lifeassistant.ui.components.AppFab
import com.anujstha.lifeassistant.ui.components.ScreenScaffold
import com.anujstha.lifeassistant.ui.components.dueLabel
import java.time.LocalDate
import java.time.ZonedDateTime

@Composable
fun RemindersScreen(
    onBack: () -> Unit,
    onOpenReminder: (String) -> Unit,
    onNewReminder: () -> Unit,
    viewModel: RemindersViewModel = viewModel(factory = RemindersViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permission = rememberNotificationPermissionState()

    ScreenScaffold(
        title = stringResource(R.string.reminders_title),
        onBack = onBack,
        floatingActionButton = {
            AppFab(stringResource(R.string.reminders_new), Icons.Filled.Add, onClick = onNewReminder)
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
            item { NotificationPermissionBanner(permission) }
            item { ExactAlarmBanner() }

            if (state.fired.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(R.string.reminders_fired)) {
                        TextButton(onClick = viewModel::dismissAll) { Text(stringResource(R.string.reminders_dismiss_all)) }
                    }
                }
                items(state.fired, key = { "event-${it.eventId}" }) { item ->
                    val time = when {
                        item.missed -> stringResource(R.string.reminders_missed_label, label(item.dueAt, state.today, state.timeFormat))
                        else -> label(item.dueAt, state.today, state.timeFormat)
                    }
                    ListItem(
                        modifier = Modifier.clickable { onOpenReminder(item.reminderId) },
                        headlineContent = { Text(item.title) },
                        supportingContent = {
                            Text(time, color = if (item.missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingContent = {
                            IconButton(onClick = { viewModel.dismiss(item.eventId) }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.notif_action_dismiss))
                            }
                        },
                    )
                }
            }

            if (!state.loading && state.upcoming.isEmpty() && state.inactive.isEmpty() && state.fired.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxHeight(0.6f).padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.reminders_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            if (state.upcoming.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.reminders_upcoming)) }
                items(state.upcoming, key = { it.reminder.id }) { row ->
                    ReminderRowItem(row, state.today, state.timeFormat, onOpenReminder, viewModel::setEnabled)
                }
            }
            if (state.inactive.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.reminders_off)) }
                items(state.inactive, key = { it.reminder.id }) { row ->
                    ReminderRowItem(row, state.today, state.timeFormat, onOpenReminder, viewModel::setEnabled)
                }
            }
        }
    }
}

@Composable
private fun ReminderRowItem(
    row: ReminderRow,
    today: LocalDate,
    timeFormat: TimeFormat,
    onOpen: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
) {
    val reminder = row.reminder
    val details = buildList {
        row.nextAt?.let { add(label(it, today, timeFormat)) }
        row.taskTitle?.let { add(stringResource(R.string.reminders_for_task, it)) }
    }.joinToString(" · ")
    ListItem(
        modifier = Modifier.clickable { onOpen(reminder.id) },
        headlineContent = { Text(reminder.title) },
        supportingContent = if (details.isNotEmpty()) ({ Text(details) }) else null,
        leadingContent = if (reminder.recurrence != null) {
            { Icon(Icons.Filled.Repeat, contentDescription = stringResource(R.string.tasks_repeats)) }
        } else {
            null
        },
        trailingContent = {
            Switch(
                checked = reminder.enabled && row.nextAt != null,
                onCheckedChange = { onToggle(reminder.id, it) },
            )
        },
    )
}

@Composable
private fun label(at: ZonedDateTime, today: LocalDate, timeFormat: TimeFormat): String =
    dueLabel(at.toLocalDate(), at.toLocalTime(), today, timeFormat)

@Composable
private fun SectionHeader(text: String, action: @Composable () -> Unit = {}) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        action()
    }
}
