package com.bbbjam.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyTest {

    @Test
    fun `keys in the schema format are accepted`() {
        VALID.forEach { text ->
            assertEquals(text, Key(text).value)
            assertEquals(Key(text), Key.parseOrNull(text))
            assertEquals(text, Key(text).toString())
        }
    }

    @Test
    fun `keys outside the schema format are rejected`() {
        INVALID.forEach { text ->
            assertThrows("\"$text\" accepted", IllegalArgumentException::class.java) { Key(text) }
            assertNull("\"$text\" parsed", Key.parseOrNull(text))
        }
    }

    @Test
    fun `minor keys end in m`() {
        assertTrue(Key("Bbm").isMinor)
        assertTrue(Key("Bm").isMinor)
        assertFalse(Key("Bb").isMinor)
        assertFalse(Key("G").isMinor)
    }

    private companion object {
        val VALID = listOf("B", "Bm", "F#", "Bbm", "Ab", "C#m", "G")
        val INVALID = listOf("", "H", "b", "bm", "Bmaj", "B m", " B", "B ", "Bm ", "Do", "F##", "Bm7", "BM", "Si")
    }
}
