package com.bbbjam.core.data.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The typed view of the server error codes, with the wire strings written out here. */
class WriteRejectionTest {
    private val wire = mapOf(
        WriteRejection.SONG_ALREADY_IN_SETLIST to "song_already_in_setlist",
        WriteRejection.SONG_NOT_IN_SETLIST to "song_not_in_setlist",
        WriteRejection.DUPLICATE_SONG to "duplicate_song",
        WriteRejection.UNKNOWN_SONG to "unknown_song",
        WriteRejection.UNKNOWN_JAM to "unknown_jam",
        WriteRejection.JAM_NOT_EDITABLE to "jam_not_editable",
        WriteRejection.DUPLICATE_DATE to "duplicate_date",
        WriteRejection.SLOT_FILLED to "slot_filled",
        WriteRejection.INSTRUMENT_NOT_EXPANDABLE to "instrument_not_expandable",
        WriteRejection.INVALID_NAME to "invalid_name",
        WriteRejection.SLOT_TAKEN to "slot_taken",
        WriteRejection.SLOT_NOT_IN_LINEUP to "slot_not_in_lineup",
        WriteRejection.SLOT_EMPTY to "slot_empty",
        WriteRejection.SLOT_CHANGED to "slot_changed",
        WriteRejection.UNORDERED_SETLIST to "unordered_setlist",
        WriteRejection.JAM_EXISTS to "jam_exists",
        WriteRejection.TAB_EXISTS to "tab_exists",
        WriteRejection.PAST_DATE to "past_date",
        WriteRejection.SETLIST_CHANGED to "setlist_changed",
        WriteRejection.EMPTY_SETLIST to "empty_setlist",
        WriteRejection.INVALID_SONG to "invalid_song",
        WriteRejection.INVALID_KEY to "invalid_key",
        WriteRejection.INVALID_DATE to "invalid_date",
        WriteRejection.INVALID_TIME to "invalid_time",
        WriteRejection.INVALID_VENUE to "invalid_venue",
        WriteRejection.SONG_EXISTS to "song_exists",
        WriteRejection.SONG_IN_FUTURE_JAM to "song_in_future_jam",
        WriteRejection.DUPLICATE_ID to "duplicate_id",
        WriteRejection.INVALID_TITLE to "invalid_title",
        WriteRejection.INVALID_ARTIST to "invalid_artist",
    )

    @Test
    fun `every entry carries the exact wire code`() {
        wire.forEach { (entry, code) -> assertEquals(code, entry.code) }
        assertEquals(wire.size + 1, WriteRejection.entries.size)
    }

    @Test
    fun `every code maps back to its entry`() {
        WriteRejection.entries.filter { it != WriteRejection.Unknown }.forEach {
            assertEquals(it, WriteRejection.fromCode(it.code))
        }
        wire.forEach { (entry, code) -> assertEquals(entry, WriteRejection.fromCode(code)) }
    }

    @Test
    fun `codes are unique and not blank`() {
        val real = WriteRejection.entries.filter { it != WriteRejection.Unknown }
        assertEquals(real.size, real.map { it.code }.toSet().size)
        assertTrue(real.none { it.code.isBlank() })
    }

    @Test
    fun `an unlisted or empty code is Unknown`() {
        assertEquals(WriteRejection.Unknown, WriteRejection.fromCode("anything_else"))
        assertEquals(WriteRejection.Unknown, WriteRejection.fromCode(""))
        assertEquals(WriteRejection.Unknown, WriteRejection.fromCode("invalid_status"))
    }

    @Test
    fun `Rejected exposes the typed view and keeps the raw code`() {
        val rejected = WriteOutcome.Rejected("slot_taken")
        assertEquals(WriteRejection.SLOT_TAKEN, rejected.rejection)
        assertEquals("slot_taken", rejected.code)
        assertEquals(WriteOutcome.Rejected("slot_taken"), rejected)
        assertEquals("Rejected(code=slot_taken)", rejected.toString())
        assertEquals(WriteRejection.Unknown, WriteOutcome.Rejected("zzz").rejection)
        assertEquals("zzz", WriteOutcome.Rejected("zzz").code)
    }

    @Test
    fun `any invalid_ code is invalid input`() {
        assertTrue(WriteOutcome.Rejected("invalid_status").isInvalidInput)
        assertTrue(WriteOutcome.Rejected("invalid_name").isInvalidInput)
        assertFalse(WriteOutcome.Rejected("jam_exists").isInvalidInput)
        assertFalse(WriteOutcome.Rejected("").isInvalidInput)
    }
}
