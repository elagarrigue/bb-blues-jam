package com.bbbjam.core.assistant

import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.JamStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PastJamsToolTest {
    private val july = jam(
        "2026-07-25",
        listOf(
            jamSong(
                1,
                "sweet-little-angel",
                "Sweet Little Angel",
                "B.B. King",
                "Bm",
                players = mapOf(Instrument.GUITAR to "Juan Pérez", Instrument.BASS to "Ana"),
                extras = listOf(ExtraParticipant("Leo", "saxo")),
            ),
            jamSong(2, "crossroads", "Crossroads", "Robert Johnson", "A", players = mapOf(Instrument.DRUMS to "Beto")),
        ),
    )
    private val june = jam(
        "2026-06-27",
        listOf(jamSong(1, "pappo-blues", "Pappo's Blues", "Pappo", "E", players = mapOf(Instrument.VOCALS to "Ana"))),
    )

    @Test
    fun `lists jams newest first with a date line and one line per song with the players and extras`() {
        val text = PastJamsTool.render(listOf(july, june), "")

        assertEquals(
            """
            fecha 2026-07-25
            sweet-little-angel | Sweet Little Angel | B.B. King | Bm | players: Juan Pérez (guitar), Ana (bass), Leo (saxo)
            crossroads | Crossroads | Robert Johnson | A | players: Beto (drums)
            fecha 2026-06-27
            pappo-blues | Pappo's Blues | Pappo | E | players: Ana (vocals)
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `a musician filter keeps only the songs they played, ignoring case and accents, extras included`() {
        assertEquals(
            """
            fecha 2026-07-25
            sweet-little-angel | Sweet Little Angel | B.B. King | Bm | players: Juan Pérez (guitar), Ana (bass), Leo (saxo)
            """.trimIndent(),
            PastJamsTool.render(listOf(july, june), "JUAN PEREZ"),
        )
        assertEquals(
            listOf("sweet-little-angel"),
            PastJamsTool.render(listOf(july, june), "leo").lines().drop(1).map { it.substringBefore(" | ") },
        )
    }

    @Test
    fun `jams where the musician did not play are omitted`() {
        val text = PastJamsTool.render(listOf(july, june), "ana")

        assertEquals(listOf("fecha 2026-07-25", "fecha 2026-06-27"), text.lines().filter { it.startsWith("fecha") })
        assertFalse("crossroads" in text)
        assertEquals(NO_ONE, PastJamsTool.render(listOf(july, june), "Nadie"))
    }

    @Test
    fun `at most 24 jams are listed`() {
        val many = (1..30).map { day -> jam("2025-01-%02d".format(day), july.songsOnly()) }

        val text = PastJamsTool.render(many, "")

        assertEquals(PastJamsTool.MAX_JAMS, text.lines().count { it.startsWith("fecha") })
        assertEquals("fecha 2025-01-01", text.lines().first())
    }

    @Test
    fun `an empty history says there are no past jams`() {
        assertEquals(PastJamsTool.NO_JAMS, PastJamsTool.render(emptyList(), ""))
    }

    @Test
    fun `a draft is never listed even if it is among the jams`() {
        val draft = jam("2026-09-26", july.songsOnly(), JamStatus.DRAFT)

        assertEquals(PastJamsTool.NO_JAMS, PastJamsTool.render(listOf(draft), ""))
    }

    @Test
    fun `the tool lists past jams only, never the upcoming one`() = runBlocking {
        val upcoming = jam("2026-11-28", listOf(jamSong(1, "crossroads", "Crossroads", "Robert Johnson")))
        val tool = PastJamsTool(FakePastJams(past = listOf(june), upcoming = upcoming))

        val text = tool.execute(PastJamsTool.Args(""))

        assertFalse("2026-11-28" in text)
        assertEquals("fecha 2026-06-27", text.lines().first())
        assertEquals("pastJams", tool.name)
    }

    private fun com.bbbjam.core.model.Jam.songsOnly() = (setlist as com.bbbjam.core.model.Setlist.Available).songs

    private companion object {
        const val NO_ONE = "No past jams recorded."
    }
}
