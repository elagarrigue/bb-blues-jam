package com.bbbjam.core.ui.status

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.core.ui.text.text
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
        assertEquals(listOf("Borrador", "Creada", "Publicada"), model.chips.map { it.label.text() })
        assertEquals(
            listOf("Estado: Borrador", "Estado: Creada", "Estado: Publicada"),
            model.chips.map { it.description.text() },
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

    @Test
    fun `a pending chip carries the announcement, is disabled and is the only pending one`() {
        val model = jamStatusChips(
            JamStatus.CREATED,
            enabled = JamStatus.entries.toSet(),
            pending = PendingChip(JamStatus.DRAFT, UiText.raw("Cambiando el estado a Borrador…")),
            events = none,
        )
        val pending = model.chips.filter { it.pending }
        assertEquals(listOf(JamStatus.DRAFT), pending.map { it.status })
        assertEquals("Cambiando el estado a Borrador…", pending.single().description.text())
        assertFalse(pending.single().enabled)
        assertFalse(pending.single().selected)
        assertEquals(
            listOf("Estado: Creada", "Estado: Publicada"),
            model.chips.filter {
                !it.pending
            }.map { it.description.text() },
        )
        assertTrue(model.chips.none { it.pending && it.selected })
    }

    @Test
    fun `without a pending chip none is pending`() {
        assertTrue(jamStatusChips(JamStatus.DRAFT, setOf(JamStatus.CREATED), none).chips.none { it.pending })
    }

    @Test
    fun `the pending style is a muted two dp outline on a raised fill, never amber`() {
        val colors = BluesJamColors
        for (published in listOf(true, false)) {
            val style = JamStatusChipDefaults.style(
                selected = false,
                published = published,
                enabled = false,
                pending = true,
            )
            assertEquals(colors.surfaceRaised, style.fill)
            assertEquals(colors.textMuted, style.outline)
            assertEquals(colors.textMuted, style.label)
            assertNotEquals(colors.published, style.fill)
            assertNotEquals(colors.published, style.outline)
            assertEquals(2.dp, style.outlineWidth)
            assertFalse(style.showGlyph)
        }
        assertEquals(Dp.Hairline, JamStatusChipDefaults.style(false, false, true).outlineWidth)
    }

    @Test
    fun `the pending outline alpha stays between the dots bounds and repeats each period`() {
        for (t in 0L..2400L step 50L) {
            val alpha = pendingOutlineAlpha(t, reduced = false)
            assertTrue("t=$t alpha=$alpha", alpha in 0.3f..1f)
            assertEquals(alpha, pendingOutlineAlpha(t + 1200L, reduced = false), 0.0001f)
        }
        assertEquals(1f, pendingOutlineAlpha(0L, reduced = false), 0.0001f)
        assertEquals(0.3f, pendingOutlineAlpha(600L, reduced = false), 0.0001f)
    }

    @Test
    fun `reduced motion draws a constant outline`() {
        for (t in listOf(0L, 300L, 600L, 1199L)) {
            assertEquals(0.6f, pendingOutlineAlpha(t, reduced = true), 0f)
        }
    }
}
