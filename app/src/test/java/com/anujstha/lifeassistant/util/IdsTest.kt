package com.anujstha.lifeassistant.util

import com.anujstha.lifeassistant.domain.model.RecurrenceFrequency
import com.anujstha.lifeassistant.domain.model.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class IdsTest {

    @Test
    fun escapeLike_escapesWildcardsAndBackslash() {
        assertEquals("50\\%", escapeLike("50%"))
        assertEquals("a\\_b", escapeLike("a_b"))
        assertEquals("c:\\\\x", escapeLike("c:\\x"))
        assertEquals("plain text", escapeLike("plain text"))
    }

    @Test
    fun randomIds_areUnique() {
        val ids = List(1_000) { IdGenerator.Random.newId() }
        assertEquals(ids.size, ids.toSet().size)
        assertNotEquals(ids[0], ids[1])
    }

    @Test(expected = IllegalArgumentException::class)
    fun recurrenceRule_rejectsZeroInterval() {
        RecurrenceRule(RecurrenceFrequency.DAILY, interval = 0)
    }
}
