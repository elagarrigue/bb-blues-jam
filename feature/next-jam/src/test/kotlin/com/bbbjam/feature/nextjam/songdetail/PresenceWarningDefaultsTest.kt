package com.bbbjam.feature.nextjam.songdetail

import androidx.compose.ui.graphics.luminance
import com.bbbjam.core.ui.theme.BluesJamColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceWarningDefaultsTest {
    @Test
    fun `pulse uses distinct red shades and its static endpoint is the error token`() {
        assertEquals(BluesJamColors.error, PresenceWarningDefaults.container(1f))
        assertNotEquals(PresenceWarningDefaults.container(0f), PresenceWarningDefaults.container(1f))
        assertEquals(BluesJamColors.background, PresenceWarningDefaults.content())
    }

    @Test
    fun `text has AA contrast throughout the pulse`() {
        val text = PresenceWarningDefaults.content().luminance()
        for (step in 0..100) {
            val fill = PresenceWarningDefaults.container(step / 100f)
            val contrast = (fill.luminance() + 0.05f) / (text + 0.05f)
            assertEquals(1f, fill.alpha, 0f)
            assertTrue("Pulse step $step contrast $contrast", contrast >= 4.5f)
        }
    }
}
