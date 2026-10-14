package com.anujstha.lifeassistant.ui.journal

import com.anujstha.lifeassistant.data.entity.JournalEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class JournalLabelsTest {

    private fun entry(title: String, body: String) =
        JournalEntryEntity("id", title, body, LocalDate.of(2026, 10, 14), null, Instant.EPOCH, Instant.EPOCH)

    @Test
    fun headline_prefersTitle() {
        assertEquals("Trip", entry("Trip", "Went to Pokhara").headline())
    }

    @Test
    fun headline_fallsBackToFirstNonBlankLine() {
        assertEquals("Went to Pokhara", entry("", "\n\n  Went to Pokhara  \nGreat lake").headline())
    }

    @Test
    fun headline_nullWhenEverythingBlank() {
        assertNull(entry(" ", "\n ").headline())
    }

    @Test
    fun preview_collapsesLines_andSkipsHeadlineLine() {
        assertEquals("Great lake Boating", entry("", "Went to Pokhara\nGreat lake\n\nBoating").preview())
        assertEquals("Went to Pokhara Great lake", entry("Trip", "Went to Pokhara\nGreat lake").preview())
    }
}
