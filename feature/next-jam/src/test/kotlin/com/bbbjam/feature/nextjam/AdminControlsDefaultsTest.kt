package com.bbbjam.feature.nextjam

import com.bbbjam.core.ui.theme.BluesJamColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The admin controls' treatment (`admin-add-song-to-setlist`, V1): muted roles and `error` only,
 * never amber. As in `DraftSetlistDefaultsTest`, "never amber" is proven by the allowed roles here
 * together with `BluesJamColorsTest` in `:core:ui`; no amber role is named (Konsist reads tests).
 */
class AdminControlsDefaultsTest {
    private val colors = BluesJamColors
    private val allowed =
        setOf(colors.surface, colors.surfaceRaised, colors.text, colors.textMuted, colors.error)

    @Test
    fun `every colour is a surface, text, muted text or error`() {
        val add = AdminControlsDefaults.addButton()
        val pending = AdminControlsDefaults.pendingRow()
        val failure = AdminControlsDefaults.failureCard()
        val draft = AdminControlsDefaults.draft()
        val removal = AdminControlsDefaults.removal()
        val keyChange = AdminControlsDefaults.keyChange()
        listOf(
            keyChange.action, keyChange.status,
            removal.action, removal.prompt, removal.details, removal.confirm, removal.cancel, removal.status,
            add.container, add.content, pending.container, pending.content,
            failure.container, failure.title, failure.message, failure.action,
            draft.badgeFill, draft.badgeText, draft.note,
        ).forEach { color -> assertTrue("$color is not an allowed role", color in allowed) }
    }

    @Test
    fun `the roles are the specified ones`() {
        assertEquals(AdminControlsDefaults.Fill(colors.surfaceRaised, colors.text), AdminControlsDefaults.addButton())
        assertEquals(AdminControlsDefaults.Fill(colors.surface, colors.textMuted), AdminControlsDefaults.pendingRow())
        assertEquals(
            AdminControlsDefaults.FailureStyle(colors.surface, colors.error, colors.textMuted, colors.text),
            AdminControlsDefaults.failureCard(),
        )
        // Removing a song: the destructive action and "Quitar" in error, never amber.
        assertEquals(
            AdminControlsDefaults.RemovalStyle(
                action = colors.error,
                prompt = colors.text,
                details = colors.textMuted,
                confirm = colors.error,
                cancel = colors.text,
                status = colors.textMuted,
            ),
            AdminControlsDefaults.removal(),
        )
        // Setting a key: a quiet text action and a muted status line (`admin-set-key`).
        assertEquals(
            AdminControlsDefaults.KeyChangeStyle(action = colors.text, status = colors.textMuted),
            AdminControlsDefaults.keyChange(),
        )
        // The badge is the musician draft card's badge (`badge-draft`).
        val card = DraftSetlistDefaults.style()
        val draft = AdminControlsDefaults.draft()
        assertEquals(card.badgeFill, draft.badgeFill)
        assertEquals(card.badgeText, draft.badgeText)
    }
}
