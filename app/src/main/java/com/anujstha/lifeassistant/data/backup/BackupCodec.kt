package com.anujstha.lifeassistant.data.backup

import com.anujstha.lifeassistant.data.entity.BudgetEntity
import com.anujstha.lifeassistant.data.entity.ExpenseCategoryEntity
import com.anujstha.lifeassistant.data.entity.JournalEntryEntity
import com.anujstha.lifeassistant.data.entity.JournalEntryTagCrossRef
import com.anujstha.lifeassistant.data.entity.JournalTagEntity
import com.anujstha.lifeassistant.data.entity.ReminderEntity
import com.anujstha.lifeassistant.data.entity.TaskCategoryEntity
import com.anujstha.lifeassistant.data.entity.TaskEntity
import com.anujstha.lifeassistant.data.entity.TransactionEntity
import com.anujstha.lifeassistant.data.preferences.SettingsRepository
import com.anujstha.lifeassistant.domain.model.Mood
import com.anujstha.lifeassistant.domain.model.PaymentMethod
import com.anujstha.lifeassistant.domain.model.Priority
import com.anujstha.lifeassistant.domain.model.RecurrenceFrequency
import com.anujstha.lifeassistant.domain.model.RecurrenceRule
import com.anujstha.lifeassistant.domain.model.TaskStatus
import com.anujstha.lifeassistant.domain.model.ThemeMode
import com.anujstha.lifeassistant.domain.model.TimeFormat
import com.anujstha.lifeassistant.domain.model.TransactionType
import com.anujstha.lifeassistant.domain.model.UserSettings
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/** All restorable records, as database entities. Reminders carry no requestCode/schedule yet. */
data class BackupData(
    val settings: UserSettings? = null,
    val journalTags: List<JournalTagEntity> = emptyList(),
    val journalEntries: List<JournalEntryEntity> = emptyList(),
    val journalEntryTags: List<JournalEntryTagCrossRef> = emptyList(),
    val expenseCategories: List<ExpenseCategoryEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val taskCategories: List<TaskCategoryEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
) {
    val totalRecords: Int
        get() = journalEntries.size + journalTags.size + expenseCategories.size + transactions.size +
            budgets.size + taskCategories.size + tasks.size + reminders.size
}

sealed interface DecodeResult {
    data class Valid(val data: BackupData, val appVersion: String, val exportedAt: Instant?) : DecodeResult

    /** [errors] are readable messages with the location of each problem. */
    data class Invalid(val errors: List<String>) : DecodeResult
}

