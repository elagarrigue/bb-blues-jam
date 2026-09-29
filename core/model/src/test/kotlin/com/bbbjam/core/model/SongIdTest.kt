package com.bbbjam.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SongIdTest {

    @Test
    fun `lowercase slugs are accepted`() {
        listOf("sweet-little-angel", "crossroads-robert-johnson", "abc123").forEach { text ->
            assertEquals(text, SongId(text).value)
            assertEquals(SongId(text), SongId.parseOrNull(text))
            assertEquals(text, SongId(text).toString())
        }
    }

    @Test
    fun `anything but a lowercase slug is rejected`() {
        listOf("Sweet-Little-Angel", "café-madrid", "-a", "a-", "a--b", "a b", "").forEach { text ->
            assertThrows("\"$text\" accepted", IllegalArgumentException::class.java) { SongId(text) }
            assertNull("\"$text\" parsed", SongId.parseOrNull(text))
        }
    }
}
