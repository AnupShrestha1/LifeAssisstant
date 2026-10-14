package com.anujstha.lifeassistant.data.repository

import androidx.room.withTransaction
import com.anujstha.lifeassistant.data.database.AppDatabase
import com.anujstha.lifeassistant.data.entity.BudgetEntity
import com.anujstha.lifeassistant.data.entity.CategoryTotal
import com.anujstha.lifeassistant.data.entity.ExpenseCategoryEntity
import com.anujstha.lifeassistant.data.entity.MonthTotal
import com.anujstha.lifeassistant.data.entity.TransactionEntity
import com.anujstha.lifeassistant.data.preferences.SettingsRepository
import com.anujstha.lifeassistant.domain.model.PaymentMethod
import com.anujstha.lifeassistant.domain.model.TransactionType
import com.anujstha.lifeassistant.domain.model.validate
import com.anujstha.lifeassistant.util.IdGenerator
import com.anujstha.lifeassistant.util.escapeLike
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** Outcome of removing a category: deleted outright, or archived because transactions use it. */
sealed interface CategoryRemoval {
    data object Deleted : CategoryRemoval
    data class Archived(val transactionCount: Int) : CategoryRemoval
}

class ExpenseCategoryRepository(
    private val db: AppDatabase,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    private val dao = db.expenseCategoryDao()

    fun observeActive(type: TransactionType): Flow<List<ExpenseCategoryEntity>> = dao.observeActive(type)

    fun observeAll(): Flow<List<ExpenseCategoryEntity>> = dao.observeAll()

    /** Creating a name that matches an archived category restores that category instead. */
    suspend fun create(name: String, type: TransactionType): ExpenseCategoryEntity = db.withTransaction {
        val clean = normalizeName(name)
        val now = clock.instant()
        val existing = dao.findByName(clean, type)
        if (existing != null) {
            validate(existing.archivedAt != null) { "A category named \"$clean\" already exists" }
            dao.setArchived(existing.id, archivedAt = null, updatedAt = now)
            return@withTransaction existing.copy(archivedAt = null, updatedAt = now)
        }
        ExpenseCategoryEntity(
            id = ids.newId(), name = clean, type = type, isDefault = false,
            archivedAt = null, createdAt = now, updatedAt = now,
        ).also { dao.insert(it) }
    }

    suspend fun rename(id: String, name: String) = db.withTransaction {
        val category = dao.get(id) ?: throw NoSuchElementException("Category not found")
        val clean = normalizeName(name)
        val clash = dao.findByName(clean, category.type)
        validate(clash == null || clash.id == id) { "A category named \"$clean\" already exists" }
        dao.update(category.copy(name = clean, updatedAt = clock.instant()))
    }

    /**
     * Deletes an unused category (its budgets go with it). A category that transactions use is
     * archived instead: hidden from pickers, but history keeps its name and totals stay correct.
     */
    suspend fun remove(id: String): CategoryRemoval = db.withTransaction {
        val used = db.transactionDao().countForCategory(id)
        if (used == 0) {
            dao.delete(id)
            CategoryRemoval.Deleted
        } else {
            dao.setArchived(id, archivedAt = clock.instant(), updatedAt = clock.instant())
            CategoryRemoval.Archived(used)
        }
    }

    private fun normalizeName(name: String): String {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        validate(clean.isNotEmpty()) { "Category name can't be empty" }
        validate(clean.length <= MAX_NAME) { "Category name is too long (max $MAX_NAME characters)" }
        return clean
    }

    companion object {
        const val MAX_NAME = 40
    }
}

data class TransactionInput(
    val amountMinor: Long,
    val currencyCode: String,
    val type: TransactionType,
    val categoryId: String,
    val date: LocalDate,
    val time: LocalTime? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val note: String? = null,
)

data class TransactionFilter(
    val type: TransactionType? = null,
    val categoryId: String? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val query: String? = null,
)

