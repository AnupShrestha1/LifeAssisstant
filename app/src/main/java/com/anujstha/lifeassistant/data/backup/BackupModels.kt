package com.anujstha.lifeassistant.data.backup

import kotlinx.serialization.Serializable

/*
 * Backup file format, version 1 (JSON, UTF-8). Documented in docs/BACKUP_FORMAT.md.
 *
 * Conventions: ids are the app's UUID strings; dates are ISO "yyyy-MM-dd"; times "HH:mm[:ss]";
 * wall-clock date-times "yyyy-MM-ddTHH:mm[:ss]"; instants ISO-8601 UTC ("2026-10-14T06:00:00Z");
 * months "yyyy-MM"; money is integer minor units (paisa for NPR) plus an ISO 4217 code;
 * enums are their constant names. Unknown fields are ignored so newer minor additions don't
 * break older readers; a higher formatVersion is rejected.
 */

const val BACKUP_FORMAT_ID = "life-assistant-backup"
const val BACKUP_FORMAT_VERSION = 1

@Serializable
data class BackupFile(
    val format: String,
    val formatVersion: Int,
    val appVersion: String = "",
    val exportedAt: String = "",
    val settings: BackupSettings? = null,
    val journalTags: List<BackupJournalTag> = emptyList(),
    val journalEntries: List<BackupJournalEntry> = emptyList(),
    val journalEntryTags: List<BackupJournalEntryTag> = emptyList(),
    val expenseCategories: List<BackupExpenseCategory> = emptyList(),
    val transactions: List<BackupTransaction> = emptyList(),
    val budgets: List<BackupBudget> = emptyList(),
    val taskCategories: List<BackupTaskCategory> = emptyList(),
    val tasks: List<BackupTask> = emptyList(),
    val reminders: List<BackupReminder> = emptyList(),
)

@Serializable
data class BackupSettings(
    val themeMode: String,
    val currencyCode: String,
    val displayZoneId: String,
    val timeFormat: String,
)

@Serializable
data class BackupJournalTag(val id: String, val name: String, val createdAt: String)

@Serializable
data class BackupJournalEntry(
    val id: String,
    val title: String,
    val body: String,
    val entryDate: String,
    val mood: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class BackupJournalEntryTag(val entryId: String, val tagId: String)

@Serializable
data class BackupExpenseCategory(
    val id: String,
    val name: String,
    val type: String,
    val isDefault: Boolean = false,
    val archivedAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class BackupTransaction(
    val id: String,
    val amountMinor: Long,
    val currencyCode: String,
    val type: String,
    val categoryId: String,
    val date: String,
    val time: String? = null,
    val paymentMethod: String,
    val note: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class BackupBudget(
    val id: String,
    val yearMonth: String,
    val categoryId: String? = null,
    val limitMinor: Long,
    val currencyCode: String,
    val warnAtPercent: Int,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class BackupTaskCategory(val id: String, val name: String, val archivedAt: String? = null, val createdAt: String, val updatedAt: String)

@Serializable
data class BackupRecurrence(val frequency: String, val interval: Int = 1, val endDate: String? = null)

@Serializable
data class BackupTask(
    val id: String,
    val title: String,
    val description: String? = null,
    val status: String,
    val priority: String,
    val dueDate: String? = null,
    val dueTime: String? = null,
    val categoryId: String? = null,
    val completedAt: String? = null,
    val seriesId: String? = null,
    val seriesStartDate: String? = null,
    val occurrenceDate: String? = null,
    val recurrence: BackupRecurrence? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class BackupReminder(
    val id: String,
    val title: String,
    val description: String? = null,
    val taskId: String? = null,
    val startAt: String,
    val zoneId: String,
    val recurrence: BackupRecurrence? = null,
    val enabled: Boolean,
    val createdAt: String,
    val updatedAt: String,
)
