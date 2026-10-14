package com.anujstha.lifeassistant.ui.expenses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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
import com.anujstha.lifeassistant.data.entity.BudgetEntity
import com.anujstha.lifeassistant.data.entity.ExpenseCategoryEntity
import com.anujstha.lifeassistant.data.preferences.SettingsRepository
import com.anujstha.lifeassistant.data.repository.BudgetRepository
import com.anujstha.lifeassistant.data.repository.ExpenseCategoryRepository
import com.anujstha.lifeassistant.domain.model.TransactionType
import com.anujstha.lifeassistant.ui.components.ScreenScaffold
import com.anujstha.lifeassistant.util.Money
import com.anujstha.lifeassistant.util.currentDateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

data class BudgetsUiState(
    val loaded: Boolean = false,
    val month: YearMonth = YearMonth.now(),
    val currency: String = "NPR",
    /** Effective budgets keyed by category id (null = overall), in the current currency. */
    val budgets: Map<String?, BudgetEntity> = emptyMap(),
    val categories: List<ExpenseCategoryEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetsViewModel(
    private val budgets: BudgetRepository,
    categories: ExpenseCategoryRepository,
    settingsRepository: SettingsRepository,
    clock: Clock,
    private val locale: Locale = Locale.getDefault(),
) : ViewModel() {

    val error = MutableStateFlow<String?>(null)

    private val month = settingsRepository.settings.map { ZoneId.of(it.displayZoneId) }.distinctUntilChanged()
        .flatMapLatest { currentDateFlow(clock, it) }.map { YearMonth.from(it) }.distinctUntilChanged()

    val state: StateFlow<BudgetsUiState> = combine(
        month.flatMapLatest { m -> budgets.observeEffective(m).map { m to it } },
        categories.observeActive(TransactionType.EXPENSE),
        settingsRepository.settings,
    ) { (m, effective), cats, settings ->
        BudgetsUiState(
            loaded = true,
            month = m,
            currency = settings.currencyCode,
            budgets = effective.filter { it.currencyCode == settings.currencyCode }.associateBy { it.categoryId },
            categories = cats,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState())

    /** Returns false (and sets [error]) if the amount can't be parsed. */
    fun save(categoryId: String?, amountText: String, warnAtPercent: Int): Boolean {
        val s = state.value
        val minor = Money.parse(amountText, s.currency, locale)
        if (minor == null || minor <= 0) {
            error.value = "Enter an amount greater than zero"
            return false
        }
        viewModelScope.launch {
            runCatching { budgets.setBudget(s.month, categoryId, minor, s.currency, warnAtPercent) }
                .onFailure { error.value = it.message }
        }
        error.value = null
        return true
    }

    fun remove(categoryId: String?) {
        viewModelScope.launch { budgets.removeBudget(state.value.month, categoryId) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                BudgetsViewModel(c.budgetRepository, c.expenseCategoryRepository, c.settingsRepository, c.clock)
            }
        }
    }
}

/** Marker for "editing the overall budget" in saveable state (category ids are UUIDs). */
private const val OVERALL = "overall"

@Composable
fun BudgetsScreen(
    onBack: () -> Unit,
    viewModel: BudgetsViewModel = viewModel(factory = BudgetsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val money = rememberMoneyFormatter()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }

    ScreenScaffold(title = stringResource(R.string.budget_title), onBack = onBack) { padding ->
        if (!state.loaded) return@ScreenScaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    stringResource(R.string.budget_explain),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                BudgetRow(stringResource(R.string.budget_overall), state.budgets[null], state.currency, money) { editing = OVERALL }
                Text(
                    stringResource(R.string.budget_per_category),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp),
                )
            }
            items(state.categories, key = { it.id }) { c ->
                BudgetRow(c.name, state.budgets[c.id], state.currency, money) { editing = c.id }
            }
        }
    }

    editing?.let { key ->
        val categoryId = key.takeUnless { it == OVERALL }
        val existing = state.budgets[categoryId]
        val name = if (categoryId == null) stringResource(R.string.budget_overall) else state.categories.firstOrNull { it.id == categoryId }?.name.orEmpty()
        BudgetDialog(
            title = stringResource(R.string.budget_edit_title, name),
            currency = state.currency,
            initialAmount = existing?.let { Money.plain(it.limitMinor, state.currency) }.orEmpty(),
            initialWarn = existing?.warnAtPercent ?: 80,
            error = error,
            canRemove = existing != null,
            onSave = { amount, warn -> if (viewModel.save(categoryId, amount, warn)) editing = null },
            onRemove = { viewModel.remove(categoryId); editing = null },
            onDismiss = { viewModel.error.value = null; editing = null },
        )
    }
}

@Composable
private fun BudgetRow(name: String, budget: BudgetEntity?, currency: String, money: (Long, String) -> String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(name) },
        supportingContent = {
            Text(
                budget?.let { "${money(it.limitMinor, currency)} · " + stringResource(R.string.budget_warn_at, it.warnAtPercent) }
                    ?: stringResource(R.string.budget_not_set),
            )
        },
    )
}

@Composable
private fun BudgetDialog(
    title: String,
    currency: String,
    initialAmount: String,
    initialWarn: Int,
    error: String?,
    canRemove: Boolean,
    onSave: (String, Int) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf(initialAmount) }
    var warn by rememberSaveable { mutableFloatStateOf(initialWarn.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.budget_amount, currency)) },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.budget_warn_at, warn.roundToInt()), modifier = Modifier.padding(top = 12.dp))
                // 50–100 % in steps of 5.
                Slider(value = warn, onValueChange = { warn = it }, valueRange = 50f..100f, steps = 9)
                if (canRemove) {
                    TextButton(onClick = onRemove) {
                        Text(stringResource(R.string.budget_remove), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(amount, warn.roundToInt()) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
