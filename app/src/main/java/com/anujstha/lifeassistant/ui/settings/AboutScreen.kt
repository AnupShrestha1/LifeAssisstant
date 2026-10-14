package com.anujstha.lifeassistant.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anujstha.lifeassistant.BuildConfig
import com.anujstha.lifeassistant.R
import com.anujstha.lifeassistant.ui.components.ScreenScaffold

/** Plain-language description of how the app handles your data. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.about_title), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
            Section(R.string.about_storage_title, R.string.about_storage_body)
            Section(R.string.about_encryption_title, R.string.about_encryption_body)
            Section(R.string.about_backup_title, R.string.about_backup_body)
            Section(R.string.about_uninstall_title, R.string.about_uninstall_body)
            Section(R.string.about_lost_title, R.string.about_lost_body)
            Section(R.string.about_reminders_title, R.string.about_reminders_body)
            Section(R.string.about_no_ai_title, R.string.about_no_ai_body)
        }
    }
}

@Composable
private fun Section(@StringRes title: Int, @StringRes body: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
    }
}
