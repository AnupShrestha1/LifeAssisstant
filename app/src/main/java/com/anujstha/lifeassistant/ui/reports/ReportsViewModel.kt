package com.anujstha.lifeassistant.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anujstha.lifeassistant.appContainer
import com.anujstha.lifeassistant.data.preferences.SettingsRepository
import com.anujstha.lifeassistant.data.repository.ExpenseCategoryRepository
import com.anujstha.lifeassistant.data.repository.JournalFilter
import com.anujstha.lifeassistant.data.repository.JournalRepository
import com.anujstha.lifeassistant.data.repository.TaskRepository
import com.anujstha.lifeassistant.data.repository.TransactionRepository
import com.anujstha.lifeassistant.domain.model.Mood
import com.anujstha.lifeassistant.domain.model.TransactionType
import com.anujstha.lifeassistant.domain.usecase.ReportMath
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneId

data class CategorySlice(val name: String, val amountMinor: Long, val percent: Int)

data class ReportsUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val currency: String = "NPR",
    val slices: List<CategorySlice> = emptyList(),
    val totalExpense: Long = 0,
    /** Oldest first: (month, income, expense). */
    val trend: List<Triple<YearMonth, Long, Long>> = emptyList(),
    val tasksCompleted: Int = 0,
    val tasksCreated: Int = 0,
    val journalEntries: Int = 0,
    /** Count per recorded mood; descriptive only. */
    val moodCounts: Map<Mood, Int> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(
    private val settingsRepository: SettingsRepository,
    private val transactions: TransactionRepository,
    private val categories: ExpenseCategoryRepository,
    private val tasks: TaskRepository,
    private val journal: JournalRepository,
    private val clock: Clock,
) : ViewModel() {

    private val month = MutableStateFlow<YearMonth?>(null)

    init {
        viewModelScope.launch {
            val zone = ZoneId.of(settingsRepository.settings.first().displayZoneId)
            month.compareAndSet(null, YearMonth.now(clock.withZone(zone)))
        }
    }

    val uiState: StateFlow<ReportsUiState> = combine(month, settingsRepository.settings) { m, s -> m to s }
        .flatMapLatest { (m, settings) -> if (m == null) MutableStateFlow(ReportsUiState()) else report(m, settings.currencyCode, ZoneId.of(settings.displayZoneId)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())

    private fun report(month: YearMonth, currency: String, zone: ZoneId): Flow<ReportsUiState> {
        val start = month.atDay(1)
        val end = month.atEndOfMonth()
        val trendStart = month.minusMonths(5).atDay(1)
        return combine(
            combine(transactions.observeCategoryTotals(TransactionType.EXPENSE, currency, start, end), categories.observeAll(), ::Pair),
            transactions.observeMonthlyTotals(TransactionType.INCOME, currency, trendStart, end),
            transactions.observeMonthlyTotals(TransactionType.EXPENSE, currency, trendStart, end),
            tasks.observeTasks(),
            journal.observeEntries(JournalFilter(from = start, to = end)),
        ) { (byCategory, cats), income, expense, allTasks, entries ->
            val names = cats.associate { it.id to it.name }
            val percents = ReportMath.percentages(byCategory.map { it.totalMinor })
            val incomeByMonth = ReportMath.fillMonths(income, month, 6)
            val expenseByMonth = ReportMath.fillMonths(expense, month, 6)
            fun inMonth(instant: java.time.Instant?) = instant != null && YearMonth.from(instant.atZone(zone)) == month
            ReportsUiState(
                loading = false,
                month = month,
                currency = currency,
                slices = byCategory.mapIndexed { i, t -> CategorySlice(names[t.categoryId].orEmpty(), t.totalMinor, percents[i]) },
                totalExpense = byCategory.sumOf { it.totalMinor },
                trend = incomeByMonth.zip(expenseByMonth) { (m, i), (_, e) -> Triple(m, i, e) },
                tasksCompleted = allTasks.count { inMonth(it.completedAt) },
                tasksCreated = allTasks.count { inMonth(it.createdAt) },
                journalEntries = entries.size,
                moodCounts = entries.mapNotNull { it.entry.mood }.groupingBy { it }.eachCount(),
            )
        }
    }

    fun previousMonth() = month.update { it?.minusMonths(1) }
    fun nextMonth() = month.update { it?.plusMonths(1) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                ReportsViewModel(c.settingsRepository, c.transactionRepository, c.expenseCategoryRepository, c.taskRepository, c.journalRepository, c.clock)
            }
        }
    }
}
