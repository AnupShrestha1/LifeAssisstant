package com.anujstha.lifeassistant.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anujstha.lifeassistant.R
import com.anujstha.lifeassistant.domain.model.Mood
import com.anujstha.lifeassistant.ui.components.DonutChart
import com.anujstha.lifeassistant.ui.components.GroupedBarChart
import com.anujstha.lifeassistant.ui.components.LegendDot
import com.anujstha.lifeassistant.ui.components.ScreenScaffold
import com.anujstha.lifeassistant.ui.components.chartPalette
import com.anujstha.lifeassistant.ui.expenses.rememberMoneyFormatter
import com.anujstha.lifeassistant.ui.journal.labelRes
import java.time.format.DateTimeFormatter

@Composable
fun ReportsScreen(onBack: () -> Unit, viewModel: ReportsViewModel = viewModel(factory = ReportsViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val monthTitle = remember(state.month, locale) { DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(state.month) }
    val shortMonth = remember(locale) { DateTimeFormatter.ofPattern("LLL", locale) }
    val money = rememberMoneyFormatter()
    val palette = chartPalette()

    ScreenScaffold(title = stringResource(R.string.reports_title), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::previousMonth) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.calendar_previous_month))
                }
                Text(monthTitle, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                IconButton(onClick = viewModel::nextMonth) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.calendar_next_month))
                }
            }
            if (state.loading) return@Column

            Section(stringResource(R.string.reports_spending_by_category)) {
                if (state.slices.isEmpty()) {
                    Text(stringResource(R.string.reports_no_spending), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        DonutChart(
                            values = state.slices.map { it.amountMinor },
                            colors = palette,
                            description = stringResource(R.string.reports_breakdown_desc),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(money(state.totalExpense, state.currency), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.expenses_expenses), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    state.slices.forEachIndexed { i, s ->
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            LegendDot(palette[i % palette.size], s.name, Modifier.weight(1f))
                            Text("${money(s.amountMinor, state.currency)} · ${s.percent}%", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Section(stringResource(R.string.reports_trend)) {
                val incomeColor = MaterialTheme.colorScheme.primary
                val expenseColor = MaterialTheme.colorScheme.tertiary
                GroupedBarChart(
                    labels = state.trend.map { shortMonth.format(it.first) },
                    series = listOf(state.trend.map { it.second }, state.trend.map { it.third }),
                    colors = listOf(incomeColor, expenseColor),
                    description = stringResource(R.string.reports_trend_desc),
                )
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendDot(incomeColor, stringResource(R.string.expenses_income))
                    LegendDot(expenseColor, stringResource(R.string.expenses_expenses))
                }
                state.trend.reversed().forEach { (m, income, expense) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(shortMonth.format(m) + " " + m.year, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(
                            "+${money(income, state.currency)}  −${money(expense, state.currency)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Section(stringResource(R.string.reports_tasks)) {
                Text(stringResource(R.string.reports_tasks_completed, state.tasksCompleted))
                Text(stringResource(R.string.reports_tasks_created, state.tasksCreated))
            }

            Section(stringResource(R.string.reports_journal)) {
                Text(stringResource(R.string.reports_journal_entries, state.journalEntries))
                Text(stringResource(R.string.reports_moods), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                if (state.moodCounts.isEmpty()) {
                    Text(stringResource(R.string.reports_no_moods), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Mood.entries.filter { it in state.moodCounts }.forEach { mood ->
                        Text("${stringResource(mood.labelRes())}: ${state.moodCounts.getValue(mood)}")
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}
