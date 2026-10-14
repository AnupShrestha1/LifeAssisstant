package com.anujstha.lifeassistant.ui.expenses

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anujstha.lifeassistant.appContainer
import com.anujstha.lifeassistant.data.entity.ExpenseCategoryEntity
import com.anujstha.lifeassistant.data.entity.TransactionEntity
import com.anujstha.lifeassistant.data.export.TransactionCsv
import com.anujstha.lifeassistant.data.preferences.SettingsRepository
import com.anujstha.lifeassistant.data.repository.BudgetRepository
import com.anujstha.lifeassistant.data.repository.ExpenseCategoryRepository
import com.anujstha.lifeassistant.data.repository.TransactionFilter
import com.anujstha.lifeassistant.data.repository.TransactionRepository
import com.anujstha.lifeassistant.domain.model.TimeFormat
import com.anujstha.lifeassistant.domain.model.TransactionType
import com.anujstha.lifeassistant.domain.usecase.BudgetStatus
import com.anujstha.lifeassistant.domain.usecase.FinanceRules
import com.anujstha.lifeassistant.util.currentDateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

data class TransactionFilters(
    val query: String = "",
    val type: TransactionType? = null,
    val categoryId: String? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
) {
    val isFiltering get() = query.isNotBlank() || type != null || categoryId != null || from != null || to != null
}

/** Expense totals for the current day / week / month plus this month's income, in [currency]. */
data class PeriodTotals(
    val spentToday: Long = 0,
    val spentWeek: Long = 0,
    val spentMonth: Long = 0,
    val incomeMonth: Long = 0,
) {
    val netMonth: Long get() = incomeMonth - spentMonth
}

data class CategoryBudgetAlert(val categoryName: String, val status: BudgetStatus)

data class TransactionRow(val transaction: TransactionEntity, val categoryName: String)

data class ExpensesUiState(
    val loading: Boolean = true,
    val currency: String = "NPR",
    val today: LocalDate = LocalDate.now(),
    val totals: PeriodTotals = PeriodTotals(),
    val overallBudget: BudgetStatus? = null,
    val categoryAlerts: List<CategoryBudgetAlert> = emptyList(),
    val otherCurrencyCount: Int = 0,
    val filters: TransactionFilters = TransactionFilters(),
    val rows: List<TransactionRow> = emptyList(),
    val categories: List<ExpenseCategoryEntity> = emptyList(),
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
)

