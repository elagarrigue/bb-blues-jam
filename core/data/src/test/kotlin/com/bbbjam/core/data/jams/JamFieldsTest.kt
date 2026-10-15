package com.bbbjam.core.data.jams

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JamFieldsTest {
    @Test
    fun `a time is exactly HH colon MM within the day`() {
        listOf("00:00", "09:30", "21:00", "23:59").forEach { assertTrue(it, isValidTime(it)) }
        listOf("", "9:30", "24:00", "21:60", "21.00", "21:0", " 21:00", "21:000").forEach {
            assertFalse(it, isValidTime(it))
        }
    }

    @Test
    fun `a venue is trimmed and its inner spaces collapsed`() {
        assertEquals("La Macanuda", normalizeVenue("  La   Macanuda "))
        assertEquals("a".repeat(MAX_VENUE_LENGTH), normalizeVenue("a".repeat(MAX_VENUE_LENGTH)))
        assertNull(venueProblem("La Macanuda"))
    }

    @Test
    fun `each unusable venue says why`() {
        assertEquals(VenueProblem.EMPTY, venueProblem("   "))
        assertEquals(VenueProblem.EMPTY, venueProblem(""))
        assertEquals(VenueProblem.TOO_LONG, venueProblem("a".repeat(MAX_VENUE_LENGTH + 1)))
        listOf("=SUM(A1)", "+1", "-x", "@here", "  =x").forEach {
            assertEquals(it, VenueProblem.FORMULA, venueProblem(it))
        }
        assertEquals(VenueProblem.CONTROL, venueProblem("La\nMacanuda"))
        listOf("", "   ", "=x", "a".repeat(MAX_VENUE_LENGTH + 1), "a\tb").forEach { assertNull(it, normalizeVenue(it)) }
    }
}
