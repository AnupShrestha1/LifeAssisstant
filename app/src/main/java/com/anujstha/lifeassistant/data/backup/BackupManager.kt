package com.anujstha.lifeassistant.data.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import com.anujstha.lifeassistant.data.database.AppDatabase
import com.anujstha.lifeassistant.data.entity.ReminderEntity
import com.anujstha.lifeassistant.data.preferences.SettingsRepository
import com.anujstha.lifeassistant.domain.usecase.ReminderSchedule
import com.anujstha.lifeassistant.notifications.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Clock
import java.time.ZoneId

enum class RestoreMode {
    /** Add records that aren't on this device; keep everything already here and current settings. */
    MERGE,

    /** Delete all current data, then restore the backup exactly, including its settings. */
    REPLACE,
}

/** What a restore would do, shown before the user confirms. */
data class RestorePreview(
    val inBackup: Map<String, Int>,
    /** MERGE only: records whose id already exists here and will be kept as they are. */
    val alreadyPresent: Int,
)

data class RestoreReport(val added: Int, val skipped: Int, val remindersScheduled: Int)

class BackupTooLargeException : IOException("Backup file is too large")

/**
 * Creates and restores backups. Restores are all-or-nothing: the database work happens in one
 * transaction, and alarms are only touched after it commits.
 */
