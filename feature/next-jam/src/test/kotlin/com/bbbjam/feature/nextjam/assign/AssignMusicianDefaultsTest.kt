package com.bbbjam.feature.nextjam.assign

import com.bbbjam.core.model.Instrument
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.core.ui.theme.BluesJamColors
import com.bbbjam.feature.nextjam.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The assignment screen's slot names and field roles (`ui-polish-pass`, U12). */
class AssignMusicianDefaultsTest {
    private val colors = BluesJamColors

    /** Neutral roles only: `BluesJamColorsTest` in `:core:ui` fails if one of these ever becomes amber. */
    private val neutral = setOf(colors.surface, colors.surfaceRaised, colors.text, colors.textMuted, colors.border)

    @Test
    fun `slot names number only guitars`() {
        assertEquals(
            UiText.res(R.string.assign_musician_slot_numbered, UiText.res(CoreR.string.instrument_guitar), 2),
            AssignMusicianDefaults.slotName(Instrument.GUITAR, 2),
        )
        assertEquals(UiText.res(CoreR.string.instrument_bass), AssignMusicianDefaults.slotName(Instrument.BASS, 1))
        assertEquals(
            UiText.res(CoreR.string.instrument_keyboards),
            AssignMusicianDefaults.slotName(Instrument.KEYBOARDS, 1),
        )
    }

    @Test
    fun `the name field has the login field's neutral roles`() {
        val field = AssignMusicianDefaults.field()
        assertEquals(colors.surface, field.container)
        assertEquals(colors.text, field.text)
        assertEquals(colors.textMuted, field.label)
        assertEquals(colors.border, field.unfocusedBorder)
        assertEquals(colors.text, field.focusedBorder)
        assertEquals(colors.text, field.cursor)
        listOf(field.container, field.text, field.label, field.unfocusedBorder, field.focusedBorder, field.cursor)
            .forEach { color -> assertTrue("$color is not a neutral role", color in neutral) }
    }
}
