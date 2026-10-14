package com.anujstha.lifeassistant.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anujstha.lifeassistant.BuildConfig
import com.anujstha.lifeassistant.LifeAssistantApp
import com.anujstha.lifeassistant.R
import com.anujstha.lifeassistant.domain.model.ThemeMode
import com.anujstha.lifeassistant.domain.model.TimeFormat
import com.anujstha.lifeassistant.domain.model.UserSettings
import com.anujstha.lifeassistant.ui.components.Choice
import com.anujstha.lifeassistant.ui.components.ScreenScaffold
import com.anujstha.lifeassistant.ui.components.SearchableChoiceDialog
import com.anujstha.lifeassistant.ui.components.SingleChoiceDialog
import java.time.LocalDate

private enum class SettingsDialog { THEME, CURRENCY, ZONE, TIME_FORMAT, CLEAR_DATA }

/** Navigation out of Settings. */
data class SettingsActions(
    val openExpenseCategories: () -> Unit,
    val openTaskCategories: () -> Unit,
    val openBudgets: () -> Unit,
    val openAbout: () -> Unit,
    val openBackup: () -> Unit,
)

@Composable
fun SettingsScreen(
    actions: SettingsActions,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    dataViewModel: DataViewModel = viewModel(factory = DataViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var openDialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(dataViewModel::exportCsv)
    }
    LaunchedEffect(dataViewModel) {
        dataViewModel.messages.collect { m ->
            snackbar.showSnackbar(
                when (m) {
                    is DataMessage.Exported -> context.getString(R.string.expenses_export_done, m.count)
                    DataMessage.ExportFailed -> context.getString(R.string.expenses_export_failed)
                    DataMessage.Cleared -> context.getString(R.string.settings_cleared)
                },
            )
        }
    }

    ScreenScaffold(title = stringResource(R.string.nav_settings), snackbarHostState = snackbar) { padding ->
        val loaded = state as? SettingsUiState.Loaded
        if (loaded != null) {
            SettingsContent(
                settings = loaded.settings,
                actions = actions,
                modifier = Modifier.padding(padding),
                onOpenDialog = { openDialog = it },
                onExportCsv = { exportLauncher.launch("life-assistant-transactions-${LocalDate.now()}.csv") },
            )
            SettingsDialogs(
                openDialog = openDialog,
                settings = loaded.settings,
                viewModel = viewModel,
                onClearAll = dataViewModel::clearAll,
                onDismiss = { openDialog = null },
            )
        }
    }
}

@Composable
private fun SettingsContent(
    settings: UserSettings,
    actions: SettingsActions,
    modifier: Modifier,
    onOpenDialog: (SettingsDialog) -> Unit,
    onExportCsv: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as LifeAssistantApp).container }
    var notificationsAllowed by remember { mutableStateOf(container.reminderNotifier.canPostNotifications()) }
    var exactAllowed by remember { mutableStateOf(container.reminderScheduler.canScheduleExact()) }
    // Re-check when returning from system settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsAllowed = container.reminderNotifier.canPostNotifications()
        exactAllowed = container.reminderScheduler.canScheduleExact()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SectionHeader(R.string.settings_section_appearance)
        SettingRow(
            title = stringResource(R.string.settings_theme),
            value = stringResource(settings.themeMode.labelRes()),
            onClick = { onOpenDialog(SettingsDialog.THEME) },
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionHeader(R.string.settings_section_regional)
        SettingRow(
            title = stringResource(R.string.settings_currency),
            value = settings.currencyCode,
            onClick = { onOpenDialog(SettingsDialog.CURRENCY) },
        )
        SettingRow(
            title = stringResource(R.string.settings_time_zone),
            value = settings.displayZoneId,
            supporting = stringResource(R.string.settings_time_zone_hint),
            onClick = { onOpenDialog(SettingsDialog.ZONE) },
        )
        SettingRow(
            title = stringResource(R.string.settings_time_format),
            value = stringResource(settings.timeFormat.labelRes()),
            onClick = { onOpenDialog(SettingsDialog.TIME_FORMAT) },
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionHeader(R.string.settings_section_notifications)
        SettingRow(
            title = stringResource(R.string.settings_notifications),
            value = stringResource(if (notificationsAllowed) R.string.settings_allowed else R.string.settings_blocked),
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SettingRow(
                title = stringResource(R.string.settings_exact_alarms),
                value = stringResource(if (exactAllowed) R.string.settings_exact_allowed else R.string.settings_exact_denied),
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                },
            )
        }
        Text(
            stringResource(R.string.settings_battery_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionHeader(R.string.settings_section_categories)
        LinkRow(R.string.settings_expense_categories, onClick = actions.openExpenseCategories)
        LinkRow(R.string.settings_task_categories, onClick = actions.openTaskCategories)
        LinkRow(R.string.expenses_budgets, onClick = actions.openBudgets)

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionHeader(R.string.settings_section_data)
        LinkRow(R.string.settings_backup, R.string.settings_backup_hint, onClick = actions.openBackup)
        LinkRow(R.string.settings_export_csv, R.string.settings_export_csv_hint, onClick = onExportCsv)
        ListItem(
            modifier = Modifier.clickable { onOpenDialog(SettingsDialog.CLEAR_DATA) },
            headlineContent = { Text(stringResource(R.string.settings_clear_data), color = MaterialTheme.colorScheme.error) },
            supportingContent = { Text(stringResource(R.string.settings_clear_data_hint)) },
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionHeader(R.string.settings_section_about)
        LinkRow(R.string.settings_about_privacy, R.string.settings_about_privacy_hint, onClick = actions.openAbout)
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_version)) },
            supportingContent = { Text(BuildConfig.VERSION_NAME) },
        )
    }
}

