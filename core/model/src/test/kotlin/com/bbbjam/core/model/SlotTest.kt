package com.bbbjam.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotTest {

    @Test
    fun `a slot with no musician is open`() {
        val slot = Slot(Instrument.BASS)

        assertTrue(slot.isOpen)
        assertFalse(slot.isFilled)
    }

    @Test
    fun `a slot with a musician is filled`() {
        val slot = Slot(Instrument.BASS, "Tincho")

        assertFalse(slot.isOpen)
        assertTrue(slot.isFilled)
    }

    @Test
    fun `a blank musician name is rejected`() {
        listOf("", " ", "\t").forEach { blank ->
            assertThrows("\"$blank\" accepted", IllegalArgumentException::class.java) {
                Slot(Instrument.BASS, blank)
            }
        }
    }
}
