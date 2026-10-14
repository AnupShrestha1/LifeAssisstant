package com.anujstha.lifeassistant.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.anujstha.lifeassistant.data.entity.BudgetEntity
import com.anujstha.lifeassistant.data.entity.ExpenseCategoryEntity
import com.anujstha.lifeassistant.data.entity.JournalEntryEntity
import com.anujstha.lifeassistant.data.entity.JournalEntryTagCrossRef
import com.anujstha.lifeassistant.data.entity.JournalTagEntity
import com.anujstha.lifeassistant.data.entity.ReminderEntity
import com.anujstha.lifeassistant.data.entity.TaskCategoryEntity
import com.anujstha.lifeassistant.data.entity.TaskEntity
import com.anujstha.lifeassistant.data.entity.TransactionEntity

/** Whole-table reads and insert-if-absent writes used only by backup and restore. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM journal_tags") suspend fun journalTags(): List<JournalTagEntity>
    @Query("SELECT * FROM journal_entries") suspend fun journalEntries(): List<JournalEntryEntity>
    @Query("SELECT * FROM journal_entry_tags") suspend fun journalEntryTags(): List<JournalEntryTagCrossRef>
    @Query("SELECT * FROM expense_categories") suspend fun expenseCategories(): List<ExpenseCategoryEntity>
    @Query("SELECT * FROM transactions") suspend fun transactions(): List<TransactionEntity>
    @Query("SELECT * FROM budgets") suspend fun budgets(): List<BudgetEntity>
    @Query("SELECT * FROM task_categories") suspend fun taskCategories(): List<TaskCategoryEntity>
    @Query("SELECT * FROM tasks") suspend fun tasks(): List<TaskEntity>
    @Query("SELECT * FROM reminders") suspend fun reminders(): List<ReminderEntity>

    // Each returns the new rowid, or -1 when a row with the same primary key / unique key exists.
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(tag: JournalTagEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(entry: JournalEntryEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(link: JournalEntryTagCrossRef): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(category: ExpenseCategoryEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(transaction: TransactionEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(budget: BudgetEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(category: TaskCategoryEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(task: TaskEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(reminder: ReminderEntity): Long

    @Query("DELETE FROM reminder_events") suspend fun deleteReminderEvents()
    @Query("DELETE FROM reminders") suspend fun deleteReminders()
    @Query("DELETE FROM tasks") suspend fun deleteTasks()
    @Query("DELETE FROM task_categories") suspend fun deleteTaskCategories()
    @Query("DELETE FROM journal_entry_tags") suspend fun deleteJournalEntryTags()
    @Query("DELETE FROM journal_tags") suspend fun deleteJournalTags()
    @Query("DELETE FROM journal_entries") suspend fun deleteJournalEntries()
    @Query("DELETE FROM budgets") suspend fun deleteBudgets()
    @Query("DELETE FROM transactions") suspend fun deleteTransactions()
    @Query("DELETE FROM expense_categories") suspend fun deleteExpenseCategories()

    /** Deletes every row, children before parents so no foreign key ever points at a deleted row. Call inside a transaction. */
    suspend fun deleteAll() {
        deleteReminderEvents()
        deleteReminders()
        deleteTasks()
        deleteTaskCategories()
        deleteJournalEntryTags()
        deleteJournalTags()
        deleteJournalEntries()
        deleteBudgets()
        deleteTransactions()
        deleteExpenseCategories()
    }
}
