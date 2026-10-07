package com.bbbjam.debug

import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.Slot
import com.bbbjam.core.model.SongId
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The debug-only demo upcoming jam (debug-demo-upcoming-jam). Pure data, never cached and never sent
 * anywhere. The venue says it is a demo so a screenshot can never pass for real data. Titles and
 * artists are copied from `docs/api-samples/catalog-seed.json`, so the songs also resolve against the
 * real catalog. Covered on purpose: every instrument open and filled somewhere, a song with no open
 * slot (`got-my-mojo-working`), one with every slot open (`the-thrill-is-gone`), instruments left out
 * of the lineup (`-`), an `Otros` with a short and one with a long free-text instrument, and keys with
 * accidentals (`Bb`, `F#m`).
 */
internal object DemoUpcomingJam {
    /** The demo's venue; also how the startup log tells the demo from a real jam. */
    const val VENUE = "Demo (solo debug)"

    /** How many days after today (Buenos Aires) the demo is dated, so it is always upcoming. */
    const val DAYS_AHEAD = 10L

    private const val START_TIME = "21:00"

    /**
     * The demo jam dated [DAYS_AHEAD] days after [today]. With [draft] it is a `DRAFT` holding the
     * same available songs (`unpublished-setlist-state`): the strongest case, because the songs are
     * in the data and the musician screens must still hide them.
     */
    fun on(today: LocalDate, draft: Boolean = false): Jam = Jam(
        date = today.plusDays(DAYS_AHEAD),
        startTime = LocalTime.parse(START_TIME),
        venue = VENUE,
        status = if (draft) JamStatus.DRAFT else JamStatus.PUBLISHED,
        setlist = Setlist.Available(SONGS.mapIndexed { index, song -> song.toJamSong(position = index + 1) }),
    )

    /** How long after the process started the live demo jam starts (`live-refresh-during-jam`). */
    val LIVE_START_AFTER: Duration = Duration.ofMinutes(10)

    /** The demo jam starting at [start] (Buenos Aires local date and time), for the live demo. */
    fun startingAt(start: LocalDateTime, draft: Boolean = false): Jam =
        on(start.toLocalDate(), draft).copy(date = start.toLocalDate(), startTime = start.toLocalTime())

    private class DemoSong(
        val id: String,
        val title: String,
        val artist: String,
        val key: String,
        val cells: List<String?>,
        val extras: List<ExtraParticipant> = emptyList(),
    ) {
        fun toJamSong(position: Int) = JamSong(
            position = position,
            songId = SongId(id),
            title = title,
            artist = artist,
            key = Key(key),
            lineup = lineupOf(cells),
            extraParticipants = extras,
        )
    }

    /** `-` leaves the column's instrument out of the lineup, null is an open slot, a name fills it. */
    private const val ABSENT = "-"

    private fun lineupOf(cells: List<String?>): Lineup {
        require(cells.size == Lineup.DEFAULT_INSTRUMENTS.size) { "One cell per default column" }
        return Lineup(
            Lineup.DEFAULT_INSTRUMENTS.zip(cells)
                .filter { (_, cell) -> cell != ABSENT }
                .map { (instrument: Instrument, cell) -> Slot(instrument, cell) },
        )
    }

    // Columns: guitar, guitar, bass, drums, vocals, harmonica, keyboards.
    private val SONGS = listOf(
        DemoSong(
            "sweet-little-angel",
            "Sweet Little Angel",
            "B.B. King",
            "Bb",
            listOf("Martín", null, "Lucía", null, "Sofía", null, "Tomás"),
        ),
        DemoSong(
            "got-my-mojo-working",
            "Got My Mojo Working",
            "Muddy Waters",
            "E",
            listOf("Martín", "Julián", "Lucía", "Diego", "Sofía", "Pablo", "Tomás"),
        ),
        DemoSong(
            "the-thrill-is-gone",
            "The Thrill Is Gone",
            "B.B. King",
            "F#m",
            listOf(null, null, null, null, null, null, null),
        ),
        DemoSong(
            "dust-my-broom",
            "Dust My Broom",
            "Elmore James",
            "D",
            listOf("Julián", ABSENT, null, "Diego", null, "Pablo", ABSENT),
            extras = listOf(ExtraParticipant("Valeria", "saxo")),
        ),
        DemoSong(
            "messin-with-the-kid",
            "Messin' With the Kid",
            "Junior Wells",
            "C",
            listOf(null, null, "Lucía", "Diego", null, null, ABSENT),
        ),
        DemoSong(
            "crossroads",
            "Crossroads",
            "Eric Clapton",
            "A",
            listOf("Martín", null, null, "Diego", "Julián", ABSENT, "Tomás"),
            extras = listOf(
                ExtraParticipant("Camila", "percusión de mano, pandereta y coros en el último estribillo"),
            ),
        ),
        DemoSong(
            "tres-palabras",
            "Tres Palabras",
            "La Mississippi",
            "Am",
            listOf(null, "Martín", "Lucía", null, "Sofía", ABSENT, null),
        ),
        DemoSong(
            "the-score",
            "The Score",
            "Robert Cray",
            "G",
            listOf("Julián", null, "Lucía", "Diego", null, "Pablo", null),
        ),
    )
}
