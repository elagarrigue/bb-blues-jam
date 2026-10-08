package com.bbbjam.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicianNameTest {

    @Test
    fun `trims and collapses whitespace`() {
        assertEquals("Tincho Ríos", MusicianName.parseOrNull("  Tincho   Ríos  ")?.value)
        assertEquals("A B", MusicianName.parseOrNull("A\u2003B")?.value)
    }

    @Test
    fun `accepts at most forty UTF-16 units`() {
        assertEquals(40, MusicianName.parseOrNull("a".repeat(40))?.value?.length)
        assertNull(MusicianName.parseOrNull("a".repeat(41)))
        assertNull(MusicianName.parseOrNull("😀".repeat(20) + "a"))
    }

    @Test
    fun `rejects unsafe or empty names`() {
        listOf(
            "", " \t ", "Pato\tLópez", "Pato\nLópez", "A;B", "A(B", "A)B",
            "=formula", "+Pato", "-Pato", "@Pato", "---", "()",
        ).forEach { assertNull("$it accepted", MusicianName.parseOrNull(it)) }
    }
}