class BackupManager(
    private val db: AppDatabase,
    private val scheduler: ReminderScheduler,
    private val cancelNotifications: () -> Unit,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val contentResolver: ContentResolver,
    private val appVersion: String,
) {
    private val dao = db.backupDao()

    /** Everything that goes into a backup, read in one transaction for a consistent snapshot. */
    suspend fun snapshot(): BackupData = db.withTransaction {
        BackupData(
            settings = settings.settings.first(),
            journalTags = dao.journalTags(),
            journalEntries = dao.journalEntries(),
            journalEntryTags = dao.journalEntryTags(),
            expenseCategories = dao.expenseCategories(),
            transactions = dao.transactions(),
            budgets = dao.budgets(),
            taskCategories = dao.taskCategories(),
            tasks = dao.tasks(),
            reminders = dao.reminders(),
        )
    }

    fun encode(data: BackupData): String = BackupCodec.encode(data, appVersion, clock.instant())

    /** Writes a backup to [uri] (picked by the user). Returns the number of records written. */
    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val data = snapshot()
        val text = encode(data)
        val stream = contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open the chosen file")
        stream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
        data.totalRecords
    }

    /** Reads and fully validates a backup file. Nothing is changed. */
    suspend fun read(uri: Uri): DecodeResult = withContext(Dispatchers.IO) {
        val stream = contentResolver.openInputStream(uri) ?: throw IOException("Cannot open the chosen file")
        val bytes = stream.use { input ->
            val buffer = input.readNBytesCompat(MAX_BYTES + 1)
            if (buffer.size > MAX_BYTES) throw BackupTooLargeException()
            buffer
        }
        BackupCodec.decode(bytes.toString(Charsets.UTF_8))
    }

    suspend fun preview(data: BackupData, mode: RestoreMode): RestorePreview {
        val counts = linkedMapOf(
            "journalEntries" to data.journalEntries.size,
            "journalTags" to data.journalTags.size,
            "tasks" to data.tasks.size,
            "taskCategories" to data.taskCategories.size,
            "reminders" to data.reminders.size,
            "transactions" to data.transactions.size,
            "expenseCategories" to data.expenseCategories.size,
            "budgets" to data.budgets.size,
        )
        if (mode == RestoreMode.REPLACE) return RestorePreview(counts, 0)
        val present = db.withTransaction {
            fun overlap(backupIds: List<String>, hereIds: List<String>) = backupIds.intersect(hereIds.toSet()).size
            overlap(data.journalEntries.map { it.id }, dao.journalEntries().map { it.id }) +
                overlap(data.tasks.map { it.id }, dao.tasks().map { it.id }) +
                overlap(data.reminders.map { it.id }, dao.reminders().map { it.id }) +
                overlap(data.transactions.map { it.id }, dao.transactions().map { it.id })
        }
        return RestorePreview(counts, present)
    }

    suspend fun restore(data: BackupData, mode: RestoreMode): RestoreReport = withContext(Dispatchers.IO) {
        val previousReminders = if (mode == RestoreMode.REPLACE) dao.reminders() else emptyList()
        val now = clock.instant()
        var added = 0
        var skipped = 0
        val toSchedule = mutableListOf<ReminderEntity>()

        db.withTransaction {
            if (mode == RestoreMode.REPLACE) dao.deleteAll()

            // Map backup ids to ids on this device: same id, or an existing record with the same name.
            val expenseCategoryIds = HashMap<String, String>()
            val existingExpense = dao.expenseCategories()
            data.expenseCategories.forEach { c ->
                val match = existingExpense.firstOrNull { it.id == c.id }
                    ?: existingExpense.firstOrNull { it.type == c.type && it.name.equals(c.name, ignoreCase = true) }
                if (match != null) {
                    expenseCategoryIds[c.id] = match.id
                    skipped++
                } else {
                    dao.insert(c)
                    expenseCategoryIds[c.id] = c.id
                    added++
                }
            }
            val taskCategoryIds = HashMap<String, String>()
            val existingTaskCats = dao.taskCategories()
            data.taskCategories.forEach { c ->
                val match = existingTaskCats.firstOrNull { it.id == c.id || it.name.equals(c.name, ignoreCase = true) }
                if (match != null) {
                    taskCategoryIds[c.id] = match.id
                    skipped++
                } else {
                    dao.insert(c)
                    taskCategoryIds[c.id] = c.id
                    added++
                }
            }
            val tagIds = HashMap<String, String>()
            val existingTags = dao.journalTags()
            data.journalTags.forEach { t ->
                val match = existingTags.firstOrNull { it.id == t.id || it.name.equals(t.name, ignoreCase = true) }
                if (match != null) {
                    tagIds[t.id] = match.id
                    skipped++
                } else {
                    dao.insert(t)
                    tagIds[t.id] = t.id
                    added++
                }
            }

            val insertedEntries = HashSet<String>()
            data.journalEntries.forEach { e ->
                if (dao.insert(e) > 0) {
                    insertedEntries += e.id
                    added++
                } else {
                    skipped++
                }
            }
            // Tag links only for entries this restore added, so existing entries are never modified.
            data.journalEntryTags.filter { it.entryId in insertedEntries }.forEach { link ->
                dao.insert(link.copy(tagId = tagIds.getValue(link.tagId)))
            }

            data.transactions.forEach { t ->
                if (dao.insert(t.copy(categoryId = expenseCategoryIds.getValue(t.categoryId))) > 0) added++ else skipped++
            }
            val existingBudgetScopes = dao.budgets().map { it.yearMonth to it.categoryId }.toHashSet()
            data.budgets.forEach { b ->
                val mapped = b.copy(categoryId = b.categoryId?.let(expenseCategoryIds::getValue))
                if (mapped.yearMonth to mapped.categoryId in existingBudgetScopes || dao.insert(mapped) <= 0) skipped++ else added++
            }
            data.tasks.forEach { t ->
                // Also skipped if the same recurring occurrence already exists (unique series key).
                if (dao.insert(t.copy(categoryId = t.categoryId?.let(taskCategoryIds::getValue))) > 0) added++ else skipped++
            }

            val taskIdsHere = dao.tasks().map { it.id }.toHashSet()
            var requestCode = db.reminderDao().nextRequestCode()
            val existingReminderIds = dao.reminders().map { it.id }.toHashSet()
            data.reminders.forEach { r ->
                if (r.id in existingReminderIds || (r.taskId != null && r.taskId !in taskIdsHere)) {
                    skipped++
                    return@forEach
                }
                // Schedules are recomputed from now; a one-time reminder already in the past stays off.
                val next = if (r.enabled) ReminderSchedule.nextOccurrenceAfter(r.startAt, ZoneId.of(r.zoneId), r.recurrence, now) else null
                val restored = r.copy(requestCode = requestCode++, nextTriggerAt = next, enabled = r.enabled && next != null)
                dao.insert(restored)
                if (restored.enabled) toSchedule += restored
                added++
            }
        }

        // The database is committed; now bring the OS alarms in line.
        if (mode == RestoreMode.REPLACE) {
            previousReminders.forEach(scheduler::cancel)
            cancelNotifications()
            data.settings?.let { s ->
                settings.setThemeMode(s.themeMode)
                settings.setCurrency(s.currencyCode)
                settings.setDisplayZone(s.displayZoneId)
                settings.setTimeFormat(s.timeFormat)
            }
        }
        toSchedule.forEach { scheduler.schedule(it, it.nextTriggerAt!!) }
        RestoreReport(added, skipped, toSchedule.size)
    }

    companion object {
        /** Generous upper bound (years of text entries are a few MB); protects against reading huge wrong files. */
        const val MAX_BYTES = 50 * 1024 * 1024
    }
}

/** InputStream.readNBytes is API 33+; read up to [limit] bytes on older devices too. */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0
    while (total < limit) {
        val n = read(buffer, 0, minOf(buffer.size, limit - total))
        if (n < 0) break
        out.write(buffer, 0, n)
        total += n
    }
    return out.toByteArray()
}
