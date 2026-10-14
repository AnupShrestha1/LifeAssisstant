package com.anujstha.lifeassistant.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.anujstha.lifeassistant.data.dao.BackupDao
import com.anujstha.lifeassistant.data.dao.BudgetDao
import com.anujstha.lifeassistant.data.dao.ExpenseCategoryDao
import com.anujstha.lifeassistant.data.dao.JournalDao
import com.anujstha.lifeassistant.data.dao.ReminderDao
import com.anujstha.lifeassistant.data.dao.ReminderEventDao
import com.anujstha.lifeassistant.data.dao.TaskCategoryDao
import com.anujstha.lifeassistant.data.dao.TaskDao
import com.anujstha.lifeassistant.data.dao.TransactionDao
import com.anujstha.lifeassistant.data.entity.BudgetEntity
import com.anujstha.lifeassistant.data.entity.ExpenseCategoryEntity
import com.anujstha.lifeassistant.data.entity.JournalEntryEntity
import com.anujstha.lifeassistant.data.entity.JournalEntryTagCrossRef
import com.anujstha.lifeassistant.data.entity.JournalTagEntity
import com.anujstha.lifeassistant.data.entity.ReminderEntity
import com.anujstha.lifeassistant.data.entity.ReminderEventEntity
import com.anujstha.lifeassistant.data.entity.TaskCategoryEntity
import com.anujstha.lifeassistant.data.entity.TaskEntity
import com.anujstha.lifeassistant.data.entity.TransactionEntity
import com.anujstha.lifeassistant.util.IdGenerator
import java.time.Clock

@Database(
    entities = [
        JournalEntryEntity::class,
        JournalTagEntity::class,
        JournalEntryTagCrossRef::class,
        ExpenseCategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        TaskCategoryEntity::class,
        TaskEntity::class,
        ReminderEntity::class,
        ReminderEventEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao
    abstract fun expenseCategoryDao(): ExpenseCategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun taskCategoryDao(): TaskCategoryDao
    abstract fun taskDao(): TaskDao
    abstract fun reminderDao(): ReminderDao
    abstract fun reminderEventDao(): ReminderEventDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val VERSION = 1
        const val NAME = "life_assistant.db"

        /**
         * Builds the on-device database (app-private storage). Schema changes go through
         * [ALL_MIGRATIONS]; there is deliberately no destructive-migration fallback.
         */
        fun build(context: Context, clock: Clock, ids: IdGenerator): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*ALL_MIGRATIONS)
                .addCallback(DefaultDataCallback(clock, ids))
                .build()
    }
}
