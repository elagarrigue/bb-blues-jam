package com.bbbjam.core.model

import com.bbbjam.core.model.Instrument.BASS
import com.bbbjam.core.model.Instrument.DRUMS
import com.bbbjam.core.model.Instrument.GUITAR
import com.bbbjam.core.model.Instrument.HARMONICA
import com.bbbjam.core.model.Instrument.KEYBOARDS
import com.bbbjam.core.model.Instrument.VOCALS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LineupTest {

    @Test
    fun `the default lineup is seven open slots in Sheet column order`() {
        val lineup = Lineup.default()

        assertEquals(
            listOf(GUITAR, GUITAR, BASS, DRUMS, VOCALS, HARMONICA, KEYBOARDS),
            lineup.slots.map { it.instrument },
        )
        assertTrue(lineup.slots.all { it.isOpen })
        assertEquals(lineup.slots, lineup.openSlots)
    }

    @Test
    fun `a lineup without harmonica has no open slot for it`() {
        val lineup = Lineup(Lineup.default().slots.filterNot { it.instrument == HARMONICA })

        assertFalse(lineup.hasOpenSlotFor(HARMONICA))
        assertTrue(lineup.hasOpenSlotFor(GUITAR))
    }

    @Test
    fun `two filled guitars leave no open guitar slot`() {
        val lineup = Lineup(
            listOf(Slot(GUITAR, "Pato"), Slot(GUITAR, "Lucho"), Slot(BASS), Slot(DRUMS, "Negro")),
        )

        assertFalse(lineup.hasOpenSlotFor(GUITAR))
        assertFalse(lineup.hasOpenSlotFor(DRUMS))
        assertTrue(lineup.hasOpenSlotFor(BASS))
        assertEquals(listOf(Slot(BASS)), lineup.openSlots)
    }

    @Test
    fun `one open guitar is enough for the guitar filter`() {
        val lineup = Lineup(listOf(Slot(GUITAR, "Pato"), Slot(GUITAR)))

        assertTrue(lineup.hasOpenSlotFor(GUITAR))
        assertEquals(listOf(Slot(GUITAR)), lineup.openSlots)
    }

    @Test
    fun `zero slots of an instrument and an empty lineup are valid`() {
        val noDrums = Lineup(listOf(Slot(GUITAR), Slot(VOCALS)))
        val empty = Lineup(emptyList())

        assertFalse(noDrums.hasOpenSlotFor(DRUMS))
        assertTrue(empty.openSlots.isEmpty())
        Instrument.entries.forEach { assertFalse(empty.hasOpenSlotFor(it)) }
    }

    @Test
    fun `default and current counts match the lineup`() {
        val lineup = Lineup.default()

        assertEquals(2, Lineup.defaultCount(GUITAR))
        Instrument.entries.filterNot { it == GUITAR }.forEach { assertEquals(1, Lineup.defaultCount(it)) }
        Instrument.entries.forEach { assertEquals(Lineup.defaultCount(it), lineup.count(it)) }
        assertEquals(0, Lineup(emptyList()).count(HARMONICA))
    }

    @Test
    fun `reducing a lineup removes the last open matching slots`() {
        val cases = listOf(
            Triple(listOf(Slot(GUITAR), Slot(GUITAR), Slot(BASS)), 1, listOf(Slot(GUITAR), Slot(BASS))),
            Triple(
                listOf(Slot(GUITAR, "Martín"), Slot(GUITAR), Slot(BASS)),
                1,
                listOf(Slot(GUITAR, "Martín"), Slot(BASS)),
            ),
            Triple(
                listOf(Slot(GUITAR), Slot(GUITAR, "Pedro"), Slot(BASS)),
                1,
                listOf(Slot(GUITAR, "Pedro"), Slot(BASS)),
            ),
            Triple(listOf(Slot(HARMONICA)), 0, emptyList()),
        )

        cases.forEach { (slots, target, expected) ->
            val lineup = Lineup(slots).withSlotCount(slots.first().instrument, target)
            assertEquals(expected, lineup?.slots)
        }
    }

    @Test
    fun `reducing returns null when the removed slots would include a musician`() {
        val lineup = Lineup(listOf(Slot(GUITAR, "Pato"), Slot(GUITAR, "Lucho")))

        assertEquals(null, lineup.withSlotCount(GUITAR, 1))
    }

    @Test
    fun `increasing restores after the last matching slot or canonical position`() {
        val afterLast = Lineup(listOf(Slot(BASS), Slot(GUITAR, "Pedro")))
            .withSlotCount(GUITAR, 2)
        val canonical = Lineup(listOf(Slot(BASS), Slot(VOCALS)))
            .withSlotCount(GUITAR, 1)
        val empty = Lineup(emptyList()).withSlotCount(KEYBOARDS, 1)

        assertEquals(listOf(Slot(BASS), Slot(GUITAR, "Pedro"), Slot(GUITAR)), afterLast?.slots)
        assertEquals(listOf(Slot(GUITAR), Slot(BASS), Slot(VOCALS)), canonical?.slots)
        assertEquals(listOf(Slot(KEYBOARDS)), empty?.slots)
        val unchanged = Lineup.default()
        assertEquals(unchanged, unchanged.withSlotCount(GUITAR, 2))
    }

    @Test
    fun `slot counts outside the maximum range are rejected`() {
        val lineup = Lineup.default()

        assertThrows(IllegalArgumentException::class.java) { lineup.withSlotCount(GUITAR, -1) }
        assertThrows(IllegalArgumentException::class.java) { lineup.withSlotCount(GUITAR, 4) }
        Instrument.entries.forEach { instrument ->
            assertThrows(IllegalArgumentException::class.java) { lineup.withSlotCount(instrument, 4) }
        }
    }

    @Test
    fun `more slots of an instrument than the maximum are rejected`() {
        val overMax = listOf(
            List(4) { Slot(GUITAR) },
            List(4) { Slot(BASS, "Tincho") },
            Lineup.default().slots + List(3) { Slot(KEYBOARDS) },
            List(4) { Slot(HARMONICA) },
        )
        overMax.forEach { slots ->
            assertThrows("${slots.map { it.instrument }} accepted", IllegalArgumentException::class.java) {
                Lineup(slots)
            }
        }
    }

    @Test
    fun `the maximum is three for every instrument and differs from the default`() {
        Instrument.entries.forEach { assertEquals(3, Lineup.maxCount(it)) }
        assertEquals(18, Lineup.MAX_INSTRUMENTS.size)
        assertEquals(Instrument.entries.flatMap { instrument -> List(3) { instrument } }, Lineup.MAX_INSTRUMENTS)
        assertEquals(7, Lineup.DEFAULT_INSTRUMENTS.size)
        assertEquals(2, Lineup.defaultCount(GUITAR))
        assertEquals(1, Lineup.defaultCount(VOCALS))
    }

    @Test
    fun `each default slot sits in its column of the eighteen`() {
        assertEquals(listOf(0, 1, 3, 6, 9, 12, 15), Lineup.DEFAULT_COLUMNS)
        Lineup.DEFAULT_COLUMNS.zip(Lineup.DEFAULT_INSTRUMENTS).forEach { (column, instrument) ->
            assertEquals(instrument, Lineup.MAX_INSTRUMENTS[column])
        }
    }

    @Test
    fun `growing any instrument to its maximum is valid and keeps the order`() {
        Instrument.entries.forEach { instrument ->
            val grown = Lineup.default().withSlotCount(instrument, 3)

            assertEquals(3, grown?.count(instrument))
            assertEquals(Lineup.default().slots.size + 3 - Lineup.defaultCount(instrument), grown?.slots?.size)
        }
        val vocals = Lineup(listOf(Slot(VOCALS, "Lu"), Slot(KEYBOARDS))).withSlotCount(VOCALS, 3)
        assertEquals(listOf(Slot(VOCALS, "Lu"), Slot(VOCALS), Slot(VOCALS), Slot(KEYBOARDS)), vocals?.slots)
        assertEquals(null, vocals?.withSlotCount(VOCALS, 1)?.withSlotCount(VOCALS, 0))
    }

    @Test
    fun `the over-maximum message names the instrument and the counts`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            Lineup(List(4) { Slot(GUITAR) })
        }

        assertEquals("Lineup has 4 GUITAR slots; at most 3 are allowed (D-18)", error.message)
    }
}
