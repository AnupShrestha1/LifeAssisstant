package com.anujstha.lifeassistant.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anujstha.lifeassistant.R
import com.anujstha.lifeassistant.data.backup.RestoreMode
import com.anujstha.lifeassistant.ui.components.ScreenScaffold
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun BackupScreen(onBack: () -> Unit, viewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmReplace by rememberSaveable { mutableStateOf(false) }

    // System file pickers: no storage permission, the user chooses the file.
    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::createBackup)
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::openBackup)
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { m ->
            snackbar.showSnackbar(
                when (m) {
                    is BackupMessage.Created -> context.resources.getQuantityString(R.plurals.backup_created, m.records, m.records)
                    BackupMessage.CreateFailed -> context.getString(R.string.backup_create_failed)
                    BackupMessage.ReadFailed -> context.getString(R.string.backup_read_failed)
                    BackupMessage.TooLarge -> context.getString(R.string.backup_too_large)
                    is BackupMessage.Restored -> context.getString(
                        R.string.backup_restored, m.report.added, m.report.skipped, m.report.remindersScheduled,
                    )
                    BackupMessage.RestoreFailed -> context.getString(R.string.backup_restore_failed)
                },
            )
        }
    }

    ScreenScaffold(title = stringResource(R.string.backup_title), onBack = onBack, snackbarHostState = snackbar) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.backup_explain), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.backup_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)

            val busy = state == BackupUiState.Working
            Button(
                onClick = { createLauncher.launch("life-assistant-backup-${LocalDate.now()}.json") },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.backup_create)) }
            OutlinedButton(
                onClick = { openLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.backup_restore)) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            when (val s = state) {
                is BackupUiState.Invalid -> InvalidCard(s.errors, onDismiss = viewModel::dismiss)
                is BackupUiState.Ready -> SummaryCard(
                    s,
                    onMode = viewModel::setMode,
                    onRestore = { if (s.mode == RestoreMode.REPLACE) confirmReplace = true else viewModel.restore() },
                    onCancel = viewModel::dismiss,
                )
                else -> Unit
            }
        }
    }

    if (confirmReplace) {
        ReplaceConfirmDialog(
            onConfirm = { confirmReplace = false; viewModel.restore() },
            onDismiss = { confirmReplace = false },
        )
    }
}

@Composable
private fun InvalidCard(errors: List<String>, onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.backup_invalid_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.backup_invalid_body), style = MaterialTheme.typography.bodySmall)
            errors.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        }
    }
}

@Composable
private fun SummaryCard(
    s: BackupUiState.Ready,
    onMode: (RestoreMode) -> Unit,
    onRestore: () -> Unit,
    onCancel: () -> Unit,
) {
    val created = s.exportedAt?.let {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(it.atZone(ZoneId.systemDefault()))
    } ?: "?"
    val counts = s.preview.inBackup
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.backup_summary_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.backup_summary_meta, created, s.appVersion.ifBlank { "?" }), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.backup_summary_contains), style = MaterialTheme.typography.labelLarge)
            listOf(
                R.plurals.backup_count_journal to "journalEntries",
                R.plurals.backup_count_tags to "journalTags",
                R.plurals.backup_count_tasks to "tasks",
                R.plurals.backup_count_task_categories to "taskCategories",
                R.plurals.backup_count_reminders to "reminders",
                R.plurals.backup_count_transactions to "transactions",
                R.plurals.backup_count_categories to "expenseCategories",
                R.plurals.backup_count_budgets to "budgets",
            ).forEach { (res, key) ->
                val n = counts[key] ?: 0
                Text("• " + pluralStringResource(res, n, n), style = MaterialTheme.typography.bodyMedium)
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                RestoreMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = s.mode == mode,
                        onClick = { onMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, RestoreMode.entries.size),
                        label = { Text(stringResource(if (mode == RestoreMode.MERGE) R.string.backup_mode_merge else R.string.backup_mode_replace)) },
                    )
                }
            }
            if (s.mode == RestoreMode.MERGE) {
                Text(stringResource(R.string.backup_merge_explain), style = MaterialTheme.typography.bodySmall)
                if (s.preview.alreadyPresent > 0) {
                    Text(pluralStringResource(R.plurals.backup_merge_present, s.preview.alreadyPresent, s.preview.alreadyPresent), style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text(stringResource(R.string.backup_replace_explain), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = onRestore, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_do_restore)) }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

@Composable
private fun ReplaceConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    val word = stringResource(R.string.backup_replace_word)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_replace_confirm_title)) },
        text = {
            Column {
                Text(stringResource(R.string.backup_replace_confirm_body))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(R.string.backup_replace_type)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = typed.trim() == word) {
                Text(stringResource(R.string.backup_mode_replace), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
