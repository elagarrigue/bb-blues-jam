package com.bbbjam.core.data.catalog.edit

import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Song
import com.bbbjam.core.model.SongId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongFormTest {
    private val catalog = listOf(
        Song(SongId("crossroads"), "Crossroads", "Eric Clapton", Key("A")),
        Song(SongId("red-house"), "Red House", "Jimi Hendrix", Key("Bb")),
    )

    @Test
    fun `slugs match the Apps Script vectors`() {
        assertEquals("cafe-madrid", songSlug("Café Madrid"))
        assertEquals("nandu", songSlug("Ñandú"))
        assertEquals("7-4", songSlug("7/4"))
        assertEquals("the-thrill-is-gone", songSlug("  The  Thrill, Is Gone!! "))
        assertEquals("", songSlug("!!!"))
    }

    @Test
    fun `text is trimmed and its whitespace runs collapsed`() {
        assertEquals("Café Madrid", normalizeSongText("  Café \t  Madrid "))
    }

    @Test
    fun `text problems are reported in the server order`() {
        assertEquals(SongTextProblem.EMPTY, songTextProblem("   "))
        assertEquals(SongTextProblem.TOO_LONG, songTextProblem("x".repeat(101)))
        assertNull(songTextProblem("x".repeat(100)))
        assertEquals(SongTextProblem.CONTROL, songTextProblem("a\u0007b"))
        for (prefix in listOf("=", "+", "-", "@")) {
            assertEquals(SongTextProblem.FORMULA_START, songTextProblem("${prefix}x"))
        }
        assertEquals(SongTextProblem.NO_LETTERS, songTextProblem("!!!"))
        assertNull(songTextProblem("7/4"))
    }

    @Test
    fun `a key is trimmed and a leading lowercase letter upper-cased`() {
        assertEquals(Key("A"), parseKeyInput(" a "))
        assertEquals(Key("Bm"), parseKeyInput("bm"))
        assertEquals(Key("F#"), parseKeyInput("f#"))
        assertEquals(Key("Bbm"), parseKeyInput("Bbm"))
        assertNull(parseKeyInput(""))
        assertNull(parseKeyInput("H"))
        assertNull(parseKeyInput("A#b"))
        assertNull(parseKeyInput("bB"))
    }

    @Test
    fun `a good form gives normalized fields`() {
        val check = checkSongForm("  Pride   and Joy ", "Stevie Ray Vaughan", "e", catalog, editing = null)

        assertTrue(check.canSubmit)
        assertEquals(SongFields("Pride and Joy", "Stevie Ray Vaughan", Key("E")), check.fields)
        assertFalse(check.duplicate)
    }

    @Test
    fun `every bad field is reported at once and no fields come back`() {
        val check = checkSongForm("", "=x", "z", catalog, editing = null)

        assertEquals(SongTextProblem.EMPTY, check.titleProblem)
        assertEquals(SongTextProblem.FORMULA_START, check.artistProblem)
        assertTrue(check.keyInvalid)
        assertNull(check.fields)
        assertFalse(check.canSubmit)
    }

    @Test
    fun `the same title and artist is a duplicate whatever the case or accents`() {
        val check = checkSongForm("CROSSROADS", "éric  clapton", "A", catalog, editing = null)

        assertTrue(check.duplicate)
        assertNull(check.fields)
    }

    @Test
    fun `a song being edited is not a duplicate of itself, but is of another`() {
        assertFalse(checkSongForm("Crossroads", "Eric Clapton", "E", catalog, SongId("crossroads")).duplicate)
        assertTrue(checkSongForm("Crossroads", "Eric Clapton", "E", catalog, SongId("red-house")).duplicate)
    }

    @Test
    fun `the same title with another artist is allowed`() {
        assertFalse(checkSongForm("Crossroads", "Cream", "A", catalog, editing = null).duplicate)
    }
}