@Composable
private fun LinkRow(@StringRes title: Int, @StringRes supporting: Int? = null, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(stringResource(title)) },
        supportingContent = supporting?.let { { Text(stringResource(it)) } },
    )
}

/** Destructive: explains exactly what is deleted and requires typing the confirmation word. */
@Composable
private fun ClearDataDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    val word = stringResource(R.string.settings_clear_confirm_word)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_clear_title)) },
        text = {
            Column {
                Text(stringResource(R.string.settings_clear_body))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(R.string.settings_clear_type)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = typed.trim() == word) {
                Text(stringResource(R.string.settings_clear_button), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun SettingsDialogs(
    openDialog: SettingsDialog?,
    settings: UserSettings,
    viewModel: SettingsViewModel,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (openDialog) {
        SettingsDialog.CLEAR_DATA -> ClearDataDialog(onConfirm = { onDismiss(); onClearAll() }, onDismiss = onDismiss)
        SettingsDialog.THEME -> SingleChoiceDialog(
            title = stringResource(R.string.settings_theme),
            choices = ThemeMode.entries.map { Choice(it, stringResource(it.labelRes())) },
            selected = settings.themeMode,
            onSelect = { viewModel.setThemeMode(it); onDismiss() },
            onDismiss = onDismiss,
        )
        SettingsDialog.TIME_FORMAT -> SingleChoiceDialog(
            title = stringResource(R.string.settings_time_format),
            choices = TimeFormat.entries.map { Choice(it, stringResource(it.labelRes())) },
            selected = settings.timeFormat,
            onSelect = { viewModel.setTimeFormat(it); onDismiss() },
            onDismiss = onDismiss,
        )
        SettingsDialog.CURRENCY -> SearchableChoiceDialog(
            title = stringResource(R.string.settings_currency),
            choices = viewModel.currencyChoices,
            selected = settings.currencyCode,
            onSelect = { viewModel.setCurrency(it); onDismiss() },
            onDismiss = onDismiss,
        )
        SettingsDialog.ZONE -> SearchableChoiceDialog(
            title = stringResource(R.string.settings_time_zone),
            choices = viewModel.zoneChoices,
            selected = settings.displayZoneId,
            onSelect = { viewModel.setDisplayZone(it); onDismiss() },
            onDismiss = onDismiss,
        )
        null -> Unit
    }
}

@Composable
private fun SectionHeader(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit, supporting: String? = null) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text(value, color = MaterialTheme.colorScheme.primary)
                supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
    )
}

@StringRes
private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@StringRes
private fun TimeFormat.labelRes(): Int = when (this) {
    TimeFormat.SYSTEM -> R.string.time_format_system
    TimeFormat.H12 -> R.string.time_format_12h
    TimeFormat.H24 -> R.string.time_format_24h
}
