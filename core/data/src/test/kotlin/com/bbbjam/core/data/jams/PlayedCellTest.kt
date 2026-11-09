package com.bbbjam.core.data.jams

import com.bbbjam.core.data.remote.SetlistRowDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same table as `isPlayedText_` in `backend/apps-script` (test `isPlayedText_ is true only for si`). */
class PlayedCellTest {
    @Test
    fun `si in any case, with or without the accent and with spaces around, is played`() {
        listOf("s\u00ed", "si", "S\u00cd", " Si ", "sI").forEach { assertTrue(it, isPlayedCell(it)) }
    }

    @Test
    fun `null, blank and any other text are not played`() {
        listOf(null, "", " ", "no", "x", "s\u00ed!", "sii", "yes", "\u2713").forEach {
            assertFalse(it.toString(), isPlayedCell(it))
        }
    }

    @Test
    fun `a row of an older script without the played key decodes with played null`() {
        val json = Json { ignoreUnknownKeys = true }
        val slots = (listOf("guitar1", "guitar2", "bass", "drums", "vocals", "harmonica", "keyboards"))
            .joinToString(",") { "\"$it\":null" }
        val old = """{"position":"1","songId":"a","title":"T","artist":"A","key":"A",""" +
            """"slots":{$slots},"extraParticipants":null}"""
        val row = json.decodeFromString<SetlistRowDto>(old)
        assertNull(row.played)
        assertEquals(
            "s\u00ed",
            json.decodeFromString<SetlistRowDto>(old.dropLast(1) + ""","played":"s\u00ed"}""").played,
        )
    }
}
