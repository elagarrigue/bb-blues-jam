package com.bbbjam.core.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The type scale matches `DESIGN.md`, and Material components only ever draw the two bundled families. */
class BluesJamTypographyTest {

    @Test
    fun `the five styles match the design tokens`() {
        val barlow = BluesJamFonts.BarlowCondensed
        val chivo = BluesJamFonts.Chivo
        assertStyle("h1", BluesJamTypography.h1, barlow, 28.sp, FontWeight.Bold)
        assertStyle("songTitle", BluesJamTypography.songTitle, barlow, 24.sp, FontWeight.SemiBold)
        assertStyle("key", BluesJamTypography.key, barlow, 44.sp, FontWeight.ExtraBold)
        assertStyle("body", BluesJamTypography.body, chivo, 16.sp, FontWeight.Normal)
        assertStyle("caption", BluesJamTypography.caption, chivo, 12.sp, FontWeight.Normal)
    }

    @Test
    fun `keyDisplay is Barlow Condensed ExtraBold 96sp on a 96sp line`() {
        val style = BluesJamTypography.keyDisplay
        assertStyle("keyDisplay", style, BluesJamFonts.BarlowCondensed, 96.sp, FontWeight.ExtraBold)
        assertEquals("keyDisplay line height", 96.sp, style.lineHeight)
        assertEquals("read through the theme", style, BluesJamTheme.typography.keyDisplay)
    }

    @Test
    fun `every Material typography style uses a bundled family`() {
        val typography = BluesJamMaterial.typography
        val styles = typography.javaClass.methods
            .filter { it.parameterCount == 0 && it.returnType == TextStyle::class.java && it.name.startsWith("get") }
            .associate { it.name.removePrefix("get") to it.invoke(typography) as TextStyle }
        assertEquals("Material Typography styles found by reflection: ${styles.keys}", 15, styles.size)
        val families = setOf(BluesJamFonts.BarlowCondensed, BluesJamFonts.Chivo)
        val foreign = styles.filterValues { it.fontFamily !in families }.keys
        assertTrue("Material styles with a family that is not bundled: $foreign", foreign.isEmpty())
    }

    private fun assertStyle(name: String, style: TextStyle, family: FontFamily, size: TextUnit, weight: FontWeight) {
        assertEquals("$name family", family, style.fontFamily)
        assertEquals("$name size", size, style.fontSize)
        assertEquals("$name weight", weight, style.fontWeight)
    }
}
