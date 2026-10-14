package com.anujstha.lifeassistant.data.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.anujstha.lifeassistant.domain.model.TransactionType
import com.anujstha.lifeassistant.util.IdGenerator
import java.time.Clock

/** Categories created on first launch. Users can rename, archive or add their own. */
object DefaultData {
    val expenseCategories = listOf(
        "Food & Dining", "Groceries", "Transport", "Rent & Housing", "Utilities",
        "Health", "Education", "Shopping", "Entertainment", "Other",
    )
    val incomeCategories = listOf("Salary", "Business", "Gifts", "Other income")
    val taskCategories = listOf("Personal", "Work", "Home", "Errands")
}

/** Seeds [DefaultData] once, when the database file is first created. */
internal class DefaultDataCallback(
    private val clock: Clock,
    private val ids: IdGenerator,
) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        val now = clock.millis()
        db.beginTransaction()
        try {
            fun category(name: String, type: TransactionType) = db.execSQL(
                "INSERT INTO expense_categories (id, name, type, isDefault, archivedAt, createdAt, updatedAt) " +
                    "VALUES (?, ?, ?, 1, NULL, ?, ?)",
                arrayOf(ids.newId(), name, type.name, now, now),
            )
            DefaultData.expenseCategories.forEach { category(it, TransactionType.EXPENSE) }
            DefaultData.incomeCategories.forEach { category(it, TransactionType.INCOME) }
            DefaultData.taskCategories.forEach {
                db.execSQL(
                    "INSERT INTO task_categories (id, name, archivedAt, createdAt, updatedAt) VALUES (?, ?, NULL, ?, ?)",
                    arrayOf(ids.newId(), it, now, now),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
