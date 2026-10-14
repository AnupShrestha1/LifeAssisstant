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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

class BackupCodecTest {

    private val t0 = Instant.parse("2026-10-14T06:00:00Z")

    private fun sample() = BackupData(
        settings = UserSettings(ThemeMode.DARK, "USD", "Europe/London", TimeFormat.H24),
        journalTags = listOf(JournalTagEntity("tag1", "Travel", t0)),
        journalEntries = listOf(JournalEntryEntity("e1", "Trip", "Line 1\n\nLine \"3\", with comma", LocalDate.of(2026, 10, 1), Mood.HAPPY, t0, t0.plusSeconds(60))),
        journalEntryTags = listOf(JournalEntryTagCrossRef("e1", "tag1")),
        expenseCategories = listOf(
            ExpenseCategoryEntity("c1", "Food", TransactionType.EXPENSE, true, null, t0, t0),
            ExpenseCategoryEntity("c2", "Old", TransactionType.EXPENSE, false, t0, t0, t0),
        ),
        transactions = listOf(TransactionEntity("x1", 123_450, "NPR", TransactionType.EXPENSE, "c1", LocalDate.of(2026, 10, 2), LocalTime.of(9, 30), PaymentMethod.MOBILE_WALLET, "Momo", t0, t0)),
        budgets = listOf(BudgetEntity("b1", YearMonth.of(2026, 10), null, 50_000_00, "NPR", 80, t0, t0)),
        taskCategories = listOf(TaskCategoryEntity("tc1", "Home", null, t0, t0)),
        tasks = listOf(
            TaskEntity("t1", "Rent", null, TaskStatus.PENDING, Priority.HIGH, LocalDate.of(2026, 10, 31), null, "tc1", null, "s1", LocalDate.of(2026, 1, 31), LocalDate.of(2026, 10, 31), RecurrenceRule(RecurrenceFrequency.MONTHLY), t0, t0),
        ),
        reminders = listOf(
            ReminderEntity("r1", 0, "Pay rent", "note", "t1", LocalDateTime.of(2026, 10, 31, 9, 0), "Asia/Kathmandu", null, null, true, t0, t0),
        ),
    )

    private fun valid(text: String) = BackupCodec.decode(text) as DecodeResult.Valid
    private fun errors(text: String) = (BackupCodec.decode(text) as DecodeResult.Invalid).errors

    @Test
    fun roundTrip_preservesEverything() {
        val data = sample()
        val decoded = valid(BackupCodec.encode(data, "0.10.0", t0))
        assertEquals(data, decoded.data)
        assertEquals("0.10.0", decoded.appVersion)
        assertEquals(t0, decoded.exportedAt)
    }

    @Test
    fun emptyBackup_isValid() {
        assertEquals(0, valid(BackupCodec.encode(BackupData(), "1", t0)).data.totalRecords)
    }

    @Test
    fun notJson_orNotOurFile_isRejected() {
        assertTrue(errors("not json at all").single().contains("not a Life Assistant backup"))
        assertTrue(errors("""{"format":"something-else","formatVersion":1}""").single().contains("not a Life Assistant backup"))
        assertTrue(errors("""{"formatVersion":1}""").isNotEmpty()) // missing marker
    }

    @Test
    fun newerFormatVersion_isRejectedWithExplanation() {
        val text = BackupCodec.encode(sample(), "9", t0).replace("\"formatVersion\": 1", "\"formatVersion\": 2")
        assertTrue(errors(text).single().contains("newer version"))
    }

    @Test
    fun unknownFields_areIgnored() {
        val text = BackupCodec.encode(sample(), "1", t0).replaceFirst("{", "{\"futureField\": {\"x\": 1},")
        assertEquals(sample(), valid(text).data)
    }

    @Test
    fun brokenReferences_areReportedWithLocation() {
        val data = sample().copy(transactions = sample().transactions.map { it.copy(categoryId = "missing") })
        val e = errors(BackupCodec.encode(data, "1", t0))
        assertTrue(e.toString(), e.any { it.startsWith("transactions[0].categoryId") })
    }

    @Test
    fun typeMismatch_betweenTransactionAndCategory_isRejected() {
        val data = sample().copy(transactions = sample().transactions.map { it.copy(type = TransactionType.INCOME) })
        assertTrue(errors(BackupCodec.encode(data, "1", t0)).any { it.contains("type doesn't match") })
    }

    @Test
    fun duplicateIds_andDuplicateNames_areRejected() {
        val dupIds = sample().copy(journalTags = sample().journalTags + JournalTagEntity("tag1", "Other", t0))
        assertTrue(errors(BackupCodec.encode(dupIds, "1", t0)).any { it.contains("duplicate id \"tag1\"") })
        val dupNames = sample().copy(taskCategories = sample().taskCategories + TaskCategoryEntity("tc2", "home", null, t0, t0))
        assertTrue(errors(BackupCodec.encode(dupNames, "1", t0)).any { it.contains("duplicate name") })
    }

    @Test
    fun invalidValues_areRejected() {
        val base = BackupCodec.encode(sample(), "1", t0)
        assertTrue(errors(base.replace("\"amountMinor\": 123450", "\"amountMinor\": -5")).any { it.contains("amountMinor") })
        assertTrue(errors(base.replace("\"mood\": \"HAPPY\"", "\"mood\": \"ECSTATIC\"")).any { it.contains("unknown value \"ECSTATIC\"") })
        assertTrue(errors(base.replace("\"entryDate\": \"2026-10-01\"", "\"entryDate\": \"2026-13-01\"")).any { it.contains("invalid date") })
        assertTrue(errors(base.replace("\"zoneId\": \"Asia/Kathmandu\"", "\"zoneId\": \"Mars/Base\"")).any { it.contains("unknown time zone") })
        assertTrue(errors(base.replace("\"currencyCode\": \"NPR\",\n            \"type\"", "\"currencyCode\": \"XXQ\",\n            \"type\"")).isNotEmpty())
    }

    @Test
    fun manyProblems_areCappedButCounted() {
        val data = sample().copy(transactions = (1..30).map { sample().transactions[0].copy(id = "x$it", categoryId = "missing") })
        val e = errors(BackupCodec.encode(data, "1", t0))
        assertEquals(21, e.size)
        assertTrue(e.last().contains("10 more problems"))
    }
}
