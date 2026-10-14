package com.anujstha.lifeassistant.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.ui.graphics.vector.ImageVector
import com.anujstha.lifeassistant.R
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable data object HomeRoute
@Serializable data object JournalRoute
@Serializable data object ExpensesRoute
@Serializable data object TasksRoute
@Serializable data object SettingsRoute

/** The primary destinations shown in the bottom bar / navigation rail. */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(HomeRoute, HomeRoute::class, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    JOURNAL(
        JournalRoute, JournalRoute::class, R.string.nav_journal,
        Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook,
    ),
    EXPENSES(
        ExpensesRoute, ExpensesRoute::class, R.string.nav_expenses,
        Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet,
    ),
    TASKS(TasksRoute, TasksRoute::class, R.string.nav_tasks, Icons.Filled.TaskAlt, Icons.Outlined.TaskAlt),
    SETTINGS(SettingsRoute, SettingsRoute::class, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
}
