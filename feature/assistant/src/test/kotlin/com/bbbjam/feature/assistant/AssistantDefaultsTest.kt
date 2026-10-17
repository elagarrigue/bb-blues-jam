package com.bbbjam.feature.assistant

import com.bbbjam.core.ui.theme.BluesJamColors
import com.bbbjam.feature.assistant.apply.ApplyDefaults
import com.bbbjam.feature.assistant.apply.ApplyStatusKind
import com.bbbjam.feature.assistant.apply.ApplyTone
import com.bbbjam.feature.assistant.apply.summary
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantDefaultsTest {
    private val colors = BluesJamColors

    // Every non-amber colour is pinned to a named non-amber token with assertEquals; the amber roles
    // this module may read are only the three it is allowlisted for (Konsist reads test sources too),
    // and BluesJamColorsTest guards which tokens are amber.

    @Test
    fun `cards use surfaceRaised for the request and surface for the reply`() {
        assertEquals(colors.surfaceRaised, AssistantDefaults.requestCard().fill)
        assertEquals(colors.surface, AssistantDefaults.replyCard().fill)
        assertEquals(colors.textMuted, AssistantDefaults.requestCard().caption)
        assertEquals(colors.text, AssistantDefaults.replyCard().text)
    }

    @Test
    fun `the key is the only amber on a proposed song`() {
        val song = AssistantDefaults.song()
        assertEquals(colors.key, song.key)
        assertEquals(colors.textMuted, song.position)
        assertEquals(colors.text, song.title)
        assertEquals(colors.textMuted, song.artist)
        assertEquals(colors.text, song.reason)
    }

    @Test
    fun `notices and actions are never amber`() {
        val notice = AssistantDefaults.notice()
        assertEquals(colors.error, notice.failure)
        assertEquals(colors.textMuted, notice.cancelled)
        assertEquals(colors.text, notice.action)
    }

    @Test
    fun `amber is on the send button only while it is enabled`() {
        val enabled = AssistantDefaults.sendButton(enabled = true)
        assertEquals(colors.primaryAction, enabled.fill)
        assertEquals(colors.onPrimaryAction, enabled.content)
        val disabled = AssistantDefaults.sendButton(enabled = false)
        assertEquals(colors.surfaceRaised, disabled.fill)
        assertEquals(colors.textMuted, disabled.content)
    }

    @Test
    fun `the field has the admin login field's colours and no amber`() {
        val field = AssistantDefaults.field()
        assertEquals(colors.surface, field.container)
        assertEquals(colors.text, field.text)
        assertEquals(colors.textMuted, field.label)
        assertEquals(colors.border, field.unfocusedBorder)
        assertEquals(colors.text, field.focusedBorder)
        assertEquals(colors.text, field.cursor)
    }

    @Test
    fun `the service banner is a surfaceRaised card with text copy, never amber`() {
        val banner = AssistantDefaults.banner()
        assertEquals(colors.surfaceRaised, banner.fill)
        assertEquals(colors.text, banner.text)
        assertEquals(colors.text, banner.action)
        assertEquals(colors.textMuted, banner.checking)
    }

    @Test
    fun `the apply button and dialog are secondary and never amber`() {
        val enabled = ApplyDefaults.applyButton(enabled = true)
        assertEquals(colors.surfaceRaised, enabled.fill)
        assertEquals(colors.text, enabled.content)
        val disabled = ApplyDefaults.applyButton(enabled = false)
        assertEquals(colors.surface, disabled.fill)
        assertEquals(colors.textMuted, disabled.content)
        val dialog = ApplyDefaults.applyDialog()
        assertEquals(colors.surfaceRaised, dialog.container)
        assertEquals(colors.text, dialog.title)
        assertEquals(colors.textMuted, dialog.lines)
        assertEquals(colors.text, dialog.confirm)
        assertEquals(colors.text, dialog.cancel)
        assertEquals(colors.textMuted, ApplyDefaults.applyNote())
    }

    @Test
    fun `a destructive dialog draws only its confirm in error`() {
        val dialog = ApplyDefaults.applyDialog(destructive = true)
        assertEquals(colors.error, dialog.confirm)
        assertEquals(colors.text, dialog.cancel)
        assertEquals(colors.text, dialog.title)
        assertEquals(colors.surfaceRaised, dialog.container)
    }

    @Test
    fun `the summary and status colours follow the tone`() {
        assertEquals(colors.text, ApplyDefaults.summary(ApplyTone.NEUTRAL))
        assertEquals(colors.text, ApplyDefaults.summary(ApplyTone.SUCCESS))
        assertEquals(colors.error, ApplyDefaults.summary(ApplyTone.FAILURE))
        assertEquals(colors.textMuted, ApplyDefaults.summary(ApplyTone.MUTED))
        assertEquals(colors.error, ApplyDefaults.status(ApplyStatusKind.FAILED))
        for (kind in listOf(
            ApplyStatusKind.PROGRESS,
            ApplyStatusKind.DONE,
            ApplyStatusKind.SKIPPED,
            ApplyStatusKind.NOT_ATTEMPTED,
        )) {
            assertEquals(colors.textMuted, ApplyDefaults.status(kind))
        }
    }
}
