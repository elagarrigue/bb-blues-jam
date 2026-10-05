package com.bbbjam.core.ui.lineup

import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Slot
import com.bbbjam.core.ui.strip.InstrumentChipKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The song detail's lineup grouping (`song-detail-screen`, G1). Every string is written out from the
 * approved copy (instrument strip C1, row expansion C1 and H1), never read from the copy objects.
 */
class InstrumentGroupsMapperTest {
    private val hint = "Para tocar, anotate en la jam: la organización te suma a un tema."

    private fun open(instrument: String) =
        LineupLineUiModel(instrument, "LIBRE", "$instrument: libre", InstrumentChipKind.OPEN_SLOT)

    private fun filled(instrument: String, name: String) =
        LineupLineUiModel(instrument, name, "$instrument: $name", InstrumentChipKind.FILLED_SLOT)

    private fun extra(instrument: String, name: String) =
        LineupLineUiModel("+ $instrument", name, "Otros: $instrument, $name", InstrumentChipKind.EXTRA)

    @Test
    fun `the default lineup is six groups in Sheet order, two open guitars first`() {
        val expected = InstrumentGroupsUiModel(
            groups = listOf(
                InstrumentGroupUiModel("Guitarra", listOf(open("Guitarra"), open("Guitarra"))),
                InstrumentGroupUiModel("Bajo", listOf(open("Bajo"))),
                InstrumentGroupUiModel("Batería", listOf(open("Batería"))),
                InstrumentGroupUiModel("Voz", listOf(open("Voz"))),
                InstrumentGroupUiModel("Armónica", listOf(open("Armónica"))),
                InstrumentGroupUiModel("Teclados", listOf(open("Teclados"))),
            ),
            extras = emptyList(),
            noOpenSlotsNote = null,
            hint = hint,
        )
        assertEquals(expected, Lineup.default().toInstrumentGroups(emptyList()))
    }

    @Test
    fun `inside a group the open slot comes before the filled one, whatever the column order`() {
        val lineup = Lineup(
            listOf(
                Slot(Instrument.GUITAR, "Tincho"),
                Slot(Instrument.GUITAR),
                Slot(Instrument.BASS, "Nico"),
                Slot(Instrument.DRUMS),
            ),
        )
        val groups = lineup.toInstrumentGroups(emptyList()).groups
        assertEquals(
            listOf(
                InstrumentGroupUiModel("Guitarra", listOf(open("Guitarra"), filled("Guitarra", "Tincho"))),
                InstrumentGroupUiModel("Bajo", listOf(filled("Bajo", "Nico"))),
                InstrumentGroupUiModel("Batería", listOf(open("Batería"))),
            ),
            groups,
        )
    }

    @Test
    fun `groups follow first appearance, and an instrument with no slot has no group`() {
        // No keyboards and no harmonica: the admin removed them (D-18). Voice before bass in the list.
        val lineup = Lineup(listOf(Slot(Instrument.VOCALS, "Laura"), Slot(Instrument.BASS), Slot(Instrument.GUITAR)))
        assertEquals(
            listOf("Voz", "Bajo", "Guitarra"),
            lineup.toInstrumentGroups(emptyList()).groups.map { it.heading },
        )
    }

    @Test
    fun `an extra typed as guitarra stays under Otros, never in the Guitarra group`() {
        val lineup = Lineup(listOf(Slot(Instrument.GUITAR, "Tincho")))
        val model = lineup.toInstrumentGroups(
            listOf(ExtraParticipant("Juan", "guitarra"), ExtraParticipant("Ana", "saxo")),
        )
        assertEquals(listOf(InstrumentGroupUiModel("Guitarra", listOf(filled("Guitarra", "Tincho")))), model.groups)
        assertEquals(listOf(extra("guitarra", "Juan"), extra("saxo", "Ana")), model.extras)
    }

    @Test
    fun `the note when no slot is open, the hint when one is`() {
        val full = Lineup(listOf(Slot(Instrument.GUITAR, "Tincho"))).toInstrumentGroups(emptyList())
        assertEquals("No quedan cupos libres.", full.noOpenSlotsNote)
        assertNull(full.hint)
        val withOpen = Lineup(listOf(Slot(Instrument.GUITAR))).toInstrumentGroups(emptyList())
        assertNull(withOpen.noOpenSlotsNote)
        assertEquals(hint, withOpen.hint)
    }

    @Test
    fun `an empty lineup has no group and the no-open-slots note`() {
        assertEquals(
            InstrumentGroupsUiModel(emptyList(), emptyList(), "No quedan cupos libres.", null),
            Lineup(emptyList()).toInstrumentGroups(emptyList()),
        )
    }
}
