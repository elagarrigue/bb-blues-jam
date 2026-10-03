package com.bbbjam.feature.nextjam

import androidx.compose.runtime.saveable.SaverScope
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedRowsTest {

    private val october = LocalDate.of(2026, 10, 31)
    private val november = LocalDate.of(2026, 11, 28)

    @Test
    fun `nothing is expanded at first`() {
        assertFalse(ExpandedRows.NONE.isExpanded(october, 1))
    }

    @Test
    fun `a toggle expands one position and a second toggle collapses it`() {
        val once = ExpandedRows.NONE.toggle(october, 2)
        assertTrue(once.isExpanded(october, 2))
        assertFalse(once.isExpanded(october, 1))
        assertFalse(once.toggle(october, 2).isExpanded(october, 2))
    }

    @Test
    fun `two positions stay expanded together and collapse independently`() {
        val both = ExpandedRows.NONE.toggle(october, 1).toggle(october, 3)
        assertEquals(ExpandedRows(october, setOf(1, 3)), both)
        assertEquals(ExpandedRows(october, setOf(1)), both.toggle(october, 3))
    }

    @Test
    fun `another jam date reads as collapsed and a toggle there starts afresh`() {
        val expanded = ExpandedRows.NONE.toggle(october, 1).toggle(october, 2)
        assertFalse(expanded.isExpanded(november, 1))
        assertEquals(ExpandedRows(november, setOf(4)), expanded.toggle(november, 4))
    }

    @Test
    fun `the saver round-trips through bundle-safe values`() {
        val scope = SaverScope { true }
        listOf(ExpandedRows.NONE, ExpandedRows(october, setOf(1, 3, 12))).forEach { value ->
            val saved = with(ExpandedRows.Saver) { scope.save(value) }
            requireNotNull(saved)
            assertEquals(value, ExpandedRows.Saver.restore(saved))
        }
    }
}
