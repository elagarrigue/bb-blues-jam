package com.bbbjam.core.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

/** The streamed validation and the final one share an accumulator, so for the same answer they agree. */
class DraftTrackerTest {
    private val catalog = listOf(
        song("sweet-little-angel", "Sweet Little Angel", "B.B. King", "Bm"),
        song("pappo-blues", "Pappo's Blues", "Pappo", "E"),
        song("crossroads", "Crossroads", "Robert Johnson", "A"),
    )

    private fun entry(id: String, title: String = "inventado", key: String = "Z", reason: String = "  Va bien  ") =
        """{"songId":" $id ","title":"$title","artist":"nadie","key":"$key","reason":"$reason"}"""

    private val text = """{"songs":[${
        listOf(
            entry("pappo-blues"),
            entry("made-up"),
            entry("NOT VALID"),
            entry("pappo-blues"),
            entry("crossroads", title = "otro"),
        ).joinToString(",")
    }],"message":"m","questions":[]}"""

    @Test
    fun `the streamed songs equal the final validation of the same answer for any chunking`() {
        val dto = kotlinx.serialization.json.Json.decodeFromString(ProposalDto.serializer(), text)
        val final = ProposalValidator.validate(dto, catalog)
        for (size in listOf(1, 2, 3, 5, 8, 13, 40, text.length)) {
            val drafts = ArrayList<AssistantDraft>()
            val tracker = DraftTracker(catalog) { drafts += it }

            text.chunked(size).forEach(tracker::feed)

            assertEquals("chunk size $size", final.songs, drafts.last().songs)
        }
    }

    @Test
    fun `discard empties the draft once and starts a fresh round`() {
        val drafts = ArrayList<AssistantDraft>()
        val tracker = DraftTracker(catalog) { drafts += it }

        tracker.feed("""{"songs":[${entry("pappo-blues")},""")
        tracker.discard()
        tracker.discard()
        tracker.feed(text)

        assertEquals(
            listOf(listOf("pappo-blues"), emptyList(), listOf("pappo-blues", "crossroads")),
            drafts.map { draft -> draft.songs.map { it.songId.value } },
        )
    }
}
