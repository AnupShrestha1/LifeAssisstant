package com.anujstha.lifeassistant.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.anujstha.lifeassistant.ui.dashboard.HomeActions
import com.anujstha.lifeassistant.ui.dashboard.HomeScreen
import com.anujstha.lifeassistant.ui.reports.ReportsScreen
import com.anujstha.lifeassistant.ui.expenses.BudgetsScreen
import com.anujstha.lifeassistant.ui.expenses.CategoriesScreen
import com.anujstha.lifeassistant.ui.expenses.ExpensesScreen
import com.anujstha.lifeassistant.ui.expenses.TransactionEditScreen
import com.anujstha.lifeassistant.ui.journal.JournalEditScreen
import com.anujstha.lifeassistant.ui.journal.JournalEntryScreen
import com.anujstha.lifeassistant.ui.journal.JournalScreen
import com.anujstha.lifeassistant.ui.reminders.ReminderEditScreen
import com.anujstha.lifeassistant.ui.reminders.RemindersScreen
import com.anujstha.lifeassistant.ui.settings.AboutScreen
import com.anujstha.lifeassistant.ui.settings.SettingsActions
import com.anujstha.lifeassistant.ui.settings.SettingsScreen
import com.anujstha.lifeassistant.ui.tasks.TaskCategoriesScreen
import com.anujstha.lifeassistant.ui.tasks.TaskEditScreen
import com.anujstha.lifeassistant.ui.tasks.TasksScreen

/**
 * App shell: adaptive navigation (bottom bar on phones, rail on wider screens) around the NavHost.
 */
@Composable
fun AppNavigation(
    openReminders: Boolean = false,
    onRemindersOpened: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    // A tapped reminder notification asks to show the reminders screen.
    LaunchedEffect(openReminders) {
        if (openReminders) {
            navController.navigate(RemindersRoute) { launchSingleTop = true }
            onRemindersOpened()
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // Detail screens (editors) take the full screen; the navigation bar shows on top-level screens only.
    val onTopLevel = currentDestination == null || TopLevelDestination.entries.any { currentDestination.isOn(it) }
    val adaptiveLayout = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())

    NavigationSuiteScaffold(
        layoutType = if (onTopLevel) adaptiveLayout else NavigationSuiteType.None,
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                val selected = currentDestination.isOn(destination)
                item(
                    selected = selected,
                    onClick = { navController.navigateToTopLevel(destination) },
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(destination.labelRes)) },
                )
            }
        },
    ) {
        NavHost(navController = navController, startDestination = HomeRoute) {
            composable<HomeRoute> {
                HomeScreen(
                    HomeActions(
                        addEntry = { navController.navigate(JournalEditRoute()) },
                        addExpense = { navController.navigate(TransactionEditRoute()) },
                        addTask = { navController.navigate(TaskEditRoute()) },
                        addReminder = { navController.navigate(ReminderEditRoute()) },
                        openTask = { navController.navigate(TaskEditRoute(it)) },
                        openTasks = { navController.navigateToTopLevel(TopLevelDestination.TASKS) },
                        openReminders = { navController.navigate(RemindersRoute) },
                        openEntry = { navController.navigate(JournalEntryRoute(it)) },
                        openJournal = { navController.navigateToTopLevel(TopLevelDestination.JOURNAL) },
                        openExpenses = { navController.navigateToTopLevel(TopLevelDestination.EXPENSES) },
                        openReports = { navController.navigate(ReportsRoute) },
                    ),
                )
            }
            composable<ReportsRoute> { ReportsScreen(onBack = { navController.popBackStack() }) }
            composable<JournalRoute> {
                JournalScreen(
                    onOpenEntry = { navController.navigate(JournalEntryRoute(it)) },
                    onNewEntry = { date -> navController.navigate(JournalEditRoute(date = date?.toString())) },
                )
            }
            composable<JournalEntryRoute> {
                JournalEntryScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(JournalEditRoute(entryId = it)) },
                )
            }
            composable<JournalEditRoute> { JournalEditScreen(onDone = { navController.popBackStack() }) }
            composable<ExpensesRoute> {
                ExpensesScreen(
                    onOpenTransaction = { navController.navigate(TransactionEditRoute(it)) },
                    onNewTransaction = { navController.navigate(TransactionEditRoute()) },
                    onOpenCategories = { navController.navigate(ExpenseCategoriesRoute) },
                    onOpenBudgets = { navController.navigate(BudgetsRoute) },
                    onOpenReports = { navController.navigate(ReportsRoute) },
                )
            }
            composable<TransactionEditRoute> { TransactionEditScreen(onDone = { navController.popBackStack() }) }
            composable<ExpenseCategoriesRoute> { CategoriesScreen(onBack = { navController.popBackStack() }) }
            composable<BudgetsRoute> { BudgetsScreen(onBack = { navController.popBackStack() }) }
            composable<TasksRoute> {
                TasksScreen(
                    onOpenTask = { navController.navigate(TaskEditRoute(it)) },
                    onNewTask = { navController.navigate(TaskEditRoute()) },
                )
            }
            composable<TaskEditRoute> {
                TaskEditScreen(
                    onDone = { navController.popBackStack() },
                    onOpenReminder = { navController.navigate(ReminderEditRoute(reminderId = it)) },
                    onAddReminder = { navController.navigate(ReminderEditRoute(taskId = it)) },
                )
            }
            composable<RemindersRoute> {
                RemindersScreen(
                    onBack = { navController.popBackStack() },
                    onOpenReminder = { navController.navigate(ReminderEditRoute(reminderId = it)) },
                    onNewReminder = { navController.navigate(ReminderEditRoute()) },
                )
            }
            composable<ReminderEditRoute> { ReminderEditScreen(onDone = { navController.popBackStack() }) }
            composable<SettingsRoute> {
                SettingsScreen(
                    SettingsActions(
                        openExpenseCategories = { navController.navigate(ExpenseCategoriesRoute) },
                        openTaskCategories = { navController.navigate(TaskCategoriesRoute) },
                        openBudgets = { navController.navigate(BudgetsRoute) },
                        openAbout = { navController.navigate(AboutRoute) },
                    ),
                )
            }
            composable<TaskCategoriesRoute> { TaskCategoriesScreen(onBack = { navController.popBackStack() }) }
            composable<AboutRoute> { AboutScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

private fun NavDestination?.isOn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true

/** Switch tabs while keeping one copy of each tab and preserving each tab's back stack/state. */
private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
