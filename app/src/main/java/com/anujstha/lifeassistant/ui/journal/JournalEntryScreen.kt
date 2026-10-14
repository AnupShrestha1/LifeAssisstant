package com.anujstha.lifeassistant.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.anujstha.lifeassistant.R
import com.anujstha.lifeassistant.appContainer
import com.anujstha.lifeassistant.data.entity.JournalEntryWithTags
import com.anujstha.lifeassistant.data.preferences.SettingsRepository
import com.anujstha.lifeassistant.data.repository.JournalRepository
import com.anujstha.lifeassistant.domain.model.TimeFormat
import com.anujstha.lifeassistant.ui.components.ScreenScaffold
import com.anujstha.lifeassistant.ui.components.rememberDateFormatter
import com.anujstha.lifeassistant.ui.components.rememberTimeFormatter
import com.anujstha.lifeassistant.ui.navigation.JournalEntryRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.FormatStyle

/** Loading = not read yet; [entry] null after loading = deleted or missing. */
data class JournalEntryUiState(
    val loaded: Boolean = false,
    val entry: JournalEntryWithTags? = null,
    val zone: ZoneId = ZoneId.systemDefault(),
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
    val deleted: Boolean = false,
)

class JournalEntryViewModel(
    val entryId: String,
    private val repository: JournalRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val deleted = MutableStateFlow(false)

    val state: StateFlow<JournalEntryUiState> = combine(
        repository.observeEntry(entryId),
        settingsRepository.settings,
        deleted,
    ) { entry, settings, deleted ->
        JournalEntryUiState(true, entry, ZoneId.of(settings.displayZoneId), settings.timeFormat, deleted)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalEntryUiState())

    fun delete() {
        viewModelScope.launch {
            repository.deleteEntry(entryId)
            deleted.value = true
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                JournalEntryViewModel(createSavedStateHandle().toRoute<JournalEntryRoute>().entryId, c.journalRepository, c.settingsRepository)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JournalEntryScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: JournalEntryViewModel = viewModel(factory = JournalEntryViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val dateFormatter = rememberDateFormatter(FormatStyle.FULL)
    val stampDate = rememberDateFormatter(FormatStyle.MEDIUM)
    val stampTime = rememberTimeFormatter(state.timeFormat)

    LaunchedEffect(state.loaded, state.entry, state.deleted) {
        if (state.deleted || (state.loaded && state.entry == null)) onBack()
    }

    ScreenScaffold(
        title = state.entry?.entry?.entryDate?.let(dateFormatter::format).orEmpty(),
        onBack = onBack,
        actions = {
            IconButton(onClick = { onEdit(viewModel.entryId) }, enabled = state.entry != null) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
            }
            IconButton(onClick = { confirmDelete = true }, enabled = state.entry != null) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        },
    ) { padding ->
        val item = state.entry ?: return@ScreenScaffold
        val entry = item.entry
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (entry.title.isNotBlank()) {
                Text(entry.title, style = MaterialTheme.typography.headlineSmall)
            }
            if (entry.mood != null || item.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    entry.mood?.let { AssistChip(onClick = {}, label = { Text(stringResource(it.labelRes())) }) }
                    item.tags.forEach { AssistChip(onClick = {}, label = { Text("#${it.name}") }) }
                }
            }
            // Selectable so text can be copied; line breaks are preserved as typed.
            SelectionContainer {
                Text(entry.body, style = MaterialTheme.typography.bodyLarge)
            }
            val created = entry.createdAt.atZone(state.zone)
            val updated = entry.updatedAt.atZone(state.zone)
            Text(
                buildString {
                    append(stringResource(R.string.journal_created, "${stampDate.format(created)} ${stampTime.format(created)}"))
                    if (entry.updatedAt != entry.createdAt) {
                        append("\n")
                        append(stringResource(R.string.journal_edited, "${stampDate.format(updated)} ${stampTime.format(updated)}"))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.journal_delete_title)) },
            text = { Text(stringResource(R.string.journal_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
