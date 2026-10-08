package com.bbbjam.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SlotPositionTest {

    @Test
    fun `position is a positive one-based value`() {
        assertEquals(SlotPosition(1), SlotPosition(1))
        assertThrows(IllegalArgumentException::class.java) { SlotPosition(0) }
        assertThrows(IllegalArgumentException::class.java) { SlotPosition(-1) }
    }

    @Test
    fun `lineup ordinals stay tied to instrument slots despite open-filled partitioning`() {
        val lineup = Lineup(
            listOf(
                Slot(Instrument.GUITAR, "Pato"),
                Slot(Instrument.GUITAR),
                Slot(Instrument.BASS),
            ),
        )

        assertEquals(SlotPosition(1), lineup.positionOf(0))
        assertEquals(SlotPosition(2), lineup.positionOf(1))
        assertEquals(SlotPosition(1), lineup.positionOf(2))
        assertEquals(Slot(Instrument.GUITAR), lineup.slotAt(Instrument.GUITAR, SlotPosition(2)))
        assertNull(lineup.slotAt(Instrument.GUITAR, SlotPosition(3)))
        assertNull(lineup.positionOf(-1))
        assertNull(lineup.positionOf(lineup.slots.size))
    }
}
