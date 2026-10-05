package com.bbbjam.feature.pastjams

import com.bbbjam.core.ui.theme.BluesJamColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The detail's archive treatment (DESIGN.md "Past jam detail"): muted roles everywhere, the key in
 * its amber `key` role and nowhere else. This module may name `key` only (`AMBER_ROLE_ALLOWLIST`).
 */
class PastJamDetailDefaultsTest {
    private val colors = BluesJamColors
    private val header = PastJamDetailDefaults.headerStyle()
    private val row = PastJamDetailDefaults.rowStyle()
    private val archive = setOf(colors.surface, colors.textMuted, colors.archive)

    @Test
    fun `every colour but the key is surface, muted text or archive`() {
        val nonKey = listOf(header.date, header.venue, header.count, header.message) +
            listOf(row.fill, row.position, row.title, row.artist, row.droppedNote)
        nonKey.forEach { color -> assertTrue("$color is not an archive role", color in archive) }
    }

    @Test
    fun `the key is the key role`() {
        assertEquals(colors.key, row.key)
    }

    @Test
    fun `the roles are the specified ones`() {
        assertEquals(
            PastJamDetailDefaults.RowStyle(
                fill = colors.surface,
                position = colors.textMuted,
                title = colors.textMuted,
                artist = colors.archive,
                key = colors.key,
                droppedNote = colors.textMuted,
            ),
            row,
        )
        assertEquals(
            PastJamDetailDefaults.HeaderStyle(colors.textMuted, colors.textMuted, colors.textMuted, colors.textMuted),
            header,
        )
    }
}
