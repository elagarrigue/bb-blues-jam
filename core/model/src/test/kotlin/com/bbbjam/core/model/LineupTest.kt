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
    fun `more slots of an instrument than the default are rejected`() {
        val overDefault = listOf(
            listOf(Slot(GUITAR), Slot(GUITAR), Slot(GUITAR)),
            listOf(Slot(BASS), Slot(BASS, "Tincho")),
            Lineup.default().slots + Slot(KEYBOARDS),
            listOf(Slot(HARMONICA), Slot(HARMONICA)),
        )
        overDefault.forEach { slots ->
            assertThrows("${slots.map { it.instrument }} accepted", IllegalArgumentException::class.java) {
                Lineup(slots)
            }
        }
    }

    @Test
    fun `the over-default message names the instrument and the counts`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            Lineup(listOf(Slot(GUITAR), Slot(GUITAR), Slot(GUITAR)))
        }

        assertEquals("Lineup has 3 GUITAR slots; the default allows at most 2 (D-18)", error.message)
    }
}
