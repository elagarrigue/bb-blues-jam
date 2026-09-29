package com.bbbjam.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExtraParticipantTest {

    @Test
    fun `a name and a free-text instrument build an extra participant`() {
        val extra = ExtraParticipant("Ana", "percusión")

        assertEquals("Ana", extra.name)
        assertEquals("percusión", extra.instrument)
    }

    @Test
    fun `a blank name or instrument is rejected`() {
        listOf("" to "saxo", " " to "saxo", "Juan" to "", "Juan" to "  ").forEach { (name, instrument) ->
            assertThrows("\"$name\" / \"$instrument\" accepted", IllegalArgumentException::class.java) {
                ExtraParticipant(name, instrument)
            }
        }
    }

    @Test
    fun `the Otros cell separators are rejected in either field`() {
        val invalid = listOf(
            "Juan; Ana" to "saxo",
            "Juan (el de Lanús)" to "saxo",
            "Juan)" to "saxo",
            "Juan" to "saxo; flauta",
            "Juan" to "saxo (tenor)",
            "Juan" to "(saxo",
        )
        invalid.forEach { (name, instrument) ->
            assertThrows("\"$name\" / \"$instrument\" accepted", IllegalArgumentException::class.java) {
                ExtraParticipant(name, instrument)
            }
        }
    }
}
