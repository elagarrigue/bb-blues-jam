package com.bbbjam.core.ui.filter

import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Slot
import com.bbbjam.core.ui.presenter.EventHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The filter bar's chips, counts, selection and copy. Every string is written out from the approved
 * copy table in `docs/specs/instrument-filter-chips.md` (C1 and the multi-select approval of F1),
 * never read from [InstrumentFilterCopy], so a copy change fails here.
 */
class InstrumentFilterMapperTest {

    /** A lineup of the default seven slots, open only for [open]; every other slot holds a name. */
    private fun openFor(vararg open: Instrument) =
        Lineup(Lineup.DEFAULT_INSTRUMENTS.map { Slot(it, if (it in open) null else "Nombre") })

    private val allFilled = openFor()

    /**
     * Five songs: 1 guitar and keyboards, 2 bass, 3 bass and vocals, 4 vocals only, 5 every slot
     * filled. Drums and harmonica are open nowhere.
     */
    private val lineups = listOf(
        openFor(Instrument.GUITAR, Instrument.KEYBOARDS),
        openFor(Instrument.BASS),
        openFor(Instrument.BASS, Instrument.VOCALS),
        openFor(Instrument.VOCALS),
        allFilled,
    )

    private fun chip(label: String, count: String, description: String, selected: Boolean = false) =
        FilterChipUiModel(label, count, description, selected, EventHandler {})

    @Test
    fun `nothing selected, Todos is checked and every chip carries its count and description`() {
        val bar = instrumentFilterBar(lineups, emptySet()) {}
        assertEquals(
            InstrumentFilterBarUiModel(
                heading = "Filtrá por cupo libre",
                chips = listOf(
                    chip("Todos", "5", "Todos los temas: 5", selected = true),
                    chip("Guitarra", "1", "Guitarra: 1 tema con cupo libre"),
                    chip("Bajo", "2", "Bajo: 2 temas con cupo libre"),
                    chip("Batería", "0", "Batería: ningún tema con cupo libre"),
                    chip("Voz", "2", "Voz: 2 temas con cupo libre"),
                    chip("Armónica", "0", "Armónica: ningún tema con cupo libre"),
                    chip("Teclados", "1", "Teclados: 1 tema con cupo libre"),
                ),
                summary = null,
                noResults = null,
                clearLabel = "Ver todos los temas",
                events = EventHandler {},
            ),
            bar,
        )
    }

    @Test
    fun `one instrument selected checks only its chip and writes the count line`() {
        val bar = instrumentFilterBar(lineups, setOf(Instrument.BASS)) {}
        assertEquals(listOf(false, false, true, false, false, false, false), bar.chips.map { it.isSelected })
        assertEquals("2 de 5 temas con cupo libre para bajo", bar.summary)
        assertNull(bar.noResults)
    }

    @Test
    fun `each instrument alone checks its own chip and names it lowercase`() {
        val expected = mapOf(
            Instrument.GUITAR to "1 de 5 temas con cupo libre para guitarra",
            Instrument.BASS to "2 de 5 temas con cupo libre para bajo",
            Instrument.VOCALS to "2 de 5 temas con cupo libre para voz",
            Instrument.KEYBOARDS to "1 de 5 temas con cupo libre para teclados",
        )
        expected.forEach { (instrument, summary) ->
            val bar = instrumentFilterBar(lineups, setOf(instrument)) {}
            assertEquals(summary, bar.summary)
            assertEquals(listOf(instrument), Instrument.entries.filterIndexed { i, _ -> bar.chips[i + 1].isSelected })
            assertFalse(bar.chips[0].isSelected)
        }
        assertEquals(
            "Ningún tema tiene cupo libre para batería.",
            instrumentFilterBar(lineups, setOf(Instrument.DRUMS)) {}.noResults,
        )
        assertEquals(
            "Ningún tema tiene cupo libre para armónica.",
            instrumentFilterBar(lineups, setOf(Instrument.HARMONICA)) {}.noResults,
        )
    }

    @Test
    fun `two and three instruments count songs open for any of them, names joined with o`() {
        // Bajo or voz: songs 2, 3 and 4 (song 3 counted once).
        val two = instrumentFilterBar(lineups, setOf(Instrument.VOCALS, Instrument.BASS)) {}
        assertEquals("3 de 5 temas con cupo libre para bajo o voz", two.summary)
        assertEquals(listOf(false, false, true, false, true, false, false), two.chips.map { it.isSelected })

        val three = instrumentFilterBar(lineups, setOf(Instrument.VOCALS, Instrument.GUITAR, Instrument.BASS)) {}
        assertEquals("4 de 5 temas con cupo libre para guitarra, bajo o voz", three.summary)

        val none = instrumentFilterBar(lineups, setOf(Instrument.HARMONICA, Instrument.DRUMS)) {}
        assertEquals("Ningún tema tiene cupo libre para batería o armónica.", none.noResults)
        assertNull(none.summary)
    }

    @Test
    fun `summary and no results are exclusive and absent while nothing is selected`() {
        Instrument.entries.forEach { instrument ->
            val bar = instrumentFilterBar(lineups, setOf(instrument)) {}
            assertTrue((bar.summary == null) != (bar.noResults == null))
        }
        val bar = instrumentFilterBar(lineups, emptySet()) {}
        assertNull(bar.summary)
        assertNull(bar.noResults)
    }

    @Test
    fun `a one-song setlist is singular`() {
        val bar = instrumentFilterBar(listOf(openFor(Instrument.BASS)), setOf(Instrument.BASS)) {}
        assertEquals("1 de 1 tema con cupo libre para bajo", bar.summary)
        assertEquals("Todos los temas: 1", bar.chips[0].contentDescription)
    }

    @Test
    fun `an extra participant never counts and an all-filled song matches no instrument`() {
        // Bass filled; an extra playing bass is in Otros, which is never a slot (D-18).
        val bassFilled = Lineup(listOf(Slot(Instrument.BASS, "Nico"), Slot(Instrument.VOCALS)))
        val bar = instrumentFilterBar(listOf(bassFilled, allFilled), setOf(Instrument.BASS)) {}
        assertEquals("0", bar.chips[2].count)
        assertEquals("Ningún tema tiene cupo libre para bajo.", bar.noResults)
        Instrument.entries.forEach { assertFalse(allFilled.matchesInstrumentFilter(setOf(it))) }
        assertTrue(allFilled.matchesInstrumentFilter(emptySet()))
    }

    @Test
    fun `handlers send their change, and the change applies to the current selection`() {
        val changes = mutableListOf<InstrumentFilterChange>()
        val bar = instrumentFilterBar(lineups, setOf(Instrument.BASS)) { changes += it }
        bar.chips.forEach { it.events(FilterChipUiModel.Event.Toggle) }
        bar.events(InstrumentFilterBarUiModel.Event.Clear)
        assertEquals(
            listOf(InstrumentFilterChange.Clear) +
                Instrument.entries.map { InstrumentFilterChange.Toggle(it) } +
                InstrumentFilterChange.Clear,
            changes,
        )

        val bass = setOf(Instrument.BASS)
        assertEquals(
            setOf(Instrument.BASS, Instrument.VOCALS),
            bass.updatedBy(InstrumentFilterChange.Toggle(Instrument.VOCALS)),
        )
        assertEquals(emptySet<Instrument>(), bass.updatedBy(InstrumentFilterChange.Toggle(Instrument.BASS)))
        assertEquals(
            emptySet<Instrument>(),
            setOf(Instrument.BASS, Instrument.VOCALS).updatedBy(InstrumentFilterChange.Clear),
        )
    }
}
