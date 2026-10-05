package com.bbbjam.core.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.outlined.Info
import com.bbbjam.core.ui.theme.BluesJamColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The bottom bar's colors and icons (`bottom-navigation`, I1): tokens by role, never amber. */
class TabBarDefaultsTest {

    private val colors = BluesJamColors
    private val bar = TabBarDefaults.colors()

    @Test
    fun `each bar color is its token`() {
        assertEquals(colors.surface, bar.container)
        assertEquals(colors.text, bar.selectedContent)
        assertEquals(colors.surfaceRaised, bar.indicator)
        assertEquals(colors.textMuted, bar.unselectedContent)
    }

    @Test
    fun `no bar color is an amber role`() {
        val amber = listOf(
            colors.primaryAction,
            colors.onPrimaryAction,
            colors.slotOpen,
            colors.key,
            colors.published,
            colors.onPublished,
            colors.activeFilter,
            colors.onActiveFilter,
        )
        listOf(bar.container, bar.selectedContent, bar.indicator, bar.unselectedContent).forEach { color ->
            amber.forEach { role -> assertNotEquals("$color is an amber role", role, color) }
        }
    }

    @Test
    fun `the icons are the approved ones`() {
        assertEquals(Icons.AutoMirrored.Filled.List, TabBarDefaults.icon(TabIcon.SETLIST))
        assertEquals(Icons.Filled.DateRange, TabBarDefaults.icon(TabIcon.ARCHIVE))
        assertEquals(Icons.Outlined.Info, TabBarDefaults.icon(TabIcon.INFO))
    }
}