class TransactionRepository(
    private val db: AppDatabase,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    private val dao = db.transactionDao()
    private val categoryDao = db.expenseCategoryDao()

    fun observe(id: String): Flow<TransactionEntity?> = dao.observe(id)

    fun observeFiltered(filter: TransactionFilter = TransactionFilter()): Flow<List<TransactionEntity>> =
        dao.observeFiltered(
            type = filter.type,
            categoryId = filter.categoryId,
            from = filter.from,
            to = filter.to,
            query = filter.query?.trim()?.takeIf { it.isNotEmpty() }?.let(::escapeLike),
        )

    fun observeTotal(type: TransactionType, currency: String, from: LocalDate, to: LocalDate): Flow<Long> =
        dao.observeTotal(type, currency, from, to)

    fun observeCategoryTotals(
        type: TransactionType,
        currency: String,
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<CategoryTotal>> = dao.observeCategoryTotals(type, currency, from, to)

    fun observeMonthlyTotals(
        type: TransactionType,
        currency: String,
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<MonthTotal>> = dao.observeMonthlyTotals(type, currency, from, to)

    fun observeCurrencies(): Flow<List<String>> = dao.observeCurrencies()

    /**
     * Creates ([id] == null) or updates a transaction. Validates the amount, currency, note
     * length and that the category exists and matches the type. New transactions cannot use an
     * archived category; an existing transaction may keep the archived category it already had.
     */
    suspend fun save(id: String?, input: TransactionInput): String = db.withTransaction {
        validate(input.amountMinor > 0) { "Amount must be greater than zero" }
        validate(input.amountMinor <= MAX_AMOUNT_MINOR) { "Amount is too large" }
        validate(SettingsRepository.isValidCurrency(input.currencyCode)) { "Unknown currency" }
        val note = input.note?.trim()?.takeIf { it.isNotEmpty() }
        validate((note?.length ?: 0) <= MAX_NOTE) { "Description is too long (max $MAX_NOTE characters)" }

        val existing = id?.let { dao.get(it) ?: throw NoSuchElementException("Transaction not found") }
        val category = categoryDao.get(input.categoryId)
        validate(category != null) { "Choose a category" }
        validate(category!!.type == input.type) { "Category doesn't match the transaction type" }
        validate(category.archivedAt == null || existing?.categoryId == category.id) {
            "That category is archived"
        }

        val now = clock.instant()
        val entity = TransactionEntity(
            id = existing?.id ?: ids.newId(),
            amountMinor = input.amountMinor,
            currencyCode = input.currencyCode,
            type = input.type,
            categoryId = input.categoryId,
            date = input.date,
            time = input.time,
            paymentMethod = input.paymentMethod,
            note = note,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        dao.upsert(entity)
        entity.id
    }

    suspend fun delete(id: String): Boolean = dao.delete(id) > 0

    companion object {
        /** 1 trillion major units at 2 decimals; far beyond personal use, safe from Long overflow when summed. */
        const val MAX_AMOUNT_MINOR = 100_000_000_000_000L
        const val MAX_NOTE = 500
    }
}

class BudgetRepository(
    private val db: AppDatabase,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    private val dao = db.budgetDao()

    fun observeForMonth(yearMonth: YearMonth): Flow<List<BudgetEntity>> = dao.observeForMonth(yearMonth)

    /**
     * Sets the budget for [yearMonth]; [categoryId] null means the overall monthly budget.
     * Replaces any existing budget for the same month and scope.
     */
    suspend fun setBudget(
        yearMonth: YearMonth,
        categoryId: String?,
        limitMinor: Long,
        currencyCode: String,
        warnAtPercent: Int = DEFAULT_WARN_PERCENT,
    ): BudgetEntity = db.withTransaction {
        validate(limitMinor > 0) { "Budget must be greater than zero" }
        validate(limitMinor <= TransactionRepository.MAX_AMOUNT_MINOR) { "Budget is too large" }
        validate(warnAtPercent in 1..100) { "Warning threshold must be between 1% and 100%" }
        validate(SettingsRepository.isValidCurrency(currencyCode)) { "Unknown currency" }
        if (categoryId != null) {
            val category = db.expenseCategoryDao().get(categoryId)
            validate(category?.type == TransactionType.EXPENSE) { "Budgets can only use expense categories" }
        }
        val now = clock.instant()
        dao.upsertForScope(
            BudgetEntity(
                id = ids.newId(),
                yearMonth = yearMonth,
                categoryId = categoryId,
                limitMinor = limitMinor,
                currencyCode = currencyCode,
                warnAtPercent = warnAtPercent,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun delete(id: String): Boolean = dao.delete(id) > 0

    companion object {
        const val DEFAULT_WARN_PERCENT = 80
    }
}
