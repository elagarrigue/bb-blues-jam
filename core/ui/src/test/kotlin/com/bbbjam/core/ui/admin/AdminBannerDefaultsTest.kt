package com.bbbjam.core.ui.admin

import com.bbbjam.core.ui.theme.BluesJamColors
import org.junit.Assert.assertEquals
import org.junit.Test

/** The banner's fill and content colours never use an amber role (D-17). */
class AdminBannerDefaultsTest {

    private val colors = BluesJamColors

    @Test
    fun `the banner is a raised surface with text content`() {
        assertEquals(colors.surfaceRaised, AdminBannerDefaults.fill())
        assertEquals(colors.text, AdminBannerDefaults.content())
    }

    @Test
    fun `the banner label is the approved copy`() {
        assertEquals("MODO ADMIN", AdminBannerCopy.LABEL)
    }
}
