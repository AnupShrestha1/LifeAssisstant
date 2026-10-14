package com.anujstha.lifeassistant.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.anujstha.lifeassistant.domain.model.PaymentMethod
import com.anujstha.lifeassistant.domain.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * Income or expense category. Categories that are in use are archived instead of deleted,
 * so historical transactions keep their category.
 */
@Entity(
    tableName = "expense_categories",
    indices = [Index(value = ["name", "type"], unique = true)],
)
data class ExpenseCategoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val type: TransactionType,
    val isDefault: Boolean,
    val archivedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * A manually recorded income or expense. Amounts are integer minor units (e.g. paisa),
 * always positive; [type] gives the direction. Not linked to any bank account.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            // A category with transactions can never be hard-deleted; it is archived instead.
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("date"), Index(value = ["categoryId", "date"])],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val amountMinor: Long,
    /** ISO 4217 code recorded with the transaction; changing the app currency never relabels it. */
    val currencyCode: String,
    val type: TransactionType,
    val categoryId: String,
    val date: LocalDate,
    val time: LocalTime?,
    val paymentMethod: PaymentMethod,
    val note: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Monthly spending limit. [categoryId] null means the overall budget for the month.
 * Uniqueness of (yearMonth, categoryId) is enforced by [com.anujstha.lifeassistant.data.dao.BudgetDao.upsertForScope],
 * because SQLite unique indexes treat NULLs as distinct.
 */
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["yearMonth", "categoryId"]), Index("categoryId")],
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    val yearMonth: YearMonth,
    val categoryId: String?,
    val limitMinor: Long,
    val currencyCode: String,
    /** Warn when spending reaches this percentage of the limit (1–100). */
    val warnAtPercent: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Result row for per-category totals. */
data class CategoryTotal(
    val categoryId: String,
    val totalMinor: Long,
)

/** Result row for per-month totals; [yearMonth] is "yyyy-MM". */
data class MonthTotal(
    val yearMonth: String,
    val totalMinor: Long,
)
