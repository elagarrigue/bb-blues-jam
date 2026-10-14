package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SongId
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `jam-status-created` (D-26), scenarios 1 to 4 on Próxima jam: a BORRADOR jam does not exist for a
 * musician, the next CREADA or PUBLICADA jam takes its place, a CREADA jam shows the date and the
 * "se está armando" card, and the admin sees all three states with their badge. The copy is written
 * out, never read from [NextJamCopy].
 */
class NextJamCreatedTest {
    private val today = LocalDate.of(2026, 10, 2)
    private val fetched = Freshness(Instant.parse("2026-10-02T14:59:00Z"), lastFailure = null, isRefreshing = false)
    private val song = JamSong(1, SongId("red-house"), "Red House", "Jimi Hendrix", Key("Bb"), Lineup.default())

    private fun jam(date: String, status: JamStatus, songs: List<JamSong> = listOf(song)) = Jam(
        date = LocalDate.parse(date),
        startTime = LocalTime.of(21, 0),
        venue = "La Macanuda",
        status = status,
        setlist = if (status == JamStatus.PUBLISHED &&
            songs.isEmpty()
        ) {
            Setlist.Available(emptyList())
        } else {
            Setlist.Available(songs)
        },
    )

    private fun snapshot(upcoming: Jam?, vararg heldBack: Jam) =
        JamsSnapshot(upcoming, emptyList(), fetched, heldBack.toList())

    private fun adminState() = AdminState(emptyList(), {}, {})

    private fun NextJamUiModel.asJam() = this as NextJamUiModel.Jam

    @Test
    fun `scenario 1, a cached BORRADOR jam with songs and no other jam is no upcoming jam for a musician`() {
        val model = snapshot(jam("2026-10-31", JamStatus.DRAFT)).toUiModel(today)

        assertTrue(model.toString(), model is NextJamUiModel.NoUpcomingJam)
        assertFalse(model.toString().contains("Red House"))
        assertNull((model as NextJamUiModel.NoUpcomingJam).adminHint)
    }

    @Test
    fun `scenario 2, a musician falls through the draft to the created jam, the admin keeps the draft`() {
        val snapshot = snapshot(jam("2026-11-07", JamStatus.DRAFT), jam("2026-11-28", JamStatus.CREATED))

        val musician = snapshot.toUiModel(today).asJam()
        assertEquals("Sábado 28 de noviembre · 21:00", musician.header.date)
        assertTrue(musician.setlist is SetlistUiModel.Withheld)
        assertNull(musician.admin)

        val admin = snapshot.toUiModel(today, admin = adminState()).asJam()
        assertEquals("Sábado 7 de noviembre · 21:00", admin.header.date)
        assertEquals("Borrador", admin.admin?.status?.badge)
    }

    @Test
    fun `scenario 3, a created jam whose cached setlist is available shows musicians the card only`() {
        val created = snapshot(jam("2026-10-31", JamStatus.CREATED))

        val model = created.toUiModel(today).asJam()

        assertEquals(
            SetlistUiModel.Withheld(
                DraftSetlistUiModel(
                    "En preparación",
                    "La lista se está armando",
                    "Cuando la organización la publique, vas a ver acá los temas, las tonalidades y los cupos libres.",
                ),
            ),
            model.setlist,
        )
        assertFalse(model.toString().contains("Red House"))
    }

    @Test
    fun `scenario 4, the admin sees the songs and a badge with its note in each of the three states`() {
        val expected = mapOf(
            JamStatus.DRAFT to Triple("Borrador", "Los músicos todavía no ven esta jam.", false),
            JamStatus.CREATED to Triple("Creada", "Los músicos ven la fecha, pero todavía no la lista.", false),
            JamStatus.PUBLISHED to Triple("Publicada", "Los músicos ya ven esta lista.", true),
        )
        expected.forEach { (status, badgeNoteAndPublished) ->
            val model = snapshot(jam("2026-10-31", status)).toUiModel(today, admin = adminState()).asJam()

            val admin = checkNotNull(model.admin)
            assertEquals("$status", listOf("Red House"), (model.setlist as SetlistUiModel.Songs).rows.map { it.title })
            assertEquals("$status", badgeNoteAndPublished.first, admin.status.badge)
            assertEquals("$status", badgeNoteAndPublished.second, admin.status.note)
            assertEquals("$status", badgeNoteAndPublished.third, admin.status.isPublished)
            // Publicar lista is offered for BORRADOR and CREADA with a song, never for PUBLICADA.
            assertEquals("$status", !badgeNoteAndPublished.third, admin.status.publish is PublishUiModel.Idle)
        }
    }

    @Test
    fun `the admin of a created jam with no song is not offered Publicar lista`() {
        val empty = jam("2026-10-31", JamStatus.CREATED, emptyList())

        val model = snapshot(empty).toUiModel(today, admin = adminState()).asJam()

        assertNull(model.admin?.status?.publish)
    }

    @Test
    fun `a filter on a musician list does not need the instrument`() {
        // The projection is applied before the filter: a draft never reaches the filter bar.
        val model = snapshot(jam("2026-10-31", JamStatus.DRAFT)).toUiModel(today, filter = setOf(Instrument.BASS))

        assertTrue(model is NextJamUiModel.NoUpcomingJam)
    }
}
