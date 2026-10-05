package com.bbbjam.feature.nextjam

import com.bbbjam.core.ui.theme.BluesJamColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The draft card's treatment (`unpublished-setlist-state`, V1): muted roles only, never amber.
 * "Never amber" is proven by the first test together with `BluesJamColorsTest` in `:core:ui`, which
 * fails if `surface`, `surfaceRaised`, `text` or `textMuted` ever becomes amber. No amber role is
 * named here: Konsist `amber-roles-allowlisted` reads test sources, and this module may read `key`
 * only.
 */
class DraftSetlistDefaultsTest {
    private val colors = BluesJamColors
    private val style = DraftSetlistDefaults.style()

    @Test
    fun `every colour is surface, raised surface, text or muted text`() {
        val allowed = setOf(colors.surface, colors.surfaceRaised, colors.text, colors.textMuted)
        listOf(style.cardFill, style.badgeFill, style.badgeText, style.title, style.message).forEach { color ->
            assertTrue("$color is not a muted role", color in allowed)
        }
    }

    @Test
    fun `the roles are the specified ones`() {
        assertEquals(
            DraftSetlistDefaults.DraftStyle(
                cardFill = colors.surface,
                badgeFill = colors.surfaceRaised,
                badgeText = colors.textMuted,
                title = colors.text,
                message = colors.textMuted,
            ),
            style,
        )
    }

    @Test
    fun `the card has the surface fill the empty block lacks, and the badge stands out from it`() {
        assertEquals(colors.surface, style.cardFill)
        assertTrue(style.badgeFill != style.cardFill)
        assertTrue(style.cardFill != colors.background)
    }
}
