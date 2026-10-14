package com.anujstha.lifeassistant.ui.expenses

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anujstha.lifeassistant.R
import com.anujstha.lifeassistant.domain.model.TransactionType
import com.anujstha.lifeassistant.domain.usecase.BudgetLevel
import com.anujstha.lifeassistant.domain.usecase.BudgetStatus
import com.anujstha.lifeassistant.ui.components.Choice
import com.anujstha.lifeassistant.ui.components.DateRangeDialog
import com.anujstha.lifeassistant.ui.components.FilterDropdownChip
import com.anujstha.lifeassistant.ui.components.AppFab
import com.anujstha.lifeassistant.ui.components.ScreenScaffold
import com.anujstha.lifeassistant.ui.components.dueLabel
import com.anujstha.lifeassistant.ui.components.rememberDateFormatter
import java.time.format.DateTimeFormatter

@Composable
fun ExpensesScreen(
    onOpenTransaction: (String) -> Unit,
    onNewTransaction: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenBudgets: () -> Unit,
    viewModel: ExpensesViewModel = viewModel(factory = ExpensesViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val money = rememberMoneyFormatter()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var pickDates by rememberSaveable { mutableStateOf(false) }

    // The system "save file" picker: no storage permission needed, the user chooses where it goes.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(viewModel::exportCsv)
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbar.showSnackbar(
                when (message) {
                    is ExpensesMessage.Exported -> context.getString(R.string.expenses_export_done, message.count)
                    ExpensesMessage.ExportFailed -> context.getString(R.string.expenses_export_failed)
                },
            )
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.nav_expenses),
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
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.expenses_menu))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.expenses_budgets)) }, onClick = { menuOpen = false; onOpenBudgets() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.expenses_categories)) }, onClick = { menuOpen = false; onOpenCategories() })
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.expenses_export_csv)) },
                        onClick = {
                            menuOpen = false
                            exportLauncher.launch("life-assistant-transactions-${state.today}.csv")
                        },
                    )
                }
            }
        },
        floatingActionButton = {
            AppFab(stringResource(R.string.expenses_add), Icons.Filled.Add, onClick = onNewTransaction)
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
            if (searchOpen) {
                item {
                    OutlinedTextField(
                        value = state.filters.query,
                        onValueChange = viewModel::setQuery,
                        placeholder = { Text(stringResource(R.string.expenses_search_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            if (!state.filters.isFiltering) {
                item { MonthSummary(state, money) }
                item { BudgetCard(state.overallBudget, state.currency, money, onOpenBudgets) }
                state.categoryAlerts.forEach { alert ->
                    item {
                        Text(
                            "${alert.categoryName}: " + budgetLine(alert.status, state.currency, money),
                            color = alert.status.level.color(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                        )
                    }
                }
                if (state.otherCurrencyCount > 0) {
                    item {
                        Text(
                            stringResource(R.string.expenses_other_currency, state.otherCurrencyCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.expenses_history),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp),
                )
                FilterRow(state, viewModel, onPickDates = { pickDates = true })
                HorizontalDivider()
            }
            if (!state.loading && state.rows.isEmpty()) {
                item {
                    Text(
                        stringResource(if (state.filters.isFiltering) R.string.expenses_empty_filtered else R.string.expenses_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            }
            items(state.rows, key = { it.transaction.id }) { row ->
                TransactionRowItem(row, state, money, onClick = { onOpenTransaction(row.transaction.id) })
            }
        }
    }

    if (pickDates) {
        DateRangeDialog(
            initialFrom = state.filters.from,
            initialTo = state.filters.to,
            onConfirm = { from, to -> viewModel.setDateRange(from, to); pickDates = false },
            onDismiss = { pickDates = false },
        )
    }
}

@Composable
private fun MonthSummary(state: ExpensesUiState, money: (Long, String) -> String) {
    val monthName = remember(state.today) { DateTimeFormatter.ofPattern("LLLL").format(state.today) }
    val t = state.totals
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.expenses_this_month, monthName), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth()) {
                Figure(stringResource(R.string.expenses_income), money(t.incomeMonth, state.currency), Modifier.weight(1f))
                Figure(stringResource(R.string.expenses_expenses), money(t.spentMonth, state.currency), Modifier.weight(1f))
                Figure(
                    stringResource(R.string.expenses_net),
                    (if (t.netMonth < 0) "−" else "") + money(kotlin.math.abs(t.netMonth), state.currency),
                    Modifier.weight(1f),
                    color = if (t.netMonth < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                stringResource(R.string.expenses_not_bank_balance),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            Text(stringResource(R.string.expenses_spending), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth()) {
                Figure(stringResource(R.string.expenses_spent_today), money(t.spentToday, state.currency), Modifier.weight(1f))
                Figure(stringResource(R.string.expenses_spent_week), money(t.spentWeek, state.currency), Modifier.weight(1f))
                Figure(stringResource(R.string.expenses_spent_month), money(t.spentMonth, state.currency), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Figure(
    label: String,
    value: String,
    modifier: Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun budgetLine(status: BudgetStatus, currency: String, money: (Long, String) -> String): String {
    val used = stringResource(R.string.expenses_budget_used, money(status.spentMinor, currency), money(status.limitMinor, currency), status.percentUsed)
    val rest = if (status.remainingMinor >= 0) {
        stringResource(R.string.expenses_budget_left, money(status.remainingMinor, currency))
    } else {
        stringResource(R.string.expenses_budget_over, money(-status.remainingMinor, currency))
    }
    return "$used · $rest"
}

@Composable
private fun BudgetCard(status: BudgetStatus?, currency: String, money: (Long, String) -> String, onOpenBudgets: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onOpenBudgets),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.expenses_budget), style = MaterialTheme.typography.titleSmall)
            if (status == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.expenses_budget_none), modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenBudgets) { Text(stringResource(R.string.expenses_budget_set)) }
                }
            } else {
                LinearProgressIndicator(
                    progress = { (status.percentUsed.coerceAtMost(100)) / 100f },
                    color = status.level.color(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(budgetLine(status, currency, money), style = MaterialTheme.typography.bodyMedium, color = status.level.color())
                if (status.level == BudgetLevel.WARNING) {
                    Text(
                        stringResource(R.string.expenses_budget_warning, status.percentUsed),
                        style = MaterialTheme.typography.bodySmall,
                        color = status.level.color(),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterRow(state: ExpensesUiState, viewModel: ExpensesViewModel, onPickDates: () -> Unit) {
    val any = stringResource(R.string.filter_any)
    val dateFormatter = rememberDateFormatter()
    val f = state.filters
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterDropdownChip(
            label = stringResource(R.string.expenses_filter_type),
            options = listOf(Choice<TransactionType?>(null, any)) +
                TransactionType.entries.map { Choice<TransactionType?>(it, stringResource(it.labelRes())) },
            selected = f.type,
            anyValue = null,
            onSelect = viewModel::setType,
        )
        FilterDropdownChip(
            label = stringResource(R.string.expenses_filter_category),
            options = listOf(Choice<String?>(null, any)) +
                state.categories.filter { f.type == null || it.type == f.type }.map { Choice<String?>(it.id, it.name) },
            selected = f.categoryId,
            anyValue = null,
            onSelect = viewModel::setCategory,
        )
        FilterChip(
            selected = f.from != null || f.to != null,
            onClick = onPickDates,
            label = {
                Text(
                    if (f.from == null && f.to == null) stringResource(R.string.journal_filter_dates)
                    else "${f.from?.let(dateFormatter::format) ?: "…"} – ${f.to?.let(dateFormatter::format) ?: "…"}",
                )
            },
        )
    }
}

@Composable
private fun TransactionRowItem(row: TransactionRow, state: ExpensesUiState, money: (Long, String) -> String, onClick: () -> Unit) {
    val t = row.transaction
    val isIncome = t.type == TransactionType.INCOME
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.categoryName, style = MaterialTheme.typography.bodyLarge)
            val details = listOfNotNull(
                dueLabel(t.date, t.time, state.today, state.timeFormat),
                stringResource(t.paymentMethod.labelRes()),
                t.note,
            ).joinToString(" · ")
            Text(
                details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            (if (isIncome) "+" else "−") + money(t.amountMinor, t.currencyCode),
            style = MaterialTheme.typography.titleSmall,
            color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
