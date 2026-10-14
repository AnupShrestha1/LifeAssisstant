package com.anujstha.lifeassistant.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anujstha.lifeassistant.R
import com.anujstha.lifeassistant.data.entity.TaskEntity
import com.anujstha.lifeassistant.domain.model.TaskStatus
import com.anujstha.lifeassistant.ui.components.ScreenScaffold
import com.anujstha.lifeassistant.ui.components.dueLabel
import com.anujstha.lifeassistant.ui.components.rememberDateFormatter
import com.anujstha.lifeassistant.ui.components.relativeDate
import com.anujstha.lifeassistant.ui.expenses.color
import com.anujstha.lifeassistant.ui.expenses.rememberMoneyFormatter
import com.anujstha.lifeassistant.ui.journal.headline
import java.time.format.FormatStyle

/** Navigation callbacks from the dashboard. */
data class HomeActions(
    val addEntry: () -> Unit,
    val addExpense: () -> Unit,
    val addTask: () -> Unit,
    val addReminder: () -> Unit,
    val openTask: (String) -> Unit,
    val openTasks: () -> Unit,
    val openReminders: () -> Unit,
    val openEntry: (String) -> Unit,
    val openJournal: () -> Unit,
    val openExpenses: () -> Unit,
    val openReports: () -> Unit,
)

@Composable
fun HomeScreen(actions: HomeActions, viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fullDate = rememberDateFormatter(FormatStyle.FULL)

    ScreenScaffold(
        title = stringResource(R.string.app_name),
        actions = {
            IconButton(onClick = actions.openReminders) {
                BadgedBox(badge = { if (state.firedReminders > 0) Badge { Text(state.firedReminders.toString()) } }) {
                    Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.home_reminders))
                }
            }
            IconButton(onClick = actions.openReports) {
                Icon(Icons.Filled.BarChart, contentDescription = stringResource(R.string.home_reports))
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.home_today), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(fullDate.format(state.today), style = MaterialTheme.typography.headlineSmall)
            }
            QuickAdd(actions)
            if (state.loading) return@Column

            TasksCard(state, actions)
            RemindersCard(state, actions)
            MoneyCard(state, actions)
            JournalCard(state, actions)
        }
    }
}

@Composable
private fun QuickAdd(actions: HomeActions) {
    Column {
        Text(
            stringResource(R.string.home_quick_add),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickChip(stringResource(R.string.home_add_entry), Icons.AutoMirrored.Filled.MenuBook, actions.addEntry)
            QuickChip(stringResource(R.string.home_add_expense), Icons.Filled.AccountBalanceWallet, actions.addExpense)
            QuickChip(stringResource(R.string.home_add_task), Icons.Filled.TaskAlt, actions.addTask)
            QuickChip(stringResource(R.string.home_add_reminder), Icons.Filled.Notifications, actions.addReminder)
        }
    }
}

@Composable
private fun QuickChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(label) }, leadingIcon = { Icon(icon, contentDescription = null) })
}

@Composable
private fun DashCard(title: String, onSeeAll: (() -> Unit)?, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (onSeeAll != null) TextButton(onClick = onSeeAll) { Text(stringResource(R.string.home_see_all)) }
            }
            content()
        }
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TasksCard(state: HomeUiState, actions: HomeActions) {
    DashCard(stringResource(R.string.home_tasks_today), actions.openTasks) {
        if (state.tasksToday.isEmpty()) {
            Muted(stringResource(R.string.home_tasks_none_today))
        } else {
            Muted(stringResource(R.string.home_tasks_counts, state.doneToday, state.pendingToday))
            state.tasksToday.take(5).forEach { TaskLine(it, state, actions) }
        }
        if (state.overdue.isNotEmpty()) {
            Text(
                "${stringResource(R.string.home_overdue)} (${state.overdue.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
            state.overdue.take(3).forEach { TaskLine(it, state, actions, overdue = true) }
        }
    }
}

@Composable
private fun TaskLine(task: TaskEntity, state: HomeUiState, actions: HomeActions, overdue: Boolean = false) {
    val closed = !task.status.isOpen
    val due = task.dueDate?.let { dueLabel(it, task.dueTime, state.today, state.timeFormat) }
    Text(
        text = task.title + if (due != null && (overdue || task.dueTime != null)) " · $due" else "",
        style = MaterialTheme.typography.bodyMedium,
        textDecoration = if (task.status == TaskStatus.COMPLETED) TextDecoration.LineThrough else null,
        color = when {
            overdue -> MaterialTheme.colorScheme.error
            closed -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurface
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().clickable { actions.openTask(task.id) }.padding(vertical = 6.dp),
    )
}

@Composable
private fun RemindersCard(state: HomeUiState, actions: HomeActions) {
    DashCard(stringResource(R.string.home_upcoming_reminders), actions.openReminders) {
        if (state.firedReminders > 0) {
            Text(
                stringResource(R.string.home_reminders_fired, state.firedReminders),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (state.upcomingReminders.isEmpty()) {
            Muted(stringResource(R.string.home_no_reminders))
        }
        state.upcomingReminders.forEach { r ->
            Text(
                "${r.reminder.title} · ${dueLabel(r.at.toLocalDate(), r.at.toLocalTime(), state.today, state.timeFormat)}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clickable(onClick = actions.openReminders).padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun MoneyCard(state: HomeUiState, actions: HomeActions) {
    val money = rememberMoneyFormatter()
    val m = state.money
    DashCard(stringResource(R.string.home_money), actions.openExpenses) {
        Row(Modifier.fillMaxWidth().clickable(onClick = actions.openExpenses)) {
            Figure(stringResource(R.string.expenses_income), money(m.incomeMonth, m.currency), Modifier.weight(1f))
            Figure(stringResource(R.string.expenses_expenses), money(m.expenseMonth, m.currency), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Figure(stringResource(R.string.home_spent_today), money(m.spentToday, m.currency), Modifier.weight(1f))
            val budget = m.budget
            if (budget == null) {
                Figure(stringResource(R.string.home_budget_left), stringResource(R.string.home_no_budget), Modifier.weight(1f))
            } else {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_budget_left), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        (if (budget.remainingMinor < 0) "−" else "") + money(kotlin.math.abs(budget.remainingMinor), m.currency) + " (${budget.percentUsed}%)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = budget.level.color(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun JournalCard(state: HomeUiState, actions: HomeActions) {
    DashCard(stringResource(R.string.home_recent_journal), actions.openJournal) {
        if (state.recentJournal.isEmpty()) Muted(stringResource(R.string.home_no_journal))
        state.recentJournal.forEach { e ->
            Column(Modifier.fillMaxWidth().clickable { actions.openEntry(e.id) }.padding(vertical = 6.dp)) {
                Text(e.headline() ?: stringResource(R.string.journal_untitled), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(relativeDate(e.entryDate, state.today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
