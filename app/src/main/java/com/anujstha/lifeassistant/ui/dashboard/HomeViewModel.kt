package com.anujstha.lifeassistant.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anujstha.lifeassistant.appContainer
import com.anujstha.lifeassistant.data.entity.JournalEntryEntity
import com.anujstha.lifeassistant.data.entity.ReminderEntity
import com.anujstha.lifeassistant.data.entity.TaskEntity
import com.anujstha.lifeassistant.data.preferences.SettingsRepository
import com.anujstha.lifeassistant.data.repository.BudgetRepository
import com.anujstha.lifeassistant.data.repository.JournalRepository
import com.anujstha.lifeassistant.data.repository.ReminderRepository
import com.anujstha.lifeassistant.data.repository.TaskRepository
import com.anujstha.lifeassistant.data.repository.TransactionRepository
import com.anujstha.lifeassistant.domain.model.TaskStatus
import com.anujstha.lifeassistant.domain.model.TimeFormat
import com.anujstha.lifeassistant.domain.model.TransactionType
import com.anujstha.lifeassistant.domain.usecase.BudgetStatus
import com.anujstha.lifeassistant.domain.usecase.FinanceRules
import com.anujstha.lifeassistant.domain.usecase.TaskRules
import com.anujstha.lifeassistant.domain.usecase.TaskSort
import com.anujstha.lifeassistant.domain.usecase.TaskView
import com.anujstha.lifeassistant.util.minuteTicks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

data class UpcomingReminder(val reminder: ReminderEntity, val at: ZonedDateTime)

data class MoneySnapshot(
    val currency: String = "NPR",
    val spentToday: Long = 0,
    val incomeMonth: Long = 0,
    val expenseMonth: Long = 0,
    val budget: BudgetStatus? = null,
)

/** Everything on the home dashboard, all derived from database records. */
data class HomeUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
    /** Due today (open first); done + pending counts exclude cancelled tasks. */
    val tasksToday: List<TaskEntity> = emptyList(),
    val doneToday: Int = 0,
    val pendingToday: Int = 0,
    val overdue: List<TaskEntity> = emptyList(),
    val upcomingReminders: List<UpcomingReminder> = emptyList(),
    val firedReminders: Int = 0,
    val recentJournal: List<JournalEntryEntity> = emptyList(),
    val money: MoneySnapshot = MoneySnapshot(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    settingsRepository: SettingsRepository,
    tasks: TaskRepository,
    reminders: ReminderRepository,
    journal: JournalRepository,
    transactions: TransactionRepository,
    budgets: BudgetRepository,
    clock: Clock,
) : ViewModel() {

    private val settings = settingsRepository.settings
    private val zone = settings.map { ZoneId.of(it.displayZoneId) }.distinctUntilChanged()

    /** Wall-clock now in the display zone, refreshed every minute (so "overdue" and "today" stay current). */
    private val now: Flow<LocalDateTime> = zone.flatMapLatest { z -> minuteTicks(clock).map { LocalDateTime.ofInstant(it, z) } }
    private val today: Flow<LocalDate> = now.map { it.toLocalDate() }.distinctUntilChanged()

    private data class TaskPart(val today: List<TaskEntity>, val done: Int, val pending: Int, val overdue: List<TaskEntity>)

    private val taskPart: Flow<TaskPart> = combine(tasks.observeTasks(), now) { all, now ->
        val dueToday = all.filter { TaskRules.matchesView(it, TaskView.TODAY, now) }
            .sortedWith(TaskRules.comparator(TaskSort.DUE_DATE, TaskView.TODAY))
        TaskPart(
            today = dueToday,
            done = dueToday.count { it.status == TaskStatus.COMPLETED },
            pending = dueToday.count { it.status.isOpen },
            overdue = all.filter { TaskRules.isOverdue(it, now) }.sortedWith(TaskRules.comparator(TaskSort.DUE_DATE, TaskView.OVERDUE)),
        )
    }

    private val reminderPart: Flow<Pair<List<UpcomingReminder>, Int>> = combine(
        reminders.observeAll(), reminders.observeActiveEventCount(), zone, minuteTicks(clock),
    ) { all, fired, z, instant ->
        val upcoming = all
            .filter { it.enabled && it.nextTriggerAt?.isAfter(instant) == true }
            .sortedBy { it.nextTriggerAt }
            .take(3)
            .map { UpcomingReminder(it, it.nextTriggerAt!!.atZone(z)) }
        upcoming to fired
    }

    private val money: Flow<MoneySnapshot> = combine(settings.map { it.currencyCode }.distinctUntilChanged(), today, ::Pair)
        .flatMapLatest { (currency, day) ->
            val (monthStart, monthEnd) = FinanceRules.monthOf(day)
            combine(
                transactions.observeTotal(TransactionType.EXPENSE, currency, day, day),
                transactions.observeTotal(TransactionType.INCOME, currency, monthStart, monthEnd),
                transactions.observeTotal(TransactionType.EXPENSE, currency, monthStart, monthEnd),
                budgets.observeEffective(YearMonth.from(day)),
            ) { spentToday, income, expense, effective ->
                val overall = effective.firstOrNull { it.categoryId == null && it.currencyCode == currency }
                MoneySnapshot(currency, spentToday, income, expense, overall?.let { BudgetStatus(it.limitMinor, expense, it.warnAtPercent) })
            }
        }

    val uiState: StateFlow<HomeUiState> = combine(
        combine(today, settings.map { it.timeFormat }, ::Pair),
        taskPart,
        reminderPart,
        journal.observeRecent(3),
        money,
    ) { (day, timeFormat), t, (upcoming, fired), recent, money ->
        HomeUiState(
            loading = false,
            today = day,
            timeFormat = timeFormat,
            tasksToday = t.today,
            doneToday = t.done,
            pendingToday = t.pending,
            overdue = t.overdue,
            upcomingReminders = upcoming,
            firedReminders = fired,
            recentJournal = recent,
            money = money,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                HomeViewModel(
                    c.settingsRepository, c.taskRepository, c.reminderRepository, c.journalRepository,
                    c.transactionRepository, c.budgetRepository, c.clock,
                )
            }
        }
    }
}
