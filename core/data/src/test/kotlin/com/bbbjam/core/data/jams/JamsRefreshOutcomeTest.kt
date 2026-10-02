package com.bbbjam.core.data.jams

import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.Fixtures
import com.bbbjam.core.data.remote.JamDto
import com.bbbjam.core.data.remote.SetlistRowDto
import com.bbbjam.core.data.remote.SlotsDto
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class JamsRefreshOutcomeTest {

    @Test
    fun `the updated line has counts, dates, indexes and issue names`() {
        val mapped = JamsMapper.map(Fixtures.sampleJams("jams-edge.json"))
        val outcome = JamsRefreshOutcome.Updated(
            mapped.jams.size,
            mapped.rejected,
            mapped.issues,
            listOf(LocalDate.of(2026, 9, 26)),
        )

        assertEquals(
            "jams refresh: updated 5 jams, 4 rejected, 2 setlist issues, 1 held back; " +
                "rejected #3 2026-06-27: InvalidStatus; rejected #6 2026-03-28: DuplicateDate; " +
                "rejected #7 2026-03-28: DuplicateDate; rejected #8 Config: InvalidDate; " +
                "2026-05-30: setlistError missing_tab; 2026-04-25: setlistError missing_header; " +
                "held back 2026-09-26",
            outcome.toLogLine(),
        )
    }

    @Test
    fun `row issues are logged by index and never with a musician's name`() {
        val slots = SlotsDto("Ana", "Luis", null, null, null, null, null)
        val rows = listOf(
            SetlistRowDto("1", "crossroads", "Crossroads", "Eric Clapton", "A", slots, "Juan saxo; Marta (voz)"),
            SetlistRowDto("2", "crossroads", "Crossroads", "Eric Clapton", "Bb m", slots, null),
        )
        val mapped = JamsMapper.map(listOf(JamDto("2026-07-25", "21:00", "La Macanuda", "PUBLICADA", rows, null)))

        val line = JamsRefreshOutcome.Updated(1, mapped.rejected, mapped.issues, emptyList()).toLogLine()

        assertEquals(
            "jams refresh: updated 1 jams, 0 rejected, 2 setlist issues, 0 held back; " +
                "2026-07-25 row #1 kept: MalformedExtraParticipant; 2026-07-25 row #2 dropped: InvalidKey",
            line,
        )
        listOf("Ana", "Luis", "Juan", "Marta").forEach { assertFalse(it, it in line) }
    }

    @Test
    fun `the failed line names the failure kind`() {
        assertEquals(
            "jams refresh: failed Storage(detail=SQLiteFullException)",
            JamsRefreshOutcome.Failed(DataFailure.Storage("SQLiteFullException")).toLogLine(),
        )
        assertEquals("jams refresh: failed Offline", JamsRefreshOutcome.Failed(DataFailure.Offline).toLogLine())
    }
}
