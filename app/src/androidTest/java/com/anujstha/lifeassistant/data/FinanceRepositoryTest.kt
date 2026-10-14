package com.anujstha.lifeassistant.data

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anujstha.lifeassistant.data.database.AppDatabase
import com.anujstha.lifeassistant.data.database.DefaultData
import com.anujstha.lifeassistant.data.entity.ExpenseCategoryEntity
import com.anujstha.lifeassistant.data.entity.MonthTotal
import com.anujstha.lifeassistant.data.repository.BudgetRepository
import com.anujstha.lifeassistant.data.repository.CategoryRemoval
import com.anujstha.lifeassistant.data.repository.ExpenseCategoryRepository
import com.anujstha.lifeassistant.data.repository.TransactionFilter
import com.anujstha.lifeassistant.data.repository.TransactionInput
import com.anujstha.lifeassistant.data.repository.TransactionRepository
import com.anujstha.lifeassistant.domain.model.TransactionType
import com.anujstha.lifeassistant.domain.model.TransactionType.EXPENSE
import com.anujstha.lifeassistant.domain.model.TransactionType.INCOME
import com.anujstha.lifeassistant.domain.model.ValidationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class FinanceRepositoryTest {

    private val clock = MutableClock()
    private val ids = SequentialIds()
    private lateinit var db: AppDatabase
    private lateinit var categories: ExpenseCategoryRepository
    private lateinit var transactions: TransactionRepository
    private lateinit var budgets: BudgetRepository

    private val oct = YearMonth.of(2026, 10)
    private val oct1: LocalDate = oct.atDay(1)
    private val oct31: LocalDate = oct.atEndOfMonth()

    @Before
    fun setUp() {
        db = inMemoryDatabase(clock, ids)
        categories = ExpenseCategoryRepository(db, clock, ids)
        transactions = TransactionRepository(db, clock, ids)
        budgets = BudgetRepository(db, clock, ids)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun category(name: String, type: TransactionType = EXPENSE): ExpenseCategoryEntity =
        categories.observeActive(type).first().single { it.name == name }

    private suspend fun add(
        amountMinor: Long,
        category: ExpenseCategoryEntity,
        date: LocalDate,
        currency: String = "NPR",
        note: String? = null,
    ) = transactions.save(null, TransactionInput(amountMinor, currency, category.type, category.id, date, note = note))

    @Test
    fun defaultCategories_areSeededOnCreate() = runTest {
        assertEquals(DefaultData.expenseCategories.sorted(), categories.observeActive(EXPENSE).first().map { it.name }.sorted())
        assertEquals(DefaultData.incomeCategories.sorted(), categories.observeActive(INCOME).first().map { it.name }.sorted())
        assertTrue(categories.observeAll().first().all { it.isDefault })
    }

    @Test
    fun emptyDatabase_totalsAreZero() = runTest {
        assertEquals(0L, transactions.observeTotal(EXPENSE, "NPR", oct1, oct31).first())
        assertTrue(transactions.observeCategoryTotals(EXPENSE, "NPR", oct1, oct31).first().isEmpty())
        assertTrue(transactions.observeFiltered().first().isEmpty())
    }

    @Test
    fun totals_areExact_andScopedByTypeCurrencyAndInclusiveDateRange() = runTest {
        val food = category("Food & Dining")
        val salary = category("Salary", INCOME)
        add(1_050, food, oct1) // NPR 10.50
        add(99_999, food, oct31) // NPR 999.99, last day still included
        add(5_000, food, oct31.plusDays(1)) // November: excluded
        add(7_000, food, oct1, currency = "USD") // other currency: excluded
        add(5_000_000, salary, oct1.plusDays(4))

        assertEquals(101_049L, transactions.observeTotal(EXPENSE, "NPR", oct1, oct31).first())
        assertEquals(5_000_000L, transactions.observeTotal(INCOME, "NPR", oct1, oct31).first())
        assertEquals(listOf("NPR", "USD"), transactions.observeCurrencies().first())
    }

    @Test
    fun categoryTotals_areGroupedAndSortedLargestFirst() = runTest {
        val food = category("Food & Dining")
        val transport = category("Transport")
        add(200, food, oct1)
        add(300, food, oct1)
        add(1_000, transport, oct1)

        val totals = transactions.observeCategoryTotals(EXPENSE, "NPR", oct1, oct31).first()
        assertEquals(listOf(transport.id to 1_000L, food.id to 500L), totals.map { it.categoryId to it.totalMinor })
    }

    @Test
    fun monthlyTotals_groupByMonth() = runTest {
        val food = category("Food & Dining")
        add(100, food, LocalDate.of(2026, 8, 31))
        add(200, food, LocalDate.of(2026, 9, 1))
        add(300, food, LocalDate.of(2026, 9, 30))
        add(400, food, LocalDate.of(2026, 10, 15))

        val months = transactions.observeMonthlyTotals(EXPENSE, "NPR", LocalDate.of(2026, 8, 1), oct31).first()
        assertEquals(
            listOf(MonthTotal("2026-08", 100), MonthTotal("2026-09", 500), MonthTotal("2026-10", 400)),
            months,
        )
    }

    @Test
    fun filters_byType_category_dateRange_andNoteSearch() = runTest {
        val food = category("Food & Dining")
        val salary = category("Salary", INCOME)
        add(100, food, oct1, note = "Momo with friends")
        add(200, food, oct31, note = "Lunch")
        add(300, salary, oct1)

        assertEquals(2, transactions.observeFiltered(TransactionFilter(type = EXPENSE)).first().size)
        assertEquals(1, transactions.observeFiltered(TransactionFilter(categoryId = salary.id)).first().size)
        assertEquals(listOf(200L), transactions.observeFiltered(TransactionFilter(from = oct.atDay(2))).first().map { it.amountMinor })
        assertEquals(listOf(100L), transactions.observeFiltered(TransactionFilter(query = "momo")).first().map { it.amountMinor })
    }

    @Test
    fun edit_keepsCreatedAt() = runTest {
        val food = category("Food & Dining")
        val id = add(100, food, oct1)
        val created = transactions.observe(id).first()!!.createdAt
        clock.advance(Duration.ofDays(1))

        transactions.save(id, TransactionInput(250, "NPR", EXPENSE, food.id, oct1))

        val edited = transactions.observe(id).first()!!
        assertEquals(250L, edited.amountMinor)
        assertEquals(created, edited.createdAt)
        assertEquals(clock.now, edited.updatedAt)
    }

    @Test
    fun invalidInput_isRejected() = runTest {
        val food = category("Food & Dining")
        val salary = category("Salary", INCOME)
        assertThrows(ValidationException::class.java) { save(TransactionInput(0, "NPR", EXPENSE, food.id, oct1)) }
        assertThrows(ValidationException::class.java) { save(TransactionInput(-5, "NPR", EXPENSE, food.id, oct1)) }
        assertThrows(ValidationException::class.java) { save(TransactionInput(5, "ZZZ1", EXPENSE, food.id, oct1)) }
        assertThrows(ValidationException::class.java) { save(TransactionInput(5, "NPR", EXPENSE, salary.id, oct1)) }
        assertThrows(ValidationException::class.java) { save(TransactionInput(5, "NPR", EXPENSE, "missing", oct1)) }
        assertThrows(ValidationException::class.java) { save(TransactionInput(5, "NPR", EXPENSE, food.id, oct1, note = "x".repeat(501))) }
    }

    private fun save(input: TransactionInput) = kotlinx.coroutines.runBlocking { transactions.save(null, input) }

    @Test
    fun removingUsedCategory_archivesIt_andKeepsHistory() = runTest {
        val food = category("Food & Dining")
        val txId = add(100, food, oct1)

        val result = categories.remove(food.id)

        assertEquals(CategoryRemoval.Archived(transactionCount = 1), result)
        assertTrue(categories.observeActive(EXPENSE).first().none { it.id == food.id })
        assertNotNull(categories.observeAll().first().single { it.id == food.id }.archivedAt)
        assertEquals(food.id, transactions.observe(txId).first()!!.categoryId)
        // New transactions can't use the archived category; the existing one can still be edited.
        assertThrows(ValidationException::class.java) { save(TransactionInput(5, "NPR", EXPENSE, food.id, oct1)) }
        transactions.save(txId, TransactionInput(150, "NPR", EXPENSE, food.id, oct1))
    }

    @Test
    fun removingUnusedCategory_deletesIt() = runTest {
        val custom = categories.create("Pets", EXPENSE)
        assertEquals(CategoryRemoval.Deleted, categories.remove(custom.id))
        assertTrue(categories.observeAll().first().none { it.id == custom.id })
    }

    @Test
    fun recreatingArchivedCategory_restoresIt() = runTest {
        val food = category("Food & Dining")
        add(100, food, oct1)
        categories.remove(food.id)

        val restored = categories.create("food & dining", EXPENSE)

        assertEquals(food.id, restored.id)
        assertTrue(categories.observeActive(EXPENSE).first().any { it.id == food.id })
    }

    @Test
    fun foreignKey_blocksHardDeleteOfUsedCategory() = runTest {
        val food = category("Food & Dining")
        add(100, food, oct1)
        assertThrows(SQLiteConstraintException::class.java) {
            kotlinx.coroutines.runBlocking { db.expenseCategoryDao().delete(food.id) }
        }
    }

    @Test
    fun budget_onePerMonthAndScope_updatesInPlace() = runTest {
        val food = category("Food & Dining")
        val first = budgets.setBudget(oct, null, 50_000_00, "NPR")
        clock.advance(Duration.ofHours(1))
        val second = budgets.setBudget(oct, null, 60_000_00, "NPR", warnAtPercent = 90)
        budgets.setBudget(oct, food.id, 10_000_00, "NPR")

        val stored = budgets.observeForMonth(oct).first()
        assertEquals(2, stored.size)
        val overall = stored.single { it.categoryId == null }
        assertEquals(first.id, second.id)
        assertEquals(60_000_00L, overall.limitMinor)
        assertEquals(90, overall.warnAtPercent)
        assertEquals(first.createdAt, overall.createdAt)
    }

    @Test
    fun budget_rejectsInvalidValues() = runTest {
        val salary = category("Salary", INCOME)
        assertThrows(ValidationException::class.java) { kotlinx.coroutines.runBlocking { budgets.setBudget(oct, null, 0, "NPR") } }
        assertThrows(ValidationException::class.java) { kotlinx.coroutines.runBlocking { budgets.setBudget(oct, null, 100, "NPR", warnAtPercent = 0) } }
        assertThrows(ValidationException::class.java) { kotlinx.coroutines.runBlocking { budgets.setBudget(oct, salary.id, 100, "NPR") } }
    }
}
