package com.bbbjam.core.ui.theme

import androidx.compose.ui.graphics.Color
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** The window color is the one XML color left; it must not drift from the background token. */
class WindowBackgroundTest {

    @Test
    fun `window background resource equals the background token`() {
        // Unit tests run with the module directory as the working directory.
        val xml = File("src/main/res/values/colors.xml").readText()
        val hex = requireNotNull(WINDOW_BACKGROUND.find(xml)) {
            "bluesjam_window_background not found in colors.xml"
        }.groupValues[1]
        assertEquals(BluesJamPalette.Background, Color(hex.toLong(radix = 16)))
    }

    private companion object {
        val WINDOW_BACKGROUND = Regex("""<color name="bluesjam_window_background">#([0-9A-Fa-f]{8})</color>""")
    }
}