sealed interface ExpensesMessage {
    data class Exported(val count: Int) : ExpensesMessage
    data object ExportFailed : ExpensesMessage
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExpensesViewModel(
    private val transactions: TransactionRepository,
    private val categories: ExpenseCategoryRepository,
    budgets: BudgetRepository,
    settingsRepository: SettingsRepository,
    clock: Clock,
    private val contentResolver: ContentResolver,
    private val firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek,
) : ViewModel() {

    private val filters = MutableStateFlow(TransactionFilters())
    private val messageChannel = Channel<ExpensesMessage>(Channel.BUFFERED)
    val messages: Flow<ExpensesMessage> = messageChannel.receiveAsFlow()

    private val settings = settingsRepository.settings
    private val currency = settings.map { it.currencyCode }.distinctUntilChanged()
    private val today = settings.map { ZoneId.of(it.displayZoneId) }.distinctUntilChanged()
        .flatMapLatest { currentDateFlow(clock, it) }

    private val context: Flow<Pair<String, LocalDate>> = combine(currency, today, ::Pair).distinctUntilChanged()

    private val totals: Flow<PeriodTotals> = context.flatMapLatest { (cur, day) ->
        val (weekStart, weekEnd) = FinanceRules.weekOf(day, firstDayOfWeek)
        val (monthStart, monthEnd) = FinanceRules.monthOf(day)
        combine(
            transactions.observeTotal(TransactionType.EXPENSE, cur, day, day),
            transactions.observeTotal(TransactionType.EXPENSE, cur, weekStart, weekEnd),
            transactions.observeTotal(TransactionType.EXPENSE, cur, monthStart, monthEnd),
            transactions.observeTotal(TransactionType.INCOME, cur, monthStart, monthEnd),
        ) { t, w, m, i -> PeriodTotals(t, w, m, i) }
    }

    /** Overall budget status plus alerts for category budgets at/over their threshold. */
    private val budgetState: Flow<Pair<BudgetStatus?, List<CategoryBudgetAlert>>> = context.flatMapLatest { (cur, day) ->
        val (monthStart, monthEnd) = FinanceRules.monthOf(day)
        combine(
            budgets.observeEffective(YearMonth.from(day)),
            transactions.observeCategoryTotals(TransactionType.EXPENSE, cur, monthStart, monthEnd),
            categories.observeAll(),
        ) { effective, byCategory, cats ->
            val spentByCategory = byCategory.associate { it.categoryId to it.totalMinor }
            val inCurrency = effective.filter { it.currencyCode == cur }
            val overall = inCurrency.firstOrNull { it.categoryId == null }?.let {
                BudgetStatus(it.limitMinor, spentByCategory.values.sum(), it.warnAtPercent)
            }
            val names = cats.associate { it.id to it.name }
            val alerts = inCurrency.filter { it.categoryId != null }.mapNotNull { b ->
                val status = BudgetStatus(b.limitMinor, spentByCategory[b.categoryId] ?: 0, b.warnAtPercent)
                if (status.level == com.anujstha.lifeassistant.domain.usecase.BudgetLevel.OK) null
                else CategoryBudgetAlert(names[b.categoryId].orEmpty(), status)
            }
            overall to alerts
        }
    }

    private val rows: Flow<List<TransactionRow>> = combine(
        filters.flatMapLatest { f ->
            transactions.observeFiltered(TransactionFilter(f.type, f.categoryId, f.from, f.to, f.query))
        },
        categories.observeAll(),
    ) { list, cats ->
        val names = cats.associate { it.id to it.name }
        list.map { TransactionRow(it, names[it.categoryId].orEmpty()) }
    }

    val uiState: StateFlow<ExpensesUiState> = combine(
        combine(context, totals, budgetState, ::Triple),
        currency.flatMapLatest { transactions.observeCountNotIn(it) },
        combine(filters, rows, ::Pair),
        categories.observeAll(),
        settings,
    ) { (ctx, totals, budget), otherCount, (filters, rows), cats, settings ->
        ExpensesUiState(
            loading = false,
            currency = ctx.first,
            today = ctx.second,
            totals = totals,
            overallBudget = budget.first,
            categoryAlerts = budget.second,
            otherCurrencyCount = otherCount,
            filters = filters,
            rows = rows,
            categories = cats,
            timeFormat = settings.timeFormat,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpensesUiState())

    fun setQuery(v: String) = filters.update { it.copy(query = v) }
    fun setType(v: TransactionType?) = filters.update {
        // A category only makes sense with its own type.
        val keepCategory = v == null || uiState.value.categories.firstOrNull { c -> c.id == it.categoryId }?.type == v
        it.copy(type = v, categoryId = if (keepCategory) it.categoryId else null)
    }
    fun setCategory(v: String?) = filters.update { it.copy(categoryId = v) }
    fun setDateRange(from: LocalDate?, to: LocalDate?) = filters.update { it.copy(from = from, to = to) }

    /** Writes every transaction to [uri] (a document the user picked via the system file picker). */
    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val all = transactions.observeFiltered().first()
                    val cats = categories.observeAll().first()
                    val stream = contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open file")
                    stream.bufferedWriter(Charsets.UTF_8).use { TransactionCsv.write(it, all, cats) }
                    all.size
                }
            }
            messageChannel.send(result.fold({ ExpensesMessage.Exported(it) }, { ExpensesMessage.ExportFailed }))
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                ExpensesViewModel(
                    c.transactionRepository, c.expenseCategoryRepository, c.budgetRepository,
                    c.settingsRepository, c.clock, checkNotNull(this[APPLICATION_KEY]).contentResolver,
                )
            }
        }
    }
}
