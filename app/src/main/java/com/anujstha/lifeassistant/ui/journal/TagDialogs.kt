package com.anujstha.lifeassistant.ui.journal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anujstha.lifeassistant.R
import com.anujstha.lifeassistant.data.entity.JournalTagEntity

/** List of tags with rename / delete, plus "New tag". */
@Composable
fun ManageTagsDialog(
    tags: List<JournalTagEntity>,
    onCreate: (String) -> Unit,
    onRename: (id: String, name: String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.journal_manage_tags)) },
        text = {
            Column {
                if (tags.isEmpty()) {
                    Text(stringResource(R.string.journal_no_tags), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(tags, key = { it.id }) { tag ->
                        ListItem(
                            headlineContent = { Text(tag.name) },
                            trailingContent = {
                                androidx.compose.foundation.layout.Row {
                                    IconButton(onClick = { renaming = tag.id }) {
                                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_rename))
                                    }
                                    IconButton(onClick = { deleting = tag.id }) {
                                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                                    }
                                }
                            },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
        dismissButton = { TextButton(onClick = { creating = true }) { Text(stringResource(R.string.journal_new_tag)) } },
    )

    if (creating) {
        TagNameDialog(
            title = stringResource(R.string.journal_new_tag),
            initial = "",
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { onCreate(it); creating = false },
            onDismiss = { creating = false },
        )
    }
    tags.firstOrNull { it.id == renaming }?.let { tag ->
        TagNameDialog(
            title = stringResource(R.string.journal_rename_tag),
            initial = tag.name,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { onRename(tag.id, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }
    tags.firstOrNull { it.id == deleting }?.let { tag ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.journal_delete_tag_title, tag.name)) },
            text = { Text(stringResource(R.string.journal_delete_tag_body)) },
            confirmButton = {
                TextButton(onClick = { onDelete(tag.id); deleting = null }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
fun TagNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    fieldLabel: String = stringResource(R.string.journal_tag_name),
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(fieldLabel) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
