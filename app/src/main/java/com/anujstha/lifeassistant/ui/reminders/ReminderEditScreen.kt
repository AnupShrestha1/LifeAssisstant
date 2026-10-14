package com.anujstha.lifeassistant.ui.reminders

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.anujstha.lifeassistant.domain.model.TimeFormat
import com.anujstha.lifeassistant.ui.components.DatePickerModal
import com.anujstha.lifeassistant.ui.components.RepeatFields
import com.anujstha.lifeassistant.ui.components.ScreenScaffold
import com.anujstha.lifeassistant.ui.components.TimePickerModal
import com.anujstha.lifeassistant.ui.components.rememberDateFormatter
import com.anujstha.lifeassistant.ui.components.rememberTimeFormatter

private enum class ReminderDialog { DATE, TIME, REPEAT_END, DELETE, PERMISSION }

@Composable
fun ReminderEditScreen(
    onDone: () -> Unit,
    viewModel: ReminderEditViewModel = viewModel(factory = ReminderEditViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timeFormat by viewModel.timeFormat.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<ReminderDialog?>(null) }
    val dateFormatter = rememberDateFormatter()
    val timeFormatter = rememberTimeFormatter(timeFormat)
    val context = LocalContext.current
    // Saving continues whether or not the user grants the permission.
    val permission = rememberNotificationPermissionState(onResult = { viewModel.save() })

    LaunchedEffect(state.finished) { if (state.finished) onDone() }

    ScreenScaffold(
        title = stringResource(if (state.isNew) R.string.reminder_edit_title_new else R.string.reminder_edit_title_edit),
        onBack = onDone,
        actions = {
            if (!state.isNew) {
                IconButton(onClick = { dialog = ReminderDialog.DELETE }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }
            TextButton(
                onClick = { if (permission.granted) viewModel.save() else dialog = ReminderDialog.PERMISSION },
                enabled = state.loaded && !state.saving,
            ) { Text(stringResource(R.string.action_save)) }
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
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.taskTitle?.let {
                Text(stringResource(R.string.reminders_for_task, it), style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                label = { Text(stringResource(R.string.reminder_field_title)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                label = { Text(stringResource(R.string.reminder_field_note)) },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { dialog = ReminderDialog.DATE }) { Text(dateFormatter.format(state.date)) }
                OutlinedButton(onClick = { dialog = ReminderDialog.TIME }) { Text(timeFormatter.format(state.time)) }
            }
            Text(
                stringResource(R.string.reminder_zone_note, state.zoneId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RepeatFields(
                frequency = state.frequency,
                interval = state.interval,
                repeatEnd = state.repeatEnd,
                onFrequency = viewModel::setFrequency,
                onInterval = viewModel::setInterval,
                onPickEnd = { dialog = ReminderDialog.REPEAT_END },
                onClearEnd = { viewModel.setRepeatEnd(null) },
            )
            if (!state.isNew) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.reminders_enabled), modifier = Modifier.weight(1f))
                    Switch(checked = state.enabled, onCheckedChange = viewModel::setEnabled)
                }
            }
        }
    }

    when (dialog) {
        ReminderDialog.DATE -> DatePickerModal(
            initial = state.date,
            onConfirm = { viewModel.setDate(it); dialog = null },
            onDismiss = { dialog = null },
        )
        ReminderDialog.TIME -> TimePickerModal(
            initial = state.time,
            is24Hour = when (timeFormat) {
                TimeFormat.SYSTEM -> DateFormat.is24HourFormat(context)
                TimeFormat.H12 -> false
                TimeFormat.H24 -> true
            },
            onConfirm = { viewModel.setTime(it); dialog = null },
            onDismiss = { dialog = null },
        )
        ReminderDialog.REPEAT_END -> DatePickerModal(
            initial = state.repeatEnd ?: state.date,
            onConfirm = { viewModel.setRepeatEnd(it); dialog = null },
            onDismiss = { dialog = null },
        )
        ReminderDialog.DELETE -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.reminder_delete_title)) },
            text = { Text(stringResource(R.string.reminder_delete_body)) },
            confirmButton = {
                TextButton(onClick = { dialog = null; viewModel.delete() }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
        // Explain why before the system permission prompt appears.
        ReminderDialog.PERMISSION -> AlertDialog(
            onDismissRequest = { dialog = null; viewModel.save() },
            title = { Text(stringResource(R.string.reminders_notif_off_title)) },
            text = { Text(stringResource(R.string.reminders_notif_off_body)) },
            confirmButton = {
                TextButton(onClick = { dialog = null; permission.request() }) {
                    Text(stringResource(R.string.reminders_notif_allow))
                }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null; viewModel.save() }) { Text(stringResource(R.string.reminder_not_now)) }
            },
        )
        null -> Unit
    }
}