object BackupCodec {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true // tolerate fields added by later minor versions
        encodeDefaults = true
    }

    private const val MAX_ERRORS = 20

    // ---------------- Encode ----------------

    fun encode(data: BackupData, appVersion: String, exportedAt: Instant): String = json.encodeToString(
        BackupFile.serializer(),
        BackupFile(
            format = BACKUP_FORMAT_ID,
            formatVersion = BACKUP_FORMAT_VERSION,
            appVersion = appVersion,
            exportedAt = exportedAt.toString(),
            settings = data.settings?.let {
                BackupSettings(it.themeMode.name, it.currencyCode, it.displayZoneId, it.timeFormat.name)
            },
            journalTags = data.journalTags.map { BackupJournalTag(it.id, it.name, it.createdAt.toString()) },
            journalEntries = data.journalEntries.map {
                BackupJournalEntry(it.id, it.title, it.body, it.entryDate.toString(), it.mood?.name, it.createdAt.toString(), it.updatedAt.toString())
            },
            journalEntryTags = data.journalEntryTags.map { BackupJournalEntryTag(it.entryId, it.tagId) },
            expenseCategories = data.expenseCategories.map {
                BackupExpenseCategory(it.id, it.name, it.type.name, it.isDefault, it.archivedAt?.toString(), it.createdAt.toString(), it.updatedAt.toString())
            },
            transactions = data.transactions.map {
                BackupTransaction(
                    it.id, it.amountMinor, it.currencyCode, it.type.name, it.categoryId, it.date.toString(),
                    it.time?.toString(), it.paymentMethod.name, it.note, it.createdAt.toString(), it.updatedAt.toString(),
                )
            },
            budgets = data.budgets.map {
                BackupBudget(it.id, it.yearMonth.toString(), it.categoryId, it.limitMinor, it.currencyCode, it.warnAtPercent, it.createdAt.toString(), it.updatedAt.toString())
            },
            taskCategories = data.taskCategories.map {
                BackupTaskCategory(it.id, it.name, it.archivedAt?.toString(), it.createdAt.toString(), it.updatedAt.toString())
            },
            tasks = data.tasks.map {
                BackupTask(
                    it.id, it.title, it.description, it.status.name, it.priority.name, it.dueDate?.toString(),
                    it.dueTime?.toString(), it.categoryId, it.completedAt?.toString(), it.seriesId,
                    it.seriesStartDate?.toString(), it.occurrenceDate?.toString(), it.recurrence?.toBackup(),
                    it.createdAt.toString(), it.updatedAt.toString(),
                )
            },
            reminders = data.reminders.map {
                BackupReminder(
                    it.id, it.title, it.description, it.taskId, it.startAt.toString(), it.zoneId,
                    it.recurrence?.toBackup(), it.enabled, it.createdAt.toString(), it.updatedAt.toString(),
                )
            },
        ),
    )

    private fun RecurrenceRule.toBackup() = BackupRecurrence(frequency.name, interval, endDate?.toString())

    // ---------------- Decode + validate ----------------

    fun decode(text: String): DecodeResult {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: SerializationException) {
            return DecodeResult.Invalid(listOf("This is not a Life Assistant backup file (it could not be read as one)."))
        } catch (e: IllegalArgumentException) {
            return DecodeResult.Invalid(listOf("This is not a Life Assistant backup file (it could not be read as one)."))
        }
        if (file.format != BACKUP_FORMAT_ID) {
            return DecodeResult.Invalid(listOf("This file is not a Life Assistant backup."))
        }
        if (file.formatVersion > BACKUP_FORMAT_VERSION) {
            return DecodeResult.Invalid(
                listOf("This backup was made by a newer version of the app (format ${file.formatVersion}). Update the app to restore it."),
            )
        }
        if (file.formatVersion < 1) return DecodeResult.Invalid(listOf("Unsupported backup format version ${file.formatVersion}."))

        val v = Validator()
        val data = v.convert(file)
        return if (v.errors.isEmpty()) {
            DecodeResult.Valid(data, file.appVersion, runCatching { Instant.parse(file.exportedAt) }.getOrNull())
        } else {
            DecodeResult.Invalid(v.errors.take(MAX_ERRORS) + if (v.errors.size > MAX_ERRORS) listOf("…and ${v.errors.size - MAX_ERRORS} more problems.") else emptyList())
        }
    }

    /** Converts DTOs to entities, recording every problem instead of stopping at the first. */
    private class Validator {
        val errors = mutableListOf<String>()

        private fun fail(where: String, message: String): Nothing? {
            errors += "$where: $message"
            return null
        }

        private fun id(where: String, value: String): String? = value.takeIf { it.isNotBlank() && it.length <= 100 } ?: fail(where, "missing or invalid id")
        private fun instant(where: String, value: String?): Instant? = value?.let { runCatching { Instant.parse(it) }.getOrNull() ?: fail(where, "invalid timestamp \"$it\"") }
        private fun requiredInstant(where: String, value: String): Instant? = instant(where, value)
        private fun date(where: String, value: String?): LocalDate? = value?.let { runCatching { LocalDate.parse(it) }.getOrNull() ?: fail(where, "invalid date \"$it\"") }
        private fun time(where: String, value: String?): LocalTime? = value?.let { runCatching { LocalTime.parse(it) }.getOrNull() ?: fail(where, "invalid time \"$it\"") }
        private inline fun <reified E : Enum<E>> enum(where: String, value: String?): E? =
            value?.let { v -> enumValues<E>().firstOrNull { it.name == v } ?: fail(where, "unknown value \"$v\"") }

        private fun text(where: String, value: String, max: Int, allowBlank: Boolean = false): String? = when {
            !allowBlank && value.isBlank() -> fail(where, "must not be empty")
            value.length > max -> fail(where, "too long")
            else -> value
        }

        private fun currency(where: String, code: String): String? = code.takeIf(SettingsRepository::isValidCurrency) ?: fail(where, "unknown currency \"$code\"")

        private fun recurrence(where: String, r: BackupRecurrence?): RecurrenceRule? {
            r ?: return null
            val frequency = enum<RecurrenceFrequency>("$where.frequency", r.frequency) ?: return null
            if (r.interval !in 1..RecurrenceRule.MAX_INTERVAL) return fail("$where.interval", "must be 1–${RecurrenceRule.MAX_INTERVAL}")
            return RecurrenceRule(frequency, r.interval, date("$where.endDate", r.endDate))
        }

        private fun <T> unique(kind: String, items: List<T>, key: (T) -> String) {
            items.groupingBy(key).eachCount().filter { it.value > 1 }.keys.forEach { fail(kind, "duplicate id \"$it\"") }
        }

        fun convert(f: BackupFile): BackupData {
            unique("journalTags", f.journalTags) { it.id }
            unique("journalEntries", f.journalEntries) { it.id }
            unique("expenseCategories", f.expenseCategories) { it.id }
            unique("transactions", f.transactions) { it.id }
            unique("budgets", f.budgets) { it.id }
            unique("taskCategories", f.taskCategories) { it.id }
            unique("tasks", f.tasks) { it.id }
            unique("reminders", f.reminders) { it.id }

            val settings = f.settings?.let { s ->
                val theme = enum<ThemeMode>("settings.themeMode", s.themeMode)
                val format = enum<TimeFormat>("settings.timeFormat", s.timeFormat)
                val cur = currency("settings.currencyCode", s.currencyCode)
                val zone = s.displayZoneId.takeIf(SettingsRepository::isValidZone) ?: fail("settings.displayZoneId", "unknown time zone")
                if (theme != null && format != null && cur != null && zone != null) UserSettings(theme, cur, zone, format) else null
            }

            val tags = f.journalTags.mapIndexedNotNull { i, t ->
                val w = "journalTags[$i]"
                JournalTagEntity(id(w, t.id) ?: return@mapIndexedNotNull null, text("$w.name", t.name, 40) ?: return@mapIndexedNotNull null, requiredInstant("$w.createdAt", t.createdAt) ?: return@mapIndexedNotNull null)
            }
            val entries = f.journalEntries.mapIndexedNotNull { i, e ->
                val w = "journalEntries[$i]"
                JournalEntryEntity(
                    id(w, e.id) ?: return@mapIndexedNotNull null,
                    text("$w.title", e.title, 200, allowBlank = true) ?: return@mapIndexedNotNull null,
                    text("$w.body", e.body, 100_000, allowBlank = true) ?: return@mapIndexedNotNull null,
                    date("$w.entryDate", e.entryDate) ?: return@mapIndexedNotNull null,
                    if (e.mood == null) null else enum<Mood>("$w.mood", e.mood) ?: return@mapIndexedNotNull null,
                    requiredInstant("$w.createdAt", e.createdAt) ?: return@mapIndexedNotNull null,
                    requiredInstant("$w.updatedAt", e.updatedAt) ?: return@mapIndexedNotNull null,
                )
            }
            val tagIds = f.journalTags.map { it.id }.toSet()
            val entryIds = f.journalEntries.map { it.id }.toSet()
            val links = f.journalEntryTags.mapIndexedNotNull { i, l ->
                val w = "journalEntryTags[$i]"
                when {
                    l.entryId !in entryIds -> fail(w, "refers to a journal entry that isn't in the backup")
                    l.tagId !in tagIds -> fail(w, "refers to a tag that isn't in the backup")
                    else -> JournalEntryTagCrossRef(l.entryId, l.tagId)
                }
            }.distinct()

            val categories = f.expenseCategories.mapIndexedNotNull { i, c ->
                val w = "expenseCategories[$i]"
                ExpenseCategoryEntity(
                    id(w, c.id) ?: return@mapIndexedNotNull null,
                    text("$w.name", c.name, 40) ?: return@mapIndexedNotNull null,
                    enum<TransactionType>("$w.type", c.type) ?: return@mapIndexedNotNull null,
                    c.isDefault,
                    instant("$w.archivedAt", c.archivedAt),
                    requiredInstant("$w.createdAt", c.createdAt) ?: return@mapIndexedNotNull null,
                    requiredInstant("$w.updatedAt", c.updatedAt) ?: return@mapIndexedNotNull null,
                )
            }
            val categoryTypes = categories.associate { it.id to it.type }
            val transactions = f.transactions.mapIndexedNotNull { i, t ->
                val w = "transactions[$i]"
                val type = enum<TransactionType>("$w.type", t.type) ?: return@mapIndexedNotNull null
                if (t.amountMinor <= 0) return@mapIndexedNotNull fail("$w.amountMinor", "must be greater than zero")
                when (categoryTypes[t.categoryId]) {
                    null -> return@mapIndexedNotNull fail("$w.categoryId", "refers to a category that isn't in the backup")
                    type -> Unit
                    else -> return@mapIndexedNotNull fail("$w.categoryId", "category type doesn't match transaction type")
                }
                TransactionEntity(
                    id(w, t.id) ?: return@mapIndexedNotNull null,
                    t.amountMinor,
                    currency("$w.currencyCode", t.currencyCode) ?: return@mapIndexedNotNull null,
                    type,
                    t.categoryId,
                    date("$w.date", t.date) ?: return@mapIndexedNotNull null,
                    time("$w.time", t.time),
                    enum<PaymentMethod>("$w.paymentMethod", t.paymentMethod) ?: return@mapIndexedNotNull null,
                    t.note?.let { text("$w.note", it, 500, allowBlank = true) ?: return@mapIndexedNotNull null },
                    requiredInstant("$w.createdAt", t.createdAt) ?: return@mapIndexedNotNull null,
                    requiredInstant("$w.updatedAt", t.updatedAt) ?: return@mapIndexedNotNull null,
                )
            }
            val budgets = f.budgets.mapIndexedNotNull { i, b ->
                val w = "budgets[$i]"
                if (b.categoryId != null && categoryTypes[b.categoryId] != TransactionType.EXPENSE) {
                    return@mapIndexedNotNull fail("$w.categoryId", "must refer to an expense category in the backup")
                }
                if (b.limitMinor < 0) return@mapIndexedNotNull fail("$w.limitMinor", "must not be negative")
                if (b.warnAtPercent !in 1..100) return@mapIndexedNotNull fail("$w.warnAtPercent", "must be 1–100")
                BudgetEntity(
                    id(w, b.id) ?: return@mapIndexedNotNull null,
                    runCatching { YearMonth.parse(b.yearMonth) }.getOrNull() ?: return@mapIndexedNotNull fail("$w.yearMonth", "invalid month"),
                    b.categoryId,
                    b.limitMinor,
                    currency("$w.currencyCode", b.currencyCode) ?: return@mapIndexedNotNull null,
                    b.warnAtPercent,
                    requiredInstant("$w.createdAt", b.createdAt) ?: return@mapIndexedNotNull null,
                    requiredInstant("$w.updatedAt", b.updatedAt) ?: return@mapIndexedNotNull null,
                )
            }
            if (budgets.groupBy { it.yearMonth to it.categoryId }.any { it.value.size > 1 }) {
                fail("budgets", "more than one budget for the same month and category")
            }

            val taskCategories = f.taskCategories.mapIndexedNotNull { i, c ->
                val w = "taskCategories[$i]"
                TaskCategoryEntity(
                    id(w, c.id) ?: return@mapIndexedNotNull null,
                    text("$w.name", c.name, 40) ?: return@mapIndexedNotNull null,
                    instant("$w.archivedAt", c.archivedAt),
                    requiredInstant("$w.createdAt", c.createdAt) ?: return@mapIndexedNotNull null,
                    requiredInstant("$w.updatedAt", c.updatedAt) ?: return@mapIndexedNotNull null,
                )
            }
            val taskCategoryIds = f.taskCategories.map { it.id }.toSet()
            val tasks = f.tasks.mapIndexedNotNull { i, t ->
                val w = "tasks[$i]"
                if (t.categoryId != null && t.categoryId !in taskCategoryIds) {
                    return@mapIndexedNotNull fail("$w.categoryId", "refers to a task category that isn't in the backup")
                }
                TaskEntity(
                    id(w, t.id) ?: return@mapIndexedNotNull null,
                    text("$w.title", t.title, 200) ?: return@mapIndexedNotNull null,
                    t.description?.let { text("$w.description", it, 5_000, allowBlank = true) ?: return@mapIndexedNotNull null },
                    enum<TaskStatus>("$w.status", t.status) ?: return@mapIndexedNotNull null,
                    enum<Priority>("$w.priority", t.priority) ?: return@mapIndexedNotNull null,
                    date("$w.dueDate", t.dueDate),
                    time("$w.dueTime", t.dueTime),
                    t.categoryId,
                    instant("$w.completedAt", t.completedAt),
                    t.seriesId,
                    date("$w.seriesStartDate", t.seriesStartDate),
                    date("$w.occurrenceDate", t.occurrenceDate),
                    recurrence("$w.recurrence", t.recurrence),
                    requiredInstant("$w.createdAt", t.createdAt) ?: return@mapIndexedNotNull null,
                    requiredInstant("$w.updatedAt", t.updatedAt) ?: return@mapIndexedNotNull null,
                )
            }
            if (tasks.filter { it.seriesId != null }.groupBy { it.seriesId to it.occurrenceDate }.any { it.value.size > 1 }) {
                fail("tasks", "the same recurring occurrence appears more than once")
            }
            val taskIds = f.tasks.map { it.id }.toSet()
            val reminders = f.reminders.mapIndexedNotNull { i, r ->
                val w = "reminders[$i]"
                if (r.taskId != null && r.taskId !in taskIds) return@mapIndexedNotNull fail("$w.taskId", "refers to a task that isn't in the backup")
                if (!SettingsRepository.isValidZone(r.zoneId)) return@mapIndexedNotNull fail("$w.zoneId", "unknown time zone")
                ReminderEntity(
                    id = id(w, r.id) ?: return@mapIndexedNotNull null,
                    requestCode = 0, // assigned on restore (device-specific)
                    title = text("$w.title", r.title, 200) ?: return@mapIndexedNotNull null,
                    description = r.description?.let { text("$w.description", it, 1_000, allowBlank = true) ?: return@mapIndexedNotNull null },
                    taskId = r.taskId,
                    startAt = runCatching { LocalDateTime.parse(r.startAt) }.getOrNull() ?: return@mapIndexedNotNull fail("$w.startAt", "invalid date-time"),
                    zoneId = r.zoneId,
                    recurrence = recurrence("$w.recurrence", r.recurrence),
                    nextTriggerAt = null, // recomputed from "now" on restore
                    enabled = r.enabled,
                    createdAt = requiredInstant("$w.createdAt", r.createdAt) ?: return@mapIndexedNotNull null,
                    updatedAt = requiredInstant("$w.updatedAt", r.updatedAt) ?: return@mapIndexedNotNull null,
                )
            }

            // Names are unique (ignoring case) in the database, so a backup can't contain duplicates either.
            fun dupNames(kind: String, names: List<String>) =
                names.groupingBy { it.trim().lowercase() }.eachCount().filter { it.value > 1 }.keys
                    .forEach { fail(kind, "duplicate name \"$it\"") }
            dupNames("journalTags", tags.map { it.name })
            dupNames("taskCategories", taskCategories.map { it.name })
            TransactionType.entries.forEach { t -> dupNames("expenseCategories ($t)", categories.filter { it.type == t }.map { it.name }) }

            return BackupData(settings, tags, entries, links, categories, transactions, budgets, taskCategories, tasks, reminders)
        }
    }
}
