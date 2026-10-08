package com.bbbjam.core.ui.lineup

import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Slot
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.ui.strip.InstrumentChipKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The expanded panel's order and copy. Every string is written out from the approved copy table in
 * `docs/specs/song-row-expansion.md` (and the strip's approved descriptions), never read from
 * [LineupPanelCopy], so a copy change fails here.
 */
class LineupPanelMapperTest {

    private val hint = "Para tocar, anotate en la jam: la organización te suma a un tema."
    private val noOpen = "No quedan cupos libres."

    private fun open(instrument: String, description: String) =
        LineupLineUiModel(instrument, "LIBRE", description, InstrumentChipKind.OPEN_SLOT)

    private fun filled(instrument: String, name: String, description: String) =
        LineupLineUiModel(instrument, name, description, InstrumentChipKind.FILLED_SLOT)

    private fun extra(instrument: String, name: String, description: String) =
        LineupLineUiModel(instrument, name, description, InstrumentChipKind.EXTRA)

    @Test
    fun `open slots first, then filled, then extras, each in lineup order`() {
        val lineup = Lineup(
            listOf(
                Slot(Instrument.GUITAR),
                Slot(Instrument.GUITAR, "Tincho"),
                Slot(Instrument.BASS, "Nico"),
                Slot(Instrument.DRUMS),
                Slot(Instrument.VOCALS),
                Slot(Instrument.HARMONICA, "Mono"),
            ),
        )
        assertEquals(
            LineupPanelUiModel(
                openSlots = listOf(
                    open("Guitarra", "Guitarra: libre"),
                    open("Batería", "Batería: libre"),
                    open("Voz", "Voz: libre"),
                ),
                filledSlots = listOf(
                    filled("Guitarra", "Tincho", "Guitarra: Tincho"),
                    filled("Bajo", "Nico", "Bajo: Nico"),
                    filled("Armónica", "Mono", "Armónica: Mono"),
                ),
                extras = listOf(extra("+ saxo", "Juan", "Otros: saxo, Juan")),
                noOpenSlotsNote = null,
                hint = hint,
            ),
            lineup.toLineupPanel(listOf(ExtraParticipant("Juan", "saxo"))),
        )
    }

    @Test
    fun `two guitars with only the second filled keep their column order in each section`() {
        val lineup = Lineup(
            listOf(
                Slot(Instrument.GUITAR),
                Slot(Instrument.GUITAR, "Tincho"),
                Slot(Instrument.BASS),
                Slot(Instrument.DRUMS, "Pato"),
                Slot(Instrument.VOCALS),
            ),
        )
        val panel = lineup.toLineupPanel(emptyList())
        assertEquals(
            listOf(open("Guitarra", "Guitarra: libre"), open("Bajo", "Bajo: libre"), open("Voz", "Voz: libre")),
            panel.openSlots,
        )
        assertEquals(
            listOf(filled("Guitarra", "Tincho", "Guitarra: Tincho"), filled("Batería", "Pato", "Batería: Pato")),
            panel.filledSlots,
        )
    }

    @Test
    fun `admin open action carries lineup ordinal before open-first presentation`() {
        val calls = mutableListOf<Pair<Instrument, SlotPosition>>()
        val panel = Lineup(
            listOf(Slot(Instrument.GUITAR), Slot(Instrument.GUITAR), Slot(Instrument.BASS, "Tincho")),
        ).toLineupPanel(emptyList()) { instrument, position -> calls += instrument to position }

        assertEquals("Anotar", panel.openSlots.first().actionLabel)
        panel.openSlots.first().action?.invoke(LineupLineEvent.Activate)
        panel.openSlots.last().action?.invoke(LineupLineEvent.Activate)
        assertEquals(
            listOf(Instrument.GUITAR to SlotPosition(1), Instrument.GUITAR to SlotPosition(2)),
            calls,
        )
        assertEquals(null, panel.filledSlots.single().action)
    }

    @Test
    fun `admin clear action carries filled slot original ordinal and expected name`() {
        val calls = mutableListOf<Triple<Instrument, SlotPosition, String>>()
        val panel = Lineup(
            listOf(Slot(Instrument.GUITAR), Slot(Instrument.GUITAR, "Ana")),
        ).toLineupPanel(emptyList(), onClear = { instrument, position, name ->
            calls +=
                Triple(instrument, position, name)
        })

        assertEquals("Liberar", panel.filledSlots.single().actionLabel)
        assertEquals("Guitarra: Ana. Liberar", panel.filledSlots.single().contentDescription)
        panel.filledSlots.single().action?.invoke(LineupLineEvent.Activate)
        assertEquals(listOf(Triple(Instrument.GUITAR, SlotPosition(2), "Ana")), calls)
        assertEquals(
            null,
            Lineup(listOf(Slot(Instrument.GUITAR, "Ana"))).toLineupPanel(emptyList()).filledSlots.single().action,
        )
    }

    @Test
    fun `an all-open lineup lists every slot as open with the hint and no note`() {
        assertEquals(
            LineupPanelUiModel(
                openSlots = listOf(
                    open("Guitarra", "Guitarra: libre"),
                    open("Guitarra", "Guitarra: libre"),
                    open("Bajo", "Bajo: libre"),
                    open("Batería", "Batería: libre"),
                    open("Voz", "Voz: libre"),
                    open("Armónica", "Armónica: libre"),
                    open("Teclados", "Teclados: libre"),
                ),
                filledSlots = emptyList(),
                extras = emptyList(),
                noOpenSlotsNote = null,
                hint = hint,
            ),
            Lineup.default().toLineupPanel(emptyList()),
        )
    }

    @Test
    fun `an all-filled lineup shows the note instead of open slots, and no hint`() {
        val lineup = Lineup(listOf(Slot(Instrument.BASS, "Nico"), Slot(Instrument.KEYBOARDS, "Caro")))
        assertEquals(
            LineupPanelUiModel(
                openSlots = emptyList(),
                filledSlots = listOf(
                    filled("Bajo", "Nico", "Bajo: Nico"),
                    filled("Teclados", "Caro", "Teclados: Caro"),
                ),
                extras = emptyList(),
                noOpenSlotsNote = noOpen,
                hint = null,
            ),
            lineup.toLineupPanel(emptyList()),
        )
    }

    @Test
    fun `an empty lineup with no extras is the note only`() {
        assertEquals(
            LineupPanelUiModel(emptyList(), emptyList(), emptyList(), noOpenSlotsNote = noOpen, hint = null),
            Lineup(emptyList()).toLineupPanel(emptyList()),
        )
    }

    @Test
    fun `extras are never open nor filled slots, and never make room`() {
        val extras = listOf(ExtraParticipant("Juan", "saxo"), ExtraParticipant("Ana", "Trompeta"))
        val panel = Lineup(listOf(Slot(Instrument.DRUMS, "Pato"))).toLineupPanel(extras)
        assertTrue(panel.openSlots.isEmpty())
        assertEquals(listOf(filled("Batería", "Pato", "Batería: Pato")), panel.filledSlots)
        assertEquals(
            listOf(extra("+ saxo", "Juan", "Otros: saxo, Juan"), extra("+ Trompeta", "Ana", "Otros: Trompeta, Ana")),
            panel.extras,
        )
        // An extra never turns a full lineup into one with room (D-18).
        assertEquals(noOpen, panel.noOpenSlotsNote)
    }

    @Test
    fun `a long name is kept verbatim`() {
        val name = "Maximiliano Fernández de la Torre y Sotomayor"
        val panel = Lineup(listOf(Slot(Instrument.KEYBOARDS, name))).toLineupPanel(emptyList())
        assertEquals(listOf(filled("Teclados", name, "Teclados: $name")), panel.filledSlots)
    }
}
