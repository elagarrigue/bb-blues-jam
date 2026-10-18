package com.bbbjam.core.ui.status

import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.ui.text.text
import com.bbbjam.core.ui.theme.BluesJamColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class JamStatusBadgeTest {
    @Test
    fun `each status has its label and only publicada is published`() {
        val badges = JamStatus.entries.map { jamStatusBadge(it) }
        assertEquals(listOf("Borrador", "Creada", "Publicada"), badges.map { it.label.text() })
        assertEquals(listOf(false, false, true), badges.map { it.isPublished })
    }

    @Test
    fun `the muted badge is textMuted on surfaceRaised and the published one uses the publication roles`() {
        val colors = BluesJamColors
        val muted = JamStatusBadgeDefaults.style(isPublished = false)
        assertEquals(colors.surfaceRaised, muted.fill)
        assertEquals(colors.textMuted, muted.content)
        val published = JamStatusBadgeDefaults.style(isPublished = true)
        assertEquals(colors.published, published.fill)
        assertEquals(colors.onPublished, published.content)
        assertNotEquals(muted.fill, published.fill)
    }
}
