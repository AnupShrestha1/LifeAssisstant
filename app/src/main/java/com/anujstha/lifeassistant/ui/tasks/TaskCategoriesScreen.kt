package com.anujstha.lifeassistant.ui.tasks

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import com.anujstha.lifeassistant.data.entity.TaskCategoryEntity
import com.anujstha.lifeassistant.data.repository.TaskRepository
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

class TaskCategoriesViewModel(private val repository: TaskRepository) : ViewModel() {
    val categories: StateFlow<List<TaskCategoryEntity>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val errors = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = errors.receiveAsFlow()

    fun create(name: String) = run { repository.createCategory(name) }
    fun rename(id: String, name: String) = run { repository.renameCategory(id, name) }
    fun delete(id: String) = run { repository.deleteCategory(id) }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() }.onFailure { errors.send(it.message ?: "Something went wrong") } }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { TaskCategoriesViewModel(appContainer().taskRepository) }
        }
    }
}

@Composable
fun TaskCategoriesScreen(onBack: () -> Unit, viewModel: TaskCategoriesViewModel = viewModel(factory = TaskCategoriesViewModel.Factory)) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    var renamingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    ScreenScaffold(
        title = stringResource(R.string.task_categories_title),
        onBack = onBack,
        snackbarHostState = snackbar,
        floatingActionButton = { AppFab(stringResource(R.string.cat_add), Icons.Filled.Add) { creating = true } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(categories, key = { it.id }) { c ->
                ListItem(
                    headlineContent = { Text(c.name) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { renamingId = c.id }) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_rename))
                            }
                            IconButton(onClick = { deletingId = c.id }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                            }
                        }
                    },
                )
            }
        }
    }

    val label = stringResource(R.string.task_category_name)
    if (creating) {
        TagNameDialog(
            title = stringResource(R.string.cat_add), initial = "", confirmLabel = stringResource(R.string.action_create),
            onConfirm = { viewModel.create(it); creating = false }, onDismiss = { creating = false }, fieldLabel = label,
        )
    }
    categories.firstOrNull { it.id == renamingId }?.let { c ->
        TagNameDialog(
            title = stringResource(R.string.action_rename), initial = c.name, confirmLabel = stringResource(R.string.action_save),
            onConfirm = { viewModel.rename(c.id, it); renamingId = null }, onDismiss = { renamingId = null }, fieldLabel = label,
        )
    }
    categories.firstOrNull { it.id == deletingId }?.let { c ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text(stringResource(R.string.task_category_delete_title, c.name)) },
            text = { Text(stringResource(R.string.task_category_delete_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(c.id); deletingId = null }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deletingId = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
