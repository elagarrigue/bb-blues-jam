package com.bbbjam.feature.nextjam

import com.bbbjam.core.ui.theme.BluesJamColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The picker's treatment (`admin-add-song-to-setlist`, V1): amber only through `key`, on the key of a
 * song that can be added; a listed song and the search field read no amber role. `key` is the one
 * amber role this module may name (`AMBER_ROLE_ALLOWLIST`).
 */
class AddSongDefaultsTest {
    private val colors = BluesJamColors
    private val muted = setOf(colors.surface, colors.surfaceRaised, colors.text, colors.textMuted, colors.border)

    @Test
    fun `an addable row reads key only for its key`() {
        assertEquals(
            AddSongDefaults.RowStyle(colors.surface, colors.text, colors.textMuted, colors.key, colors.textMuted),
            AddSongDefaults.row(isListed = false),
        )
    }

    @Test
    fun `a listed row is muted throughout`() {
        val style = AddSongDefaults.row(isListed = true)
        listOf(style.container, style.title, style.artist, style.key, style.note).forEach { color ->
            assertTrue("$color is not muted", color in muted)
        }
    }

    @Test
    fun `the search field has no amber`() {
        val field = AddSongDefaults.field()
        listOf(field.container, field.text, field.label, field.unfocusedBorder, field.focusedBorder, field.cursor)
            .forEach { color -> assertTrue("$color is not muted", color in muted) }
    }
}
