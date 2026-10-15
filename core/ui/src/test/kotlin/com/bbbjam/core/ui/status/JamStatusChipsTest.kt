package com.bbbjam.core.ui.status

import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.theme.BluesJamColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JamStatusChipsTest {
    private val none = EventHandler<JamStatusChipsUiModel.Event> {}

    @Test
    fun `the chips come in order with labels and state descriptions`() {
        val model = jamStatusChips(JamStatus.CREATED, enabled = setOf(JamStatus.DRAFT), events = none)
        assertEquals(listOf("Borrador", "Creada", "Publicada"), model.chips.map { it.label })
        assertEquals(
            listOf("Estado: Borrador", "Estado: Creada", "Estado: Publicada"),
            model.chips.map { it.description },
        )
        assertEquals(listOf(JamStatus.DRAFT, JamStatus.CREATED, JamStatus.PUBLISHED), model.chips.map { it.status })
    }

    @Test
    fun `only the current status is selected and only publicada is published`() {
        JamStatus.entries.forEach { current ->
            val model = jamStatusChips(current, enabled = emptySet(), events = none)
            assertEquals(listOf(current), model.chips.filter { it.selected }.map { it.status })
            assertEquals(listOf(JamStatus.PUBLISHED), model.chips.filter { it.isPublished }.map { it.status })
        }
    }

    @Test
    fun `a chip is enabled only when it is in the enabled set`() {
        val model = jamStatusChips(JamStatus.DRAFT, enabled = setOf(JamStatus.CREATED), events = none)
        assertEquals(listOf(false, true, false), model.chips.map { it.enabled })
        assertTrue(jamStatusChips(JamStatus.DRAFT, emptySet(), none).chips.none { it.enabled })
    }

    @Test
    fun `selecting an event reaches the handler`() {
        val seen = mutableListOf<JamStatusChipsUiModel.Event>()
        val model = jamStatusChips(JamStatus.DRAFT, setOf(JamStatus.CREATED), EventHandler { seen += it })
        model.events(JamStatusChipsUiModel.Event.Select(JamStatus.CREATED))
        assertEquals(listOf<JamStatusChipsUiModel.Event>(JamStatusChipsUiModel.Event.Select(JamStatus.CREATED)), seen)
    }

    @Test
    fun `amber is only the selected publicada chip`() {
        val colors = BluesJamColors
        val amber = JamStatusChipDefaults.style(selected = true, published = true, enabled = true)
        assertEquals(colors.published, amber.fill)
        assertEquals(colors.onPublished, amber.label)
        assertEquals(colors.onPublished, amber.glyph)
        assertNull(amber.outline)
        for (selected in listOf(true, false)) {
            for (enabled in listOf(true, false)) {
                if (selected && enabled) continue
                val style = JamStatusChipDefaults.style(selected, published = true, enabled = enabled)
                if (!selected) {
                    assertNotEquals(colors.published, style.fill)
                    assertNotEquals(colors.published, style.outline)
                }
            }
        }
        JamStatusChipDefaults.style(selected = true, published = false, enabled = true).let {
            assertNotEquals(colors.published, it.fill)
        }
    }

    @Test
    fun `selected neutral chips are raised with a text outline and a glyph`() {
        val colors = BluesJamColors
        val style = JamStatusChipDefaults.style(selected = true, published = false, enabled = true)
        assertEquals(colors.surfaceRaised, style.fill)
        assertEquals(colors.text, style.outline)
        assertEquals(colors.text, style.label)
        assertTrue(style.showGlyph)
    }

    @Test
    fun `unselected enabled chips have a hairline border and muted label and no glyph`() {
        val colors = BluesJamColors
        val style = JamStatusChipDefaults.style(selected = false, published = false, enabled = true)
        assertEquals(colors.surfaceRaised, style.fill)
        assertEquals(colors.border, style.outline)
        assertEquals(colors.textMuted, style.label)
        assertFalse(style.showGlyph)
    }

    @Test
    fun `a disabled unselected chip has no fill and no outline and a muted label`() {
        val style = JamStatusChipDefaults.style(selected = false, published = true, enabled = false)
        assertEquals(androidx.compose.ui.graphics.Color.Transparent, style.fill)
        assertNull(style.outline)
        assertEquals(BluesJamColors.textMuted, style.label)
        assertFalse(style.showGlyph)
    }

    @Test
    fun `a disabled selected chip keeps its selected look`() {
        assertEquals(
            JamStatusChipDefaults.style(selected = true, published = false, enabled = true),
            JamStatusChipDefaults.style(selected = true, published = false, enabled = false),
        )
        assertEquals(
            JamStatusChipDefaults.style(selected = true, published = true, enabled = true),
            JamStatusChipDefaults.style(selected = true, published = true, enabled = false),
        )
    }
}
