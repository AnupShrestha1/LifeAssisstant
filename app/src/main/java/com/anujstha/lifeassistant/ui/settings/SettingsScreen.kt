package com.anujstha.lifeassistant.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anujstha.lifeassistant.BuildConfig
import com.anujstha.lifeassistant.R
import com.anujstha.lifeassistant.domain.model.ThemeMode
import com.anujstha.lifeassistant.domain.model.TimeFormat
import com.anujstha.lifeassistant.domain.model.UserSettings
import com.anujstha.lifeassistant.ui.components.Choice
import com.anujstha.lifeassistant.ui.components.ScreenScaffold
import com.anujstha.lifeassistant.ui.components.SearchableChoiceDialog
import com.anujstha.lifeassistant.ui.components.SingleChoiceDialog

private enum class SettingsDialog { THEME, CURRENCY, ZONE, TIME_FORMAT }

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var openDialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }

    ScreenScaffold(title = stringResource(R.string.nav_settings)) { padding ->
        val loaded = state as? SettingsUiState.Loaded
        if (loaded != null) {
            SettingsContent(
                settings = loaded.settings,
                modifier = Modifier.padding(padding),
                onOpenDialog = { openDialog = it },
            )
            SettingsDialogs(
                openDialog = openDialog,
                settings = loaded.settings,
                viewModel = viewModel,
                onDismiss = { openDialog = null },
            )
        }
    }
}

@Composable
private fun SettingsContent(
    settings: UserSettings,
    modifier: Modifier,
    onOpenDialog: (SettingsDialog) -> Unit,
) {
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
        SectionHeader(R.string.settings_section_about)
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_privacy)) },
            supportingContent = { Text(stringResource(R.string.settings_privacy_body)) },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_version)) },
            supportingContent = { Text(BuildConfig.VERSION_NAME) },
        )
    }
}

@Composable
private fun SettingsDialogs(
    openDialog: SettingsDialog?,
    settings: UserSettings,
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    when (openDialog) {
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
