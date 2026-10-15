package com.bbbjam.core.data.assistant

import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class JamScheduleToolTest {
    private val today = LocalDate.of(2026, 10, 14)
    private val fresh = Freshness(fetchedAt = null, lastFailure = null, isRefreshing = false)
    private val upcoming =
        Jam(LocalDate.of(2026, 10, 31), LocalTime.of(21, 30), "La Macanuda", JamStatus.DRAFT, Setlist.Withheld)
    private val past = Jam(
        LocalDate.of(2026, 9, 26),
        LocalTime.of(20, 0),
        "Bar del Sur",
        JamStatus.PUBLISHED,
        Setlist.Available(emptyList()),
    )

    @Test
    fun `an upcoming draft is also the last jam`() {
        assertEquals(
            """
            today 2026-10-14 (miércoles 14 de octubre, Buenos Aires)
            upcoming 2026-10-31 (sábado 31 de octubre) 21:30 | La Macanuda | BORRADOR
            last 2026-10-31 (sábado 31 de octubre) 21:30 | La Macanuda
            """.trimIndent(),
            JamScheduleTool.render(JamsSnapshot(upcoming, listOf(past), fresh), today),
        )
    }

    @Test
    fun `with no upcoming jam the last one is the newest past jam`() {
        assertEquals(
            """
            today 2026-10-14 (miércoles 14 de octubre, Buenos Aires)
            upcoming none
            last 2026-09-26 (sábado 26 de septiembre) 20:00 | Bar del Sur
            """.trimIndent(),
            JamScheduleTool.render(JamsSnapshot(null, listOf(past), fresh), today),
        )
    }

    @Test
    fun `a published upcoming jam says PUBLICADA and no jams at all says none twice`() {
        val published = upcoming.copy(status = JamStatus.PUBLISHED, setlist = Setlist.Available(emptyList()))
        assertEquals(
            "upcoming 2026-10-31 (sábado 31 de octubre) 21:30 | La Macanuda | PUBLICADA",
            JamScheduleTool.render(JamsSnapshot(published, emptyList(), fresh), today).lines()[1],
        )
        assertEquals(
            "today 2026-10-14 (miércoles 14 de octubre, Buenos Aires)\nupcoming none\nlast none",
            JamScheduleTool.render(JamsSnapshot(null, emptyList(), fresh), today),
        )
    }

    @Test
    fun `a created upcoming jam says CREADA`() {
        val created = upcoming.copy(status = JamStatus.CREATED)
        assertEquals(
            "upcoming 2026-10-31 (sábado 31 de octubre) 21:30 | La Macanuda | CREADA",
            JamScheduleTool.render(JamsSnapshot(created, emptyList(), fresh), today).lines()[1],
        )
    }

    @Test
    fun `the tool reads the repository and the calendar`() = runBlocking {
        val tool = JamScheduleTool(FakeJams(listOf(past), upcoming), fixedCalendar())

        val text = tool.execute(JamScheduleTool.Args())

        assertEquals(JamScheduleTool.render(JamsSnapshot(upcoming, listOf(past), fresh), today), text)
    }
}
