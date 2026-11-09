package com.bbbjam.core.ui.filter

import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.RosterEntry
import com.bbbjam.core.ui.text.text
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The musician block's names, lines and handlers; wording written out from the spec's copy. */
class MusicianFilterMapperTest {
    private val roster = listOf(RosterEntry("Ana", "ana", 1), RosterEntry("Juan", "juan", 3))
    private val changes = mutableListOf<MusicianFilterChange>()

    private fun model(
        selected: String?,
        counts: MusicianFilterCounts = MusicianFilterCounts(total = 13, withMusician = 3, shown = 3),
        instruments: Set<Instrument> = emptySet(),
    ) = musicianFilter(roster, selected, counts, instruments) { changes += it }

    @Test
    fun `nothing chosen shows the pick button and no line`() {
        val model = model(selected = null, counts = MusicianFilterCounts(13, 0, 13))
        assertEquals("Filtrá por músico", model.heading.text())
        assertEquals("Elegir músico", model.pickLabel.text())
        assertNull(model.selectedName)
        assertNull(model.summary)
        assertNull(model.noResults)
    }

    @Test
    fun `options list each name with its song count and mark the chosen one`() {
        val model = model(selected = "juan  ")
        assertEquals(listOf("Ana, 1 tema", "Juan, 3 temas"), model.options.map { it.label.text() })
        assertEquals(listOf(false, true), model.options.map { it.isSelected })
        assertEquals("Todos los músicos", model.allLabel.text())
        assertEquals("Juan", model.selectedName)
    }

    @Test
    fun `a chosen name says how many songs remain`() {
        assertEquals("Quedan 3 de 13 temas con Juan", model("Juan").summary?.text())
        val one = MusicianFilterCounts(13, 1, 1)
        assertEquals("Queda 1 de 13 temas con Juan", model("Juan", one).summary?.text())
    }

    @Test
    fun `with an instrument the line names it`() {
        val model = model("Juan", MusicianFilterCounts(13, 3, 1), setOf(Instrument.BASS))
        assertEquals("Queda 1 de 13 temas con Juan y cupo libre para bajo", model.summary?.text())
    }

    @Test
    fun `a name that is in no song has its own empty line`() {
        val model = model("Pedro", MusicianFilterCounts(13, 0, 0))
        assertNull(model.summary)
        assertEquals("Pedro no está en ningún tema de esta jam.", model.noResults?.text())
        assertEquals("Ver todos los temas", model.noResultsClearLabel.text())
        assertNull(model.options.firstOrNull { it.isSelected })
    }

    @Test
    fun `a name and an instrument that match nothing together say so`() {
        val model = model("Juan", MusicianFilterCounts(13, 3, 0), setOf(Instrument.BASS, Instrument.VOCALS))
        assertEquals("Ningún tema con Juan tiene cupo libre para bajo o voz.", model.noResults?.text())
    }

    @Test
    fun `an absent name with an instrument still reads as absent`() {
        val model = model("Pedro", MusicianFilterCounts(13, 0, 0), setOf(Instrument.BASS))
        assertEquals("Pedro no está en ningún tema de esta jam.", model.noResults?.text())
    }

    @Test
    fun `handlers carry the change, and equal models compare equal`() {
        val model = model("Juan", MusicianFilterCounts(13, 3, 0))
        model.options.first().events(MusicianOptionUiModel.Event.Select)
        model.events(MusicianFilterUiModel.Event.Clear)
        model.events(MusicianFilterUiModel.Event.ClearAll)
        assertEquals(
            listOf(MusicianFilterChange.Select("Ana"), MusicianFilterChange.Clear, MusicianFilterChange.ClearAll),
            changes,
        )
        assertEquals(model("Juan"), model("Juan"))
        assertNotEquals(model("Juan"), model("Ana"))
    }

    @Test
    fun `the new strings use vos and never tu forms`() {
        val xml = File("src/main/res/values/strings.xml").readText()
        val block = xml.substringAfter("<!-- Musician filter -->").substringBefore("<!-- Song rows -->")
        assertTrue(block.contains("Filtrá"))
        assertFalse(Regex("\\b(Filtra|Elige|Quita|Tienes|Puedes)\\b").containsMatchIn(block))
    }
}
