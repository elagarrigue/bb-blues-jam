package com.bbbjam.core.ui.strip

import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Slot
import com.bbbjam.core.ui.text.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every string expected here is written out from the approved copy (`docs/specs/instrument-strip-component.md`,
 * C1 and E1), compared with the text the chips resolve to in `strings.xml`, so a change to the shipped
 * copy fails this class.
 */
class LineupChipsTest {

    /** A chip as the user reads it. */
    private data class Chip(val label: String, val description: String, val kind: InstrumentChipKind) {
        val isOpen: Boolean get() = kind == InstrumentChipKind.OPEN_SLOT
    }

    private fun List<InstrumentChipUiModel>.resolved() =
        map { Chip(it.label.text(), it.contentDescription.text(), it.kind) }

    private fun open(label: String, description: String) = Chip(label, description, InstrumentChipKind.OPEN_SLOT)

    private fun filled(label: String, description: String) = Chip(label, description, InstrumentChipKind.FILLED_SLOT)

    private fun extra(label: String, description: String) = Chip(label, description, InstrumentChipKind.EXTRA)

    /** Scenario 2: [GTR open, GTR Tincho, BAJO Nico, BAT open, VOZ open, ARM Mono], TEC removed. */
    private val mixed = Lineup(
        listOf(
            Slot(Instrument.GUITAR),
            Slot(Instrument.GUITAR, "Tincho"),
            Slot(Instrument.BASS, "Nico"),
            Slot(Instrument.DRUMS),
            Slot(Instrument.VOCALS),
            Slot(Instrument.HARMONICA, "Mono"),
        ),
    )

    private val mixedChips = listOf(
        open("GTR: LIBRE", "Guitarra: libre"),
        filled("Gtr: Tincho", "Guitarra: Tincho"),
        filled("Bajo: Nico", "Bajo: Nico"),
        open("BAT: LIBRE", "Batería: libre"),
        open("VOZ: LIBRE", "Voz: libre"),
        filled("Arm: Mono", "Armónica: Mono"),
    )

    @Test
    fun `the default lineup gives seven open chips in Sheet column order`() {
        assertEquals(
            listOf(
                open("GTR: LIBRE", "Guitarra: libre"),
                open("GTR: LIBRE", "Guitarra: libre"),
                open("BAJO: LIBRE", "Bajo: libre"),
                open("BAT: LIBRE", "Batería: libre"),
                open("VOZ: LIBRE", "Voz: libre"),
                open("ARM: LIBRE", "Armónica: libre"),
                open("TEC: LIBRE", "Teclados: libre"),
            ),
            Lineup.default().toInstrumentChips(emptyList()).resolved(),
        )
    }

    @Test
    fun `every instrument has its open and filled copy`() {
        val expected = mapOf(
            Instrument.GUITAR to listOf("GTR: LIBRE", "Guitarra: libre", "Gtr: Ana", "Guitarra: Ana"),
            Instrument.BASS to listOf("BAJO: LIBRE", "Bajo: libre", "Bajo: Ana", "Bajo: Ana"),
            Instrument.DRUMS to listOf("BAT: LIBRE", "Batería: libre", "Bat: Ana", "Batería: Ana"),
            Instrument.VOCALS to listOf("VOZ: LIBRE", "Voz: libre", "Voz: Ana", "Voz: Ana"),
            Instrument.HARMONICA to listOf("ARM: LIBRE", "Armónica: libre", "Arm: Ana", "Armónica: Ana"),
            Instrument.KEYBOARDS to listOf("TEC: LIBRE", "Teclados: libre", "Tec: Ana", "Teclados: Ana"),
        )
        assertEquals(Instrument.entries.toSet(), expected.keys)
        expected.forEach { (instrument, copy) ->
            assertEquals(
                listOf(open(copy[0], copy[1])),
                Lineup(listOf(Slot(instrument))).toInstrumentChips(emptyList()).resolved(),
            )
            assertEquals(
                listOf(filled(copy[2], copy[3])),
                Lineup(listOf(Slot(instrument, "Ana"))).toInstrumentChips(emptyList()).resolved(),
            )
        }
    }

    @Test
    fun `a mixed lineup keeps its order and a removed slot gets no chip`() {
        val chips = mixed.toInstrumentChips(emptyList()).resolved()
        assertEquals(mixedChips, chips)
        assertTrue(chips.none { it.label.startsWith("TEC") || it.label.startsWith("Tec") })
    }

    @Test
    fun `only the second guitar filled keeps column order`() {
        val lineup = Lineup(listOf(Slot(Instrument.GUITAR), Slot(Instrument.GUITAR, "Seba")))
        assertEquals(
            listOf(open("GTR: LIBRE", "Guitarra: libre"), filled("Gtr: Seba", "Guitarra: Seba")),
            lineup.toInstrumentChips(emptyList()).resolved(),
        )
    }

    @Test
    fun `an empty lineup gives no chips`() {
        assertEquals(emptyList<InstrumentChipUiModel>(), Lineup(emptyList()).toInstrumentChips(emptyList()).resolved())
    }

    @Test
    fun `a long name is kept verbatim in label and description`() {
        val name = "Maximiliano Fernández de la Torre y Sotomayor"
        assertEquals(
            listOf(filled("Gtr: $name", "Guitarra: $name")),
            Lineup(listOf(Slot(Instrument.GUITAR, name))).toInstrumentChips(emptyList()).resolved(),
        )
    }

    @Test
    fun `extras come after every slot chip, in Otros order, with the instrument as typed`() {
        val extras = listOf(ExtraParticipant("Juan", "saxo"), ExtraParticipant("Ana", "Trompeta"))
        assertEquals(
            mixedChips + listOf(
                extra("+ saxo: Juan", "Otros: saxo, Juan"),
                extra("+ Trompeta: Ana", "Otros: Trompeta, Ana"),
            ),
            mixed.toInstrumentChips(extras).resolved(),
        )
    }

    @Test
    fun `without extras the chips are only the slots`() {
        assertEquals(mixedChips, mixed.toInstrumentChips(emptyList()).resolved())
    }

    @Test
    fun `an extra never looks like an open slot`() {
        val chips = Lineup(emptyList()).toInstrumentChips(listOf(ExtraParticipant("Juan", "guitarra"))).resolved()
        assertEquals(listOf(extra("+ guitarra: Juan", "Otros: guitarra, Juan")), chips)
        chips.forEach { chip ->
            assertFalse(chip.isOpen)
            assertFalse(chip.label.contains("LIBRE"))
        }
    }
}
