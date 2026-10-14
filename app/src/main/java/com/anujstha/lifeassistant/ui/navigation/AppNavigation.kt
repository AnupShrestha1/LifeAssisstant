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
import com.anujstha.lifeassistant.R
import com.anujstha.lifeassistant.ui.components.NotBuiltYetScreen
import com.anujstha.lifeassistant.ui.dashboard.HomeScreen
import com.anujstha.lifeassistant.ui.reminders.ReminderEditScreen
import com.anujstha.lifeassistant.ui.reminders.RemindersScreen
import com.anujstha.lifeassistant.ui.settings.SettingsScreen
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
            composable<HomeRoute> { HomeScreen(onOpenReminders = { navController.navigate(RemindersRoute) }) }
            composable<JournalRoute> { NotBuiltYetScreen(R.string.nav_journal, milestone = 6) }
            composable<ExpensesRoute> { NotBuiltYetScreen(R.string.nav_expenses, milestone = 7) }
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
            composable<SettingsRoute> { SettingsScreen() }
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
