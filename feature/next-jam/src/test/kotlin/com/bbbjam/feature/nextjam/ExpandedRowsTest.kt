package com.bbbjam.feature.nextjam

import androidx.compose.runtime.saveable.SaverScope
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedRowsTest {

    private val october = LocalDate.of(2026, 10, 31)
    private val november = LocalDate.of(2026, 11, 28)
    private val crossroads = SongId("crossroads")
    private val hoochie = SongId("hoochie")
    private val thrill = SongId("thrill")

    @Test
    fun `nothing is expanded at first`() {
        assertFalse(ExpandedRows.NONE.isExpanded(october, crossroads))
    }

    @Test
    fun `a toggle expands one song and a second toggle collapses it`() {
        val once = ExpandedRows.NONE.toggle(october, hoochie)
        assertTrue(once.isExpanded(october, hoochie))
        assertFalse(once.isExpanded(october, crossroads))
        assertFalse(once.toggle(october, hoochie).isExpanded(october, hoochie))
    }

    @Test
    fun `two songs stay expanded together and collapse independently`() {
        val both = ExpandedRows.NONE.toggle(october, crossroads).toggle(october, thrill)
        assertEquals(ExpandedRows(october, setOf("crossroads", "thrill")), both)
        assertEquals(ExpandedRows(october, setOf("crossroads")), both.toggle(october, thrill))
    }

    @Test
    fun `another jam date reads as collapsed and a toggle there starts afresh`() {
        val expanded = ExpandedRows.NONE.toggle(october, crossroads).toggle(october, hoochie)
        assertFalse(expanded.isExpanded(november, crossroads))
        assertEquals(ExpandedRows(november, setOf("thrill")), expanded.toggle(november, thrill))
    }

    @Test
    fun `the saver round-trips through bundle-safe values`() {
        val scope = SaverScope { true }
        listOf(ExpandedRows.NONE, ExpandedRows(october, setOf("crossroads", "thrill", "pride"))).forEach { value ->
            val saved = with(ExpandedRows.Saver) { scope.save(value) }
            requireNotNull(saved)
            assertEquals(value, ExpandedRows.Saver.restore(saved))
        }
    }
}
