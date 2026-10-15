package com.bbbjam.feature.pastjams

import com.bbbjam.core.ui.theme.BluesJamColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The admin section's colours: neutral roles, and amber only on `Publicar lista` (D-17). */
class JamsAdminDefaultsTest {
    private val colors = BluesJamColors
    private val neutral = setOf(colors.surface, colors.surfaceRaised, colors.text, colors.textMuted, colors.error)

    @Test
    fun `cards, headings, notes and failures use neutral roles only`() {
        val card = JamsAdminDefaults.card()
        assertEquals(
            JamsAdminDefaults.CardStyle(
                colors.surface,
                colors.text,
                colors.textMuted,
                colors.textMuted,
                colors.textMuted,
            ),
            card,
        )
        val failure = JamsAdminDefaults.failure()
        assertEquals(
            JamsAdminDefaults.FailureStyle(colors.surface, colors.error, colors.textMuted, colors.text),
            failure,
        )
        val all = listOf(
            card.fill, card.date, card.venue, card.count, card.note,
            failure.container, failure.title, failure.message, failure.action,
            JamsAdminDefaults.heading(), JamsAdminDefaults.status(),
        )
        assertTrue(all.all { it in neutral })
    }

    @Test
    fun `Publicar lista is the primary action pair`() {
        assertEquals(
            JamsAdminDefaults.PublishStyle(colors.primaryAction, colors.onPrimaryAction),
            JamsAdminDefaults.publish(),
        )
    }

    @Test
    fun `Nueva jam button uses neutral roles only, never amber`() {
        val button = JamsAdminDefaults.newJamButton()
        assertEquals(JamsAdminDefaults.ButtonStyle(colors.surfaceRaised, colors.text), button)
        val all = listOf(button.fill, button.content)
        assertTrue(all.all { it in neutral || it == colors.border })
    }
}
