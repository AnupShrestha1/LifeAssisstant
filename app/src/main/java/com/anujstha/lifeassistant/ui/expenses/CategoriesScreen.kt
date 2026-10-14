package com.anujstha.lifeassistant.ui.expenses

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anujstha.lifeassistant.R
import com.anujstha.lifeassistant.appContainer
import com.anujstha.lifeassistant.data.entity.ExpenseCategoryEntity
import com.anujstha.lifeassistant.data.repository.CategoryRemoval
import com.anujstha.lifeassistant.data.repository.ExpenseCategoryRepository
import com.anujstha.lifeassistant.domain.model.TransactionType
import com.anujstha.lifeassistant.ui.components.AppFab
import com.anujstha.lifeassistant.ui.components.ScreenScaffold
import com.anujstha.lifeassistant.ui.journal.TagNameDialog
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CategoryMessage {
    data class Archived(val name: String, val count: Int) : CategoryMessage
    data class Deleted(val name: String) : CategoryMessage
    data class Error(val text: String) : CategoryMessage
}

class CategoriesViewModel(private val repository: ExpenseCategoryRepository) : ViewModel() {
    val categories: StateFlow<List<ExpenseCategoryEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val channel = Channel<CategoryMessage>(Channel.BUFFERED)
    val messages: Flow<CategoryMessage> = channel.receiveAsFlow()

    fun create(name: String, type: TransactionType) = run { repository.create(name, type) }
    fun rename(id: String, name: String) = run { repository.rename(id, name) }
    fun restore(id: String) = run { repository.restore(id) }

    fun remove(category: ExpenseCategoryEntity) = run {
        when (val result = repository.remove(category.id)) {
            CategoryRemoval.Deleted -> channel.send(CategoryMessage.Deleted(category.name))
            is CategoryRemoval.Archived -> channel.send(CategoryMessage.Archived(category.name, result.transactionCount))
        }
    }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { channel.send(CategoryMessage.Error(it.message ?: "Something went wrong")) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { CategoriesViewModel(appContainer().expenseCategoryRepository) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = viewModel(factory = CategoriesViewModel.Factory),
) {
    val all by viewModel.categories.collectAsStateWithLifecycle()
    var type by rememberSaveable { mutableStateOf(TransactionType.EXPENSE) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var renamingId by rememberSaveable { mutableStateOf<String?>(null) }
    var removingId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { m ->
            snackbar.showSnackbar(
                when (m) {
                    is CategoryMessage.Archived -> context.resources.getQuantityString(R.plurals.cat_archived_message, m.count, m.name, m.count)
                    is CategoryMessage.Deleted -> context.getString(R.string.cat_deleted_message, m.name)
                    is CategoryMessage.Error -> m.text
                },
            )
        }
    }

    val ofType = all.filter { it.type == type }
    val active = ofType.filter { it.archivedAt == null }
    val archived = ofType.filter { it.archivedAt != null }

    ScreenScaffold(
        title = stringResource(R.string.cat_title),
        onBack = onBack,
        snackbarHostState = snackbar,
        floatingActionButton = {
            AppFab(stringResource(R.string.cat_add), Icons.Filled.Add, onClick = { creating = true })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = type.ordinal) {
                TransactionType.entries.forEach { t ->
                    Tab(selected = t == type, onClick = { type = t }, text = { Text(stringResource(t.labelRes())) })
                }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                items(active, key = { it.id }) { c ->
                    ListItem(
                        headlineContent = { Text(c.name) },
                        supportingContent = if (c.isDefault) ({ Text(stringResource(R.string.cat_default_label)) }) else null,
                        trailingContent = {
                            Row {
                                IconButton(onClick = { renamingId = c.id }) {
                                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_rename))
                                }
                                IconButton(onClick = { removingId = c.id }) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cat_remove))
                                }
                            }
                        },
                    )
                }
                if (archived.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.cat_archived),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp),
                        )
                    }
                    items(archived, key = { it.id }) { c ->
                        ListItem(
                            headlineContent = { Text(c.name, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            trailingContent = {
                                TextButton(onClick = { viewModel.restore(c.id) }) { Text(stringResource(R.string.cat_restore)) }
                            },
                        )
                    }
                }
            }
        }
    }

    val nameLabel = stringResource(R.string.task_category_name)
    if (creating) {
        TagNameDialog(
            title = stringResource(R.string.cat_add),
            initial = "",
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { viewModel.create(it, type); creating = false },
            onDismiss = { creating = false },
            fieldLabel = nameLabel,
        )
    }
    all.firstOrNull { it.id == renamingId }?.let { c ->
        TagNameDialog(
            title = stringResource(R.string.action_rename),
            initial = c.name,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { viewModel.rename(c.id, it); renamingId = null },
            onDismiss = { renamingId = null },
            fieldLabel = nameLabel,
        )
    }
    all.firstOrNull { it.id == removingId }?.let { c ->
        AlertDialog(
            onDismissRequest = { removingId = null },
            title = { Text(stringResource(R.string.cat_remove_title, c.name)) },
            text = { Text(stringResource(R.string.cat_remove_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.remove(c); removingId = null }) {
                    Text(stringResource(R.string.cat_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { removingId = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
