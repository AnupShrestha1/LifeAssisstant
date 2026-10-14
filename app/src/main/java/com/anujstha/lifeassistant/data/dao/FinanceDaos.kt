package com.anujstha.lifeassistant.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.anujstha.lifeassistant.data.entity.BudgetEntity
import com.anujstha.lifeassistant.data.entity.CategoryTotal
import com.anujstha.lifeassistant.data.entity.ExpenseCategoryEntity
import com.anujstha.lifeassistant.data.entity.MonthTotal
import com.anujstha.lifeassistant.data.entity.TransactionEntity
import com.anujstha.lifeassistant.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

@Dao
interface ExpenseCategoryDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: ExpenseCategoryEntity)

    @Update
    suspend fun update(category: ExpenseCategoryEntity)

    @Query("SELECT * FROM expense_categories WHERE id = :id")
    suspend fun get(id: String): ExpenseCategoryEntity?

    /** Case-insensitive name lookup within a type, including archived categories. */
    @Query("SELECT * FROM expense_categories WHERE name = :name AND type = :type LIMIT 1")
    suspend fun findByName(name: String, type: TransactionType): ExpenseCategoryEntity?

    @Query("SELECT * FROM expense_categories WHERE type = :type AND archivedAt IS NULL ORDER BY name")
    fun observeActive(type: TransactionType): Flow<List<ExpenseCategoryEntity>>

    /** All categories, archived included, so history can still show their names. */
    @Query("SELECT * FROM expense_categories ORDER BY type, name")
    fun observeAll(): Flow<List<ExpenseCategoryEntity>>

    @Query("UPDATE expense_categories SET archivedAt = :archivedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: String, archivedAt: Instant?, updatedAt: Instant): Int

    /** Fails with a constraint error if transactions still reference the category. */
    @Query("DELETE FROM expense_categories WHERE id = :id")
    suspend fun delete(id: String): Int
}

@Dao
interface TransactionDao {

    @Upsert
    suspend fun upsert(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observe(id: String): Flow<TransactionEntity?>

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: String): Int

    /** History, newest first. Null filters are ignored; [query] must already be LIKE-escaped. */
    @Query(
        """
        SELECT * FROM transactions
        WHERE (:type IS NULL OR type = :type)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:from IS NULL OR date >= :from)
          AND (:to IS NULL OR date <= :to)
          AND (:query IS NULL OR note LIKE '%' || :query || '%' ESCAPE '\')
        ORDER BY date DESC, COALESCE(time, -1) DESC, createdAt DESC
        """,
    )
    fun observeFiltered(
        type: TransactionType?,
        categoryId: String?,
        from: LocalDate?,
        to: LocalDate?,
        query: String?,
    ): Flow<List<TransactionEntity>>

    /** Sum of one type in one currency over an inclusive date range; 0 when empty. */
    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0) FROM transactions
        WHERE type = :type AND currencyCode = :currency AND date BETWEEN :from AND :to
        """,
    )
    fun observeTotal(type: TransactionType, currency: String, from: LocalDate, to: LocalDate): Flow<Long>

    @Query(
        """
        SELECT categoryId, SUM(amountMinor) AS totalMinor FROM transactions
        WHERE type = :type AND currencyCode = :currency AND date BETWEEN :from AND :to
        GROUP BY categoryId
        ORDER BY totalMinor DESC
        """,
    )
    fun observeCategoryTotals(
        type: TransactionType,
        currency: String,
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<CategoryTotal>>

    /** Per-month totals ("yyyy-MM"), oldest first. Months with no rows are omitted. */
    @Query(
        """
        SELECT substr(date, 1, 7) AS yearMonth, SUM(amountMinor) AS totalMinor FROM transactions
        WHERE type = :type AND currencyCode = :currency AND date BETWEEN :from AND :to
        GROUP BY yearMonth
        ORDER BY yearMonth
        """,
    )
    fun observeMonthlyTotals(
        type: TransactionType,
        currency: String,
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<MonthTotal>>

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countForCategory(categoryId: String): Int

    /** How many transactions are recorded in a currency other than [currency] (excluded from its totals). */
    @Query("SELECT COUNT(*) FROM transactions WHERE currencyCode != :currency")
    fun observeCountNotIn(currency: String): Flow<Int>

    /** Currencies present in the data, so reports can flag records not in the current currency. */
    @Query("SELECT DISTINCT currencyCode FROM transactions ORDER BY currencyCode")
    fun observeCurrencies(): Flow<List<String>>
}

@Dao
interface BudgetDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(budget: BudgetEntity)

    @Update
    suspend fun update(budget: BudgetEntity)

    /** `IS` (not `=`) so a null [categoryId] matches the overall budget row. */
    @Query("SELECT * FROM budgets WHERE yearMonth = :yearMonth AND categoryId IS :categoryId LIMIT 1")
    suspend fun findForScope(yearMonth: YearMonth, categoryId: String?): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE yearMonth = :yearMonth")
    fun observeForMonth(yearMonth: YearMonth): Flow<List<BudgetEntity>>

    /** Every budget set in or before [yearMonth] ("yyyy-MM" text compares chronologically). */
    @Query("SELECT * FROM budgets WHERE yearMonth <= :yearMonth ORDER BY yearMonth DESC")
    fun observeUpTo(yearMonth: YearMonth): Flow<List<BudgetEntity>>

    @Query("SELECT COUNT(*) FROM budgets WHERE yearMonth < :yearMonth AND categoryId IS :categoryId")
    suspend fun countBefore(yearMonth: YearMonth, categoryId: String?): Int

    @Query("DELETE FROM budgets WHERE yearMonth = :yearMonth AND categoryId IS :categoryId")
    suspend fun deleteForScope(yearMonth: YearMonth, categoryId: String?): Int

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun delete(id: String): Int

    /**
     * Inserts, or updates the existing budget for the same month and scope (keeping its id and
     * createdAt). Returns the stored row. This is what keeps (yearMonth, categoryId) unique.
     */
    @Transaction
    suspend fun upsertForScope(budget: BudgetEntity): BudgetEntity {
        val existing = findForScope(budget.yearMonth, budget.categoryId)
        return if (existing == null) {
            insert(budget)
            budget
        } else {
            val merged = budget.copy(id = existing.id, createdAt = existing.createdAt)
            update(merged)
            merged
        }
    }
}
