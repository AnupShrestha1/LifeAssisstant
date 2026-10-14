package com.anujstha.lifeassistant.ui.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anujstha.lifeassistant.R
import com.anujstha.lifeassistant.data.entity.JournalEntryWithTags
import com.anujstha.lifeassistant.domain.model.Mood
import com.anujstha.lifeassistant.ui.components.AppFab
import com.anujstha.lifeassistant.ui.components.Choice
import com.anujstha.lifeassistant.ui.components.DateRangeDialog
import com.anujstha.lifeassistant.ui.components.FilterDropdownChip
import com.anujstha.lifeassistant.ui.components.MonthCalendar
import com.anujstha.lifeassistant.ui.components.ScreenScaffold
import com.anujstha.lifeassistant.ui.components.rememberDateFormatter
import com.anujstha.lifeassistant.ui.components.relativeDate
import java.time.LocalDate

@Composable
fun JournalScreen(
    onOpenEntry: (String) -> Unit,
    onNewEntry: (LocalDate?) -> Unit,
    viewModel: JournalViewModel = viewModel(factory = JournalViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var showRangePicker by rememberSaveable { mutableStateOf(false) }
    var showTags by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    ScreenScaffold(
        title = stringResource(R.string.nav_journal),
        snackbarHostState = snackbar,
        actions = {
            IconButton(onClick = {
                if (searchOpen) viewModel.setQuery("")
                searchOpen = !searchOpen
            }) {
                Icon(
                    if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                    contentDescription = stringResource(if (searchOpen) R.string.action_close_search else R.string.action_search),
                )
            }
            val calendar = state.mode == JournalMode.CALENDAR
            IconButton(onClick = { viewModel.setMode(if (calendar) JournalMode.LIST else JournalMode.CALENDAR) }) {
                Icon(
                    if (calendar) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.CalendarMonth,
                    contentDescription = stringResource(if (calendar) R.string.journal_view_list else R.string.journal_view_calendar),
                )
            }
            IconButton(onClick = { showTags = true }) {
                Icon(Icons.AutoMirrored.Filled.Label, contentDescription = stringResource(R.string.journal_manage_tags))
            }
        },
        floatingActionButton = {
            // In calendar mode a new entry defaults to the selected day.
            AppFab(stringResource(R.string.journal_new), Icons.Filled.Add) {
                onNewEntry(if (state.mode == JournalMode.CALENDAR) state.selectedDay else null)
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (searchOpen) {
                OutlinedTextField(
                    value = state.filters.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text(stringResource(R.string.journal_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            Filters(state, viewModel, onPickDates = { showRangePicker = true })
            HorizontalDivider()

            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                if (state.mode == JournalMode.CALENDAR) {
                    item {
                        MonthCalendar(
                            month = state.month,
                            selected = state.selectedDay,
                            today = state.today,
                            markedDays = state.daysWithEntries,
                            onMonthChange = viewModel::setMonth,
                            onSelect = viewModel::selectDay,
                        )
                        HorizontalDivider()
                    }
                }
                if (!state.loading && state.entries.isEmpty()) {
                    item {
                        val text = when {
                            state.mode == JournalMode.CALENDAR -> R.string.journal_empty_day
                            state.filters.isFiltering -> R.string.journal_empty_filtered
                            else -> R.string.journal_empty
                        }
                        Text(
                            stringResource(text),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                        )
                    }
                }
                // Timeline: a date header whenever the day changes.
                var lastDate: LocalDate? = null
                state.entries.forEach { item ->
                    val date = item.entry.entryDate
                    if (date != lastDate && state.mode == JournalMode.LIST) {
                        item(key = "header-$date") { DateHeader(date, state.today) }
                    }
                    lastDate = date
                    item(key = item.entry.id) { EntryRow(item, onClick = { onOpenEntry(item.entry.id) }) }
                }
            }
        }
    }

    if (showRangePicker) {
        DateRangeDialog(
            initialFrom = state.filters.from,
            initialTo = state.filters.to,
            onConfirm = { from, to ->
                viewModel.setDateRange(from, to)
                showRangePicker = false
            },
            onDismiss = { showRangePicker = false },
        )
    }
    if (showTags) {
        ManageTagsDialog(
            tags = state.tags,
            onCreate = viewModel::createTag,
            onRename = viewModel::renameTag,
            onDelete = viewModel::deleteTag,
            onDismiss = { showTags = false },
        )
    }
}

@Composable
private fun Filters(state: JournalUiState, viewModel: JournalViewModel, onPickDates: () -> Unit) {
    val any = stringResource(R.string.filter_any)
    val dateFormatter = rememberDateFormatter()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterDropdownChip(
            label = stringResource(R.string.journal_filter_mood),
            options = listOf(Choice<Mood?>(null, any)) + Mood.entries.map { Choice<Mood?>(it, stringResource(it.labelRes())) },
            selected = state.filters.mood,
            anyValue = null,
            onSelect = viewModel::setMood,
        )
        FilterDropdownChip(
            label = stringResource(R.string.journal_filter_tag),
            options = listOf(Choice<String?>(null, any)) + state.tags.map { Choice<String?>(it.id, it.name) },
            selected = state.filters.tagId,
            anyValue = null,
            onSelect = viewModel::setTag,
        )
        if (state.mode == JournalMode.LIST) {
            val from = state.filters.from
            val to = state.filters.to
            FilterChip(
                selected = from != null || to != null,
                onClick = onPickDates,
                label = {
                    Text(
                        if (from == null && to == null) {
                            stringResource(R.string.journal_filter_dates)
                        } else {
                            "${from?.let(dateFormatter::format) ?: "…"} – ${to?.let(dateFormatter::format) ?: "…"}"
                        },
                    )
                },
            )
        }
        if (state.filters.mood != null || state.filters.tagId != null || state.filters.from != null || state.filters.to != null) {
            TextButton(onClick = viewModel::clearFilters) { Text(stringResource(R.string.journal_clear_filters)) }
        }
    }
}

@Composable
private fun DateHeader(date: LocalDate, today: LocalDate) {
    Text(
        relativeDate(date, today),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun EntryRow(item: JournalEntryWithTags, onClick: () -> Unit) {
    val entry = item.entry
    val meta = buildList {
        entry.mood?.let { add(stringResource(it.labelRes())) }
        addAll(item.tags.map { "#${it.name}" })
    }.joinToString(" · ")
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            entry.headline() ?: stringResource(R.string.journal_untitled),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val preview = entry.preview()
        if (preview.isNotEmpty()) {
            Text(
                preview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (meta.isNotEmpty()) {
            Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}
