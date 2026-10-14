package com.anujstha.lifeassistant.ui.journal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anujstha.lifeassistant.R
import com.anujstha.lifeassistant.domain.model.Mood
import com.anujstha.lifeassistant.ui.components.DatePickerModal
import com.anujstha.lifeassistant.ui.components.ScreenScaffold
import com.anujstha.lifeassistant.ui.components.rememberDateFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JournalEditScreen(
    onDone: () -> Unit,
    viewModel: JournalEditViewModel = viewModel(factory = JournalEditViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var newTag by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val dateFormatter = rememberDateFormatter(FormatStyle.FULL)
    val draft = state.draft

    LaunchedEffect(state.finished) { if (state.finished) onDone() }
    // Never lose writing silently: ask before leaving with unsaved changes.
    BackHandler(enabled = state.isDirty) { confirmDiscard = true }
    val back = { if (state.isDirty) confirmDiscard = true else onDone() }

    ScreenScaffold(
        title = stringResource(if (state.isNew) R.string.journal_edit_title_new else R.string.journal_edit_title_edit),
        onBack = back,
        actions = {
            TextButton(onClick = viewModel::save, enabled = state.loaded && !state.saving) {
                Text(stringResource(R.string.action_save))
            }
        },
    ) { padding ->
        if (!state.loaded) return@ScreenScaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 8.dp))
            }
            AssistChip(
                onClick = { pickDate = true },
                label = { Text(dateFormatter.format(draft.date)) },
                leadingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Text(
                stringResource(R.string.journal_field_mood),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            FlowRow(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mood.entries.forEach { mood ->
                    FilterChip(
                        selected = draft.mood == mood,
                        onClick = { viewModel.toggleMood(mood) },
                        label = { Text(stringResource(mood.labelRes())) },
                    )
                }
            }

            Text(
                stringResource(R.string.journal_field_tags),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            FlowRow(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    FilterChip(
                        selected = tag.id in draft.tagIds,
                        onClick = { viewModel.toggleTag(tag.id) },
                        label = { Text("#${tag.name}") },
                    )
                }
                AssistChip(
                    onClick = { newTag = true },
                    label = { Text(stringResource(R.string.journal_new_tag)) },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                )
            }

            // Borderless fields read like a page rather than a form.
            val plain = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            )
            TextField(
                value = draft.title,
                onValueChange = viewModel::setTitle,
                placeholder = { Text(stringResource(R.string.journal_field_title), style = MaterialTheme.typography.headlineSmall) },
                textStyle = MaterialTheme.typography.headlineSmall,
                singleLine = true,
                colors = plain,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = draft.body,
                onValueChange = viewModel::setBody,
                placeholder = { Text(stringResource(R.string.journal_field_body)) },
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = plain,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp),
            )
        }
    }

    if (pickDate) {
        DatePickerModal(
            initial = draft.date,
            onConfirm = { viewModel.setDate(it); pickDate = false },
            onDismiss = { pickDate = false },
        )
    }
    if (newTag) {
        TagNameDialog(
            title = stringResource(R.string.journal_new_tag),
            initial = "",
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { viewModel.addTag(it); newTag = false },
            onDismiss = { newTag = false },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.journal_discard_title)) },
            text = { Text(stringResource(R.string.journal_discard_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onDone() }) {
                    Text(stringResource(R.string.journal_discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.journal_keep_editing)) }
            },
        )
    }
}
