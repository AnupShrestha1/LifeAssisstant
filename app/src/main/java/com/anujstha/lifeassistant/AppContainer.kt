package com.anujstha.lifeassistant

import android.content.Context
import com.anujstha.lifeassistant.data.backup.BackupManager
import com.anujstha.lifeassistant.data.database.AppDatabase
import com.anujstha.lifeassistant.data.export.TransactionExporter
import com.anujstha.lifeassistant.data.preferences.SettingsRepository
import com.anujstha.lifeassistant.data.preferences.settingsDataStore
import com.anujstha.lifeassistant.data.repository.BudgetRepository
import com.anujstha.lifeassistant.data.repository.DataResetter
import com.anujstha.lifeassistant.data.repository.ExpenseCategoryRepository
import com.anujstha.lifeassistant.data.repository.JournalRepository
import com.anujstha.lifeassistant.data.repository.ReminderRepository
import com.anujstha.lifeassistant.data.repository.TaskRepository
import com.anujstha.lifeassistant.data.repository.TransactionRepository
import com.anujstha.lifeassistant.notifications.AlarmManagerReminderScheduler
import com.anujstha.lifeassistant.notifications.ReminderNotifier
import com.anujstha.lifeassistant.notifications.ReminderScheduler
import com.anujstha.lifeassistant.util.IdGenerator
import java.time.Clock

/**
 * Manual dependency container, created once by [LifeAssistantApp].
 * Holds app-scoped singletons; ViewModels get their dependencies from here via factories.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Single source of "now" so time-dependent logic can be tested with a fixed clock. */
    val clock: Clock = Clock.systemUTC()
    val ids: IdGenerator = IdGenerator.Random

    val settingsRepository: SettingsRepository = SettingsRepository(appContext.settingsDataStore)

    /** Opened lazily on first use, off the main thread (Room runs queries on its own executors). */
    val database: AppDatabase by lazy { AppDatabase.build(appContext, clock, ids) }

    val journalRepository by lazy { JournalRepository(database.journalDao(), clock, ids) }
    val expenseCategoryRepository by lazy { ExpenseCategoryRepository(database, clock, ids) }
    val transactionRepository by lazy { TransactionRepository(database, clock, ids) }
    val budgetRepository by lazy { BudgetRepository(database, clock, ids) }
    val reminderScheduler: ReminderScheduler by lazy { AlarmManagerReminderScheduler(appContext) }
    val reminderNotifier by lazy { ReminderNotifier(appContext) }
    val reminderRepository by lazy { ReminderRepository(database, reminderScheduler, clock, ids) }
    val taskRepository by lazy { TaskRepository(database, reminderScheduler, clock, ids) }
    val transactionExporter by lazy {
        TransactionExporter(transactionRepository, expenseCategoryRepository, appContext.contentResolver)
    }
    val backupManager by lazy {
        BackupManager(
            database, reminderScheduler, reminderNotifier::cancelAll, settingsRepository, clock,
            appContext.contentResolver, BuildConfig.VERSION_NAME,
        )
    }
    val dataResetter by lazy {
        DataResetter(database, reminderScheduler, reminderNotifier::cancelAll, settingsRepository, clock, ids)
    }
}
