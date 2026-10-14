package com.anujstha.lifeassistant.domain.model

// Enum names are persisted in the database and backup files. Never rename or remove a
// constant without a migration; adding new constants is safe.

/** Optional journal mood label. Purely descriptive; the app never interprets it. */
enum class Mood { HAPPY, NEUTRAL, SAD, STRESSED, EXCITED }

enum class TransactionType { INCOME, EXPENSE }

enum class PaymentMethod { CASH, CARD, BANK_TRANSFER, MOBILE_WALLET, OTHER }

enum class TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    /** Open tasks can become overdue. Cancelled tasks are closed but never count as completed. */
    val isOpen: Boolean get() = this == PENDING || this == IN_PROGRESS
}

/** Declared low → high so ordinal comparisons sort by importance. */
enum class Priority { LOW, MEDIUM, HIGH, URGENT }

enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY }
