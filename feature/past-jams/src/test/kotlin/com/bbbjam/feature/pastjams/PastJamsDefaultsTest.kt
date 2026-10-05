package com.bbbjam.feature.pastjams

import com.bbbjam.core.ui.theme.BluesJamColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The archive treatment of the list (DESIGN.md "Past jams list"): muted roles only, never amber.
 * "Never amber" is proven by the first test together with `BluesJamColorsTest` in `:core:ui`, which
 * fails if `surface`, `textMuted` or `archive` ever becomes amber. The module may read `key` since
 * `past-jam-detail` (its detail rows), and only there; the list reads no amber role.
 */
class PastJamsDefaultsTest {
    private val colors = BluesJamColors
    private val style = PastJamsDefaults.rowStyle()
    private val all = listOf(style.fill, style.date, style.venue, style.count, style.hook, style.message)

    @Test
    fun `every row colour is surface, muted text or archive`() {
        val allowed = setOf(colors.surface, colors.textMuted, colors.archive)
        all.forEach { color -> assertTrue("$color is not an archive role", color in allowed) }
    }

    @Test
    fun `the roles are the specified ones`() {
        assertEquals(
            PastJamsDefaults.RowStyle(
                fill = colors.surface,
                date = colors.textMuted,
                venue = colors.textMuted,
                count = colors.textMuted,
                hook = colors.archive,
                message = colors.textMuted,
            ),
            style,
        )
    }
}
