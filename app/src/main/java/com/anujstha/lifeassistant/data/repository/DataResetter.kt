package com.anujstha.lifeassistant.data.repository

import androidx.room.withTransaction
import com.anujstha.lifeassistant.data.database.AppDatabase
import com.anujstha.lifeassistant.data.database.DefaultData
import com.anujstha.lifeassistant.data.entity.ExpenseCategoryEntity
import com.anujstha.lifeassistant.data.entity.TaskCategoryEntity
import com.anujstha.lifeassistant.data.preferences.SettingsRepository
import com.anujstha.lifeassistant.domain.model.TransactionType
import com.anujstha.lifeassistant.notifications.ReminderScheduler
import com.anujstha.lifeassistant.util.IdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Clock

/**
 * "Clear all data": removes every journal entry, tag, task, reminder, transaction, budget and
 * custom category; cancels all scheduled alarms and shown notifications; resets preferences; then
 * restores the default categories so the app is as on first launch. Irreversible.
 */
class DataResetter(
    private val db: AppDatabase,
    private val scheduler: ReminderScheduler,
    private val cancelNotifications: () -> Unit,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        // Alarms live outside the database, so cancel them before their rows disappear.
        db.reminderDao().observeAll().first().forEach(scheduler::cancel)
        cancelNotifications()

        db.withTransaction {
            // Children before parents, so no foreign key ever points at a deleted row.
            db.openHelper.writableDatabase.apply {
                execSQL("DELETE FROM reminder_events")
                execSQL("DELETE FROM reminders")
                execSQL("DELETE FROM tasks")
                execSQL("DELETE FROM task_categories")
                execSQL("DELETE FROM journal_entry_tags")
                execSQL("DELETE FROM journal_tags")
                execSQL("DELETE FROM journal_entries")
                execSQL("DELETE FROM budgets")
                execSQL("DELETE FROM transactions")
                execSQL("DELETE FROM expense_categories")
            }
            val now = clock.instant()
            DefaultData.expenseCategories.forEach {
                db.expenseCategoryDao().insert(ExpenseCategoryEntity(ids.newId(), it, TransactionType.EXPENSE, true, null, now, now))
            }
            DefaultData.incomeCategories.forEach {
                db.expenseCategoryDao().insert(ExpenseCategoryEntity(ids.newId(), it, TransactionType.INCOME, true, null, now, now))
            }
            DefaultData.taskCategories.forEach {
                db.taskCategoryDao().insert(TaskCategoryEntity(ids.newId(), it, null, now, now))
            }
        }
        settings.resetAll()
    }
}
