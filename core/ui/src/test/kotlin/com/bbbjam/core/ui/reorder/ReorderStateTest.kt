package com.bbbjam.core.ui.reorder

import androidx.compose.foundation.lazy.LazyListState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReorderStateTest {
    private fun state() = ReorderState(LazyListState(), { { true } }, { { _, _ -> } })

    @Test
    fun `a new list never blocks actions before a drag`() {
        val state = state()
        listOf(0L, 1L, 1_000_000_000L, Long.MAX_VALUE).forEach { now ->
            assertFalse("Fresh list at $now must allow row actions", state.recentlyDragged(now))
        }
    }

    @Test
    fun `returning to a recreated list allows actions without needing a drag`() {
        val original = state()
        original.finish(commit = false)
        assertTrue(original.recentlyDragged())
        assertFalse(state().recentlyDragged())
    }

    @Test
    fun `release guard expires after 300 milliseconds`() {
        val state = state()
        val beforeRelease = System.nanoTime()
        state.finish(commit = false)
        val afterRelease = System.nanoTime()
        assertTrue(state.recentlyDragged(beforeRelease))
        assertFalse(state.recentlyDragged(afterRelease + 300_000_000L))
    }
}
