package com.bbbjam.core.data.assistant

import com.bbbjam.core.model.Key
import com.bbbjam.core.model.SongId
import org.junit.Assert.assertEquals
import org.junit.Test

class ProposalValidatorTest {
    private val catalog = listOf(
        song("sweet-little-angel", "Sweet Little Angel", "B.B. King", "Bm"),
        song("pappo-blues", "Pappo's Blues", "Pappo", "E"),
        song("crossroads", "Crossroads", "Robert Johnson", "A"),
    )

    private fun proposed(id: String, title: String = "invented", key: String = "C", reason: String = "porque si") =
        ProposedSongDto(id, title, "invented artist", key, reason)

    private fun dto(vararg songs: ProposedSongDto, message: String = "Listo", questions: List<String> = emptyList()) =
        ProposalDto(songs.toList(), message, questions)

    @Test
    fun `catalog songs keep the model order`() {
        val result = ProposalValidator.validate(dto(proposed("crossroads"), proposed("sweet-little-angel")), catalog)

        assertEquals(listOf("crossroads", "sweet-little-angel"), result.songs.map { it.songId.value })
        assertEquals(emptyList<String>(), result.rejectedSongIds)
    }

    @Test
    fun `unknown, malformed and repeated ids are dropped and listed in order`() {
        val result = ProposalValidator.validate(
            dto(
                proposed("crossroads"),
                proposed("not-in-the-catalog"),
                proposed("Not A Slug!"),
                proposed("crossroads"),
                proposed("pappo-blues"),
            ),
            catalog,
        )

        assertEquals(listOf("crossroads", "pappo-blues"), result.songs.map { it.songId.value })
        assertEquals(listOf("not-in-the-catalog", "Not A Slug!", "crossroads"), result.rejectedSongIds)
    }

    @Test
    fun `title artist and key come from the catalog never from the model`() {
        val result = ProposalValidator.validate(
            dto(proposed("sweet-little-angel", title = "Other Title", key = "F#m", reason = " abre fuerte ")),
            catalog,
        )

        val first = result.songs.single()
        assertEquals(SongId("sweet-little-angel"), first.songId)
        assertEquals("Sweet Little Angel", first.title)
        assertEquals("B.B. King", first.artist)
        assertEquals(Key("Bm"), first.key)
        assertEquals("abre fuerte", first.reason)
    }

    @Test
    fun `no songs with questions is a valid proposal and blank questions are dropped`() {
        val result = ProposalValidator.validate(
            dto(message = " Necesito saber más ", questions = listOf(" ¿Cuántos temas? ", "  ")),
            catalog,
        )

        assertEquals(emptyList<ProposedSong>(), result.songs)
        assertEquals("Necesito saber más", result.message)
        assertEquals(listOf("¿Cuántos temas?"), result.questions)
    }

    @Test
    fun `a validated proposal round trips to the model's dto shape for history`() {
        val result = ProposalValidator.validate(dto(proposed("pappo-blues", reason = "sube el clima")), catalog)

        val back = ProposalValidator.toDto(result)

        assertEquals(listOf(ProposedSongDto("pappo-blues", "Pappo's Blues", "Pappo", "E", "sube el clima")), back.songs)
    }
}
