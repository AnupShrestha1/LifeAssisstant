package com.anujstha.lifeassistant.ui.tasks

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anujstha.lifeassistant.R
import com.anujstha.lifeassistant.domain.model.Priority
import com.anujstha.lifeassistant.domain.model.TaskStatus
import com.anujstha.lifeassistant.domain.model.TimeFormat
import com.anujstha.lifeassistant.ui.components.Choice
import com.anujstha.lifeassistant.ui.components.DatePickerModal
import com.anujstha.lifeassistant.ui.components.DropdownField
import com.anujstha.lifeassistant.ui.components.ScreenScaffold
import com.anujstha.lifeassistant.ui.components.TimePickerModal
import com.anujstha.lifeassistant.ui.components.rememberDateFormatter
import com.anujstha.lifeassistant.ui.components.rememberTimeFormatter
import java.time.LocalDate
import java.time.ZoneId
import com.anujstha.lifeassistant.ui.components.RepeatFields
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.foundation.clickable

private const val NEW_CATEGORY = "\u0000new"

private enum class PickerDialog { DUE_DATE, DUE_TIME, REPEAT_END, NEW_CATEGORY, DELETE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    onDone: () -> Unit,
    onOpenReminder: (reminderId: String) -> Unit,
    onAddReminder: (taskId: String) -> Unit,
    viewModel: TaskEditViewModel = viewModel(factory = TaskEditViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val timeFormat by viewModel.timeFormat.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<PickerDialog?>(null) }
    val dateFormatter = rememberDateFormatter()
    val timeFormatter = rememberTimeFormatter(timeFormat)
    val context = LocalContext.current

    LaunchedEffect(state.finished) { if (state.finished) onDone() }

    ScreenScaffold(
        title = stringResource(if (state.isNew) R.string.task_edit_title_new else R.string.task_edit_title_edit),
        onBack = onDone,
        actions = {
            if (!state.isNew) {
                IconButton(onClick = { dialog = PickerDialog.DELETE }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }
            TextButton(onClick = viewModel::save, enabled = state.loaded && !state.saving) {
                Text(stringResource(R.string.action_save))
            }
        },
    ) { padding ->
        if (!state.loaded) return@ScreenScaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                label = { Text(stringResource(R.string.task_field_title)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::setDescription,
                label = { Text(stringResource(R.string.task_field_description)) },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            FieldLabel(R.string.task_field_priority)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Priority.entries.forEachIndexed { index, priority ->
                    SegmentedButton(
                        selected = state.priority == priority,
                        onClick = { viewModel.setPriority(priority) },
                        shape = SegmentedButtonDefaults.itemShape(index, Priority.entries.size),
                        label = { Text(stringResource(priority.labelRes()), maxLines = 1) },
                    )
                }
            }

            if (!state.isNew) {
                DropdownField(
                    label = stringResource(R.string.task_field_status),
                    options = TaskStatus.entries.map { Choice(it, stringResource(it.labelRes())) },
                    selected = state.status,
                    onSelect = viewModel::setStatus,
                )
            }

            DropdownField(
                label = stringResource(R.string.task_field_category),
                options = listOf(Choice<String?>(null, stringResource(R.string.task_category_none))) +
                    categories.map { Choice<String?>(it.id, it.name) } +
                    Choice<String?>(NEW_CATEGORY, stringResource(R.string.task_category_new)),
                selected = state.categoryId,
                onSelect = { if (it == NEW_CATEGORY) dialog = PickerDialog.NEW_CATEGORY else viewModel.setCategory(it) },
            )

            FieldLabel(R.string.task_field_due_date)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { dialog = PickerDialog.DUE_DATE }) {
                    Text(state.dueDate?.let(dateFormatter::format) ?: stringResource(R.string.task_no_due_date))
                }
                OutlinedButton(onClick = { dialog = PickerDialog.DUE_TIME }, enabled = state.dueDate != null) {
                    Text(state.dueTime?.let(timeFormatter::format) ?: stringResource(R.string.task_no_due_time))
                }
                if (state.dueDate != null) {
                    TextButton(onClick = { viewModel.setDueDate(null) }) { Text(stringResource(R.string.clear)) }
                }
            }
            if (state.dueTime != null) {
                TextButton(onClick = { viewModel.setDueTime(null) }) {
                    Text(stringResource(R.string.task_remove_time))
                }
            }

            RepeatFields(
                frequency = state.frequency,
                interval = state.interval,
                repeatEnd = state.repeatEnd,
                onFrequency = { viewModel.setFrequency(it, LocalDate.now()) },
                onInterval = viewModel::setInterval,
                onPickEnd = { dialog = PickerDialog.REPEAT_END },
                onClearEnd = { viewModel.setRepeatEnd(null) },
            )

            if (state.wasRecurring) {
                Text(
                    stringResource(R.string.task_series_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Reminders belong to a saved task, so the section appears when editing.
            val taskId = viewModel.taskId
            if (taskId != null) {
                FieldLabel(R.string.task_reminders)
                reminders.forEach { reminder ->
                    Text(
                        text = reminder.title + (reminder.nextTriggerAt?.let { at ->
                            val local = at.atZone(ZoneId.of(reminder.zoneId))
                            " · ${dateFormatter.format(local)} ${timeFormatter.format(local)}"
                        } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenReminder(reminder.id) }
                            .padding(vertical = 8.dp),
                    )
                }
                OutlinedButton(onClick = { onAddReminder(taskId) }) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                    Text(stringResource(R.string.task_add_reminder), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    when (dialog) {
        PickerDialog.DUE_DATE -> DatePickerModal(
            initial = state.dueDate,
            onConfirm = { viewModel.setDueDate(it); dialog = null },
            onDismiss = { dialog = null },
        )
        PickerDialog.DUE_TIME -> TimePickerModal(
            initial = state.dueTime,
            is24Hour = when (timeFormat) {
                TimeFormat.SYSTEM -> DateFormat.is24HourFormat(context)
                TimeFormat.H12 -> false
                TimeFormat.H24 -> true
            },
            onConfirm = { viewModel.setDueTime(it); dialog = null },
            onDismiss = { dialog = null },
        )
        PickerDialog.REPEAT_END -> DatePickerModal(
            initial = state.repeatEnd ?: state.dueDate,
            onConfirm = { viewModel.setRepeatEnd(it); dialog = null },
            onDismiss = { dialog = null },
        )
        PickerDialog.NEW_CATEGORY -> NewCategoryDialog(
            onCreate = { viewModel.createCategory(it); dialog = null },
            onDismiss = { dialog = null },
        )
        PickerDialog.DELETE -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.task_delete_title)) },
            text = {
                Text(stringResource(if (state.wasRecurring) R.string.task_delete_body_recurring else R.string.task_delete_body))
            },
            confirmButton = {
                TextButton(onClick = { dialog = null; viewModel.delete() }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
        null -> Unit
    }
}

@Composable
private fun NewCategoryDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.task_category_new)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.task_category_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun FieldLabel(res: Int) {
    Text(stringResource(res), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
