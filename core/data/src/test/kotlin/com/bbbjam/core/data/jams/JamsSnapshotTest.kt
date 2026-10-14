package com.bbbjam.core.data.jams

import com.bbbjam.core.data.Freshness
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JamsSnapshotTest {
    private val fresh =
        Freshness(fetchedAt = java.time.Instant.ofEpochMilli(7), lastFailure = null, isRefreshing = false)

    @Test
    fun `an upcoming draft falls through to the next created jam`() {
        val snapshot = JamsSnapshot(
            upcoming = jam("2026-11-07", JamStatus.DRAFT),
            past = emptyList(),
            freshness = fresh,
            heldBack = listOf(jam("2026-11-28", JamStatus.CREATED), jam("2026-12-19", JamStatus.PUBLISHED)),
        )

        val musicians = snapshot.forMusicians()

        assertEquals(LocalDate.of(2026, 11, 28), musicians.upcoming?.date)
        assertEquals(listOf(LocalDate.of(2026, 12, 19)), musicians.heldBack.map { it.date })
        assertEquals(fresh, musicians.freshness)
        // The raw snapshot is untouched: the admin still sees the draft.
        assertEquals(LocalDate.of(2026, 11, 7), snapshot.upcoming?.date)
    }

    @Test
    fun `with only drafts ahead there is no upcoming jam for musicians`() {
        val snapshot =
            JamsSnapshot(
                jam("2026-11-07", JamStatus.DRAFT),
                emptyList(),
                fresh,
                listOf(jam("2026-11-28", JamStatus.DRAFT)),
            )

        val musicians = snapshot.forMusicians()

        assertNull(musicians.upcoming)
        assertEquals(emptyList<Jam>(), musicians.heldBack)
    }

    @Test
    fun `a past draft is dropped, a past created jam is kept, and the order holds`() {
        val snapshot = JamsSnapshot(
            upcoming = null,
            past = listOf(
                jam("2026-09-26", JamStatus.PUBLISHED),
                jam("2026-08-29", JamStatus.DRAFT),
                jam("2026-07-25", JamStatus.CREATED),
            ),
            freshness = fresh,
        )

        assertEquals(
            listOf(LocalDate.of(2026, 9, 26), LocalDate.of(2026, 7, 25)),
            snapshot.forMusicians().past.map { it.date },
        )
    }

    @Test
    fun `the projection is idempotent and leaves a snapshot with no drafts equal to itself`() {
        val snapshot = JamsSnapshot(
            jam("2026-11-07", JamStatus.DRAFT),
            listOf(jam("2026-08-29", JamStatus.DRAFT)),
            fresh,
            listOf(jam("2026-11-28", JamStatus.CREATED)),
        )

        assertEquals(snapshot.forMusicians(), snapshot.forMusicians().forMusicians())
        val clean = JamsSnapshot(jam("2026-11-28", JamStatus.PUBLISHED), emptyList(), fresh)
        assertEquals(clean, clean.forMusicians())
    }

    private fun jam(date: String, status: JamStatus) = Jam(
        LocalDate.parse(date),
        LocalTime.of(21, 0),
        "Lugar",
        status,
        if (status == JamStatus.PUBLISHED) Setlist.Available(emptyList()) else Setlist.Withheld,
    )
}
