package com.bbbjam.core.data.jams

import com.bbbjam.core.data.Fixtures
import com.bbbjam.core.data.remote.JamDto
import com.bbbjam.core.data.remote.SetlistErrorDto
import com.bbbjam.core.data.remote.SetlistRowDto
import com.bbbjam.core.data.remote.SlotsDto
import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SetlistProblem
import com.bbbjam.core.model.Slot
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JamsMapperTest {

    @Test
    fun `the seed sample maps to one published jam with 13 songs, seven open slots each, tab copies as titles`() {
        val mapped = JamsMapper.map(Fixtures.sampleJams("jams-seed.json"))

        assertEquals(emptyList<RejectedJam>(), mapped.rejected)
        assertEquals(emptyList<SetlistIssue>(), mapped.issues)
        val jam = mapped.jams.single().jam
        assertEquals(LocalDate.of(2026, 7, 25), jam.date)
        assertEquals(LocalTime.of(21, 0), jam.startTime)
        assertEquals("La Macanuda", jam.venue)
        assertEquals(JamStatus.PUBLISHED, jam.status)
        val songs = jam.available().songs
        assertEquals((1..13).toList(), songs.map { it.position })
        assertTrue(songs.all { it.lineup == Lineup.default() && it.extraParticipants.isEmpty() })
        assertEquals("Sweet Little Angel", songs.first().title)
        assertEquals("B.B. King", songs.first().artist)
        assertEquals(Key("B"), songs.first().key)
        assertEquals(0, jam.available().droppedRows)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), mapped.jams.single().slotColumns.getValue(1))
    }

    @Test
    fun `the seed CSV files map to the same jams as the seed sample`() {
        assertEquals(JamsMapper.map(Fixtures.sampleJams("jams-seed.json")), JamsMapper.map(Fixtures.seedJams()))
    }

    @Test
    fun `the edge sample keeps five jams with their setlist states and rejects four rows`() {
        val mapped = JamsMapper.map(Fixtures.sampleJams("jams-edge.json"))

        val states = mapped.jams.associate { it.jam.date.toString() to it.jam.setlist }
        assertEquals(
            listOf("2026-08-29", "2026-09-26", "2026-05-30", "2026-04-25", "2026-02-28"),
            states.keys.toList(),
        )
        assertTrue(states.getValue("2026-08-29") is Setlist.Available)
        assertEquals(Setlist.Withheld, states.getValue("2026-09-26"))
        assertEquals(Setlist.Unavailable(SetlistProblem.MISSING_TAB), states.getValue("2026-05-30"))
        assertEquals(Setlist.Unavailable(SetlistProblem.INVALID_TAB), states.getValue("2026-04-25"))
        assertTrue(states.getValue("2026-02-28") is Setlist.Available)
        assertEquals(JamStatus.DRAFT, mapped.jams[1].jam.status)
        assertEquals(
            listOf(
                RejectedJam(3, "2026-06-27", listOf(JamIssue.InvalidStatus)),
                RejectedJam(6, "2026-03-28", listOf(JamIssue.DuplicateDate)),
                RejectedJam(7, "2026-03-28", listOf(JamIssue.DuplicateDate)),
                RejectedJam(8, "Config", listOf(JamIssue.InvalidDate)),
            ),
            mapped.rejected,
        )
        assertEquals(
            listOf(
                SetlistIssue.SetlistError(LocalDate.of(2026, 5, 30), "missing_tab"),
                SetlistIssue.SetlistError(LocalDate.of(2026, 4, 25), "missing_header"),
            ),
            mapped.issues,
        )
    }

    @Test
    fun `the edge sample's published jam is sorted by position with names, a dash and Otros`() {
        val mapped = JamsMapper.map(Fixtures.sampleJams("jams-edge.json")).jams.first()
        val songs = mapped.jam.available().songs

        assertEquals(listOf(1, 2, 3), songs.map { it.position })
        assertEquals(listOf("the-thrill-is-gone", "crossroads", "got-my-mojo-working"), songs.map { it.songId.value })
        val thrill = songs[0]
        assertEquals(Key("Bm"), thrill.key)
        assertEquals(
            listOf(
                Slot(Instrument.GUITAR, "Ana"),
                Slot(Instrument.GUITAR, "Luis"),
                Slot(Instrument.BASS, "Marta"),
                Slot(Instrument.DRUMS, "Diego"),
                Slot(Instrument.VOCALS, "Sofía"),
                Slot(Instrument.KEYBOARDS),
            ),
            thrill.lineup.slots,
        )
        assertEquals(
            listOf(ExtraParticipant("Juan", "saxo"), ExtraParticipant("Ana", "percusión")),
            thrill.extraParticipants,
        )
        val crossroads = songs[1]
        assertEquals(Slot(Instrument.GUITAR, "Pedro"), crossroads.lineup.slots.first())
        assertEquals(1, crossroads.lineup.slots.count { it.instrument == Instrument.GUITAR })
        assertEquals(5, crossroads.lineup.openSlots.size)
        assertEquals(Lineup.default(), songs[2].lineup)
        assertEquals(listOf(0, 1, 2, 3, 4, 6), mapped.slotColumns.getValue(1))
        assertEquals(listOf(0, 2, 3, 4, 5, 6), mapped.slotColumns.getValue(2))
    }

    @Test
    fun `padded cells are trimmed before they are read`() {
        val row = row(position = " 1 ", songId = " crossroads ", key = " A ", extras = "  Juan (saxo) ; ")
        val mapped = JamsMapper.map(
            listOf(jam(date = " 2026-07-25 ", startTime = " 21:00 ", venue = " Bar ", setlist = listOf(row))),
        )

        val jam = mapped.jams.single().jam
        assertEquals("Bar", jam.venue)
        val song = jam.available().songs.single()
        assertEquals("crossroads", song.songId.value)
        assertEquals(listOf(ExtraParticipant("Juan", "saxo")), song.extraParticipants)
    }

    @Test
    fun `a jam row with a missing or invalid cell is rejected with every issue`() {
        val cases = listOf(
            jam(date = null) to listOf(JamIssue.MissingDate),
            jam(date = "  ") to listOf(JamIssue.MissingDate),
            jam(date = "2026-02-30") to listOf(JamIssue.InvalidDate),
            jam(date = "2026-7-25") to listOf(JamIssue.InvalidDate),
            jam(date = "25/07/2026") to listOf(JamIssue.InvalidDate),
            jam(startTime = null) to listOf(JamIssue.MissingStartTime),
            jam(startTime = "9:00") to listOf(JamIssue.InvalidStartTime),
            jam(startTime = "24:00") to listOf(JamIssue.InvalidStartTime),
            jam(startTime = "21:60") to listOf(JamIssue.InvalidStartTime),
            jam(startTime = "21:00:00") to listOf(JamIssue.InvalidStartTime),
            jam(venue = null) to listOf(JamIssue.MissingVenue),
            jam(status = null) to listOf(JamIssue.MissingStatus),
            jam(status = "Publicada") to listOf(JamIssue.InvalidStatus),
            jam(status = "borrador") to listOf(JamIssue.InvalidStatus),
            jam(status = "PUBLICADO") to listOf(JamIssue.InvalidStatus),
            jam(date = "x", startTime = "", venue = null, status = "?") to
                listOf(JamIssue.InvalidDate, JamIssue.MissingStartTime, JamIssue.MissingVenue, JamIssue.InvalidStatus),
        )
        cases.forEach { (dto, issues) ->
            val mapped = JamsMapper.map(listOf(dto))

            assertEquals("$dto", emptyList<MappedJam>(), mapped.jams)
            assertEquals("$dto", listOf(RejectedJam(1, dto.date?.trim()?.ifEmpty { null }, issues)), mapped.rejected)
        }
    }

    @Test
    fun `a valid time and both statuses are read exactly`() {
        val mapped = JamsMapper.map(
            listOf(
                jam(date = "2026-07-25", startTime = "09:30"),
                jam(date = "2026-08-29", status = "BORRADOR", setlist = null),
            ),
        )

        assertEquals(LocalTime.of(9, 30), mapped.jams[0].jam.startTime)
        assertEquals(listOf(JamStatus.PUBLISHED, JamStatus.DRAFT), mapped.jams.map { it.jam.status })
    }

    @Test
    fun `every row sharing a date is rejected, an invalid one included`() {
        val mapped = JamsMapper.map(
            listOf(
                jam(date = "2026-07-25"),
                jam(date = "2026-08-29"),
                jam(date = "2026-07-25", status = "BORRADOR", setlist = null),
                jam(date = "2026-07-25", status = "Publicada"),
            ),
        )

        assertEquals(listOf(LocalDate.of(2026, 8, 29)), mapped.jams.map { it.jam.date })
        assertEquals(
            listOf(
                RejectedJam(1, "2026-07-25", listOf(JamIssue.DuplicateDate)),
                RejectedJam(3, "2026-07-25", listOf(JamIssue.DuplicateDate)),
                RejectedJam(4, "2026-07-25", listOf(JamIssue.InvalidStatus, JamIssue.DuplicateDate)),
            ),
            mapped.rejected,
        )
    }

    @Test
    fun `a draft is always withheld, and a setlist sent with it is ignored and reported`() {
        val mapped = JamsMapper.map(
            listOf(
                jam(date = "2026-07-25", status = "BORRADOR", setlist = listOf(row())),
                jam(date = "2026-08-29", status = "BORRADOR", setlist = null),
                jam(
                    date = "2026-09-26",
                    status = "BORRADOR",
                    setlist = null,
                    error = SetlistErrorDto("missing_tab", "m"),
                ),
            ),
        )

        assertEquals(List(3) { Setlist.Withheld }, mapped.jams.map { it.jam.setlist })
        assertEquals(
            listOf(
                SetlistIssue.DraftSetlistIgnored(LocalDate.of(2026, 7, 25)),
                SetlistIssue.DraftSetlistIgnored(LocalDate.of(2026, 9, 26)),
            ),
            mapped.issues,
        )
        assertTrue(mapped.jams.all { it.slotColumns.isEmpty() })
    }

    @Test
    fun `a published jam's setlistError makes it unavailable, never empty`() {
        val cases = mapOf(
            "missing_tab" to SetlistProblem.MISSING_TAB,
            "missing_header" to SetlistProblem.INVALID_TAB,
            "duplicate_header" to SetlistProblem.INVALID_TAB,
            "something_new" to SetlistProblem.UNKNOWN,
        )
        cases.forEach { (code, problem) ->
            val mapped = JamsMapper.map(listOf(jam(setlist = null, error = SetlistErrorDto(code, "m"))))

            assertEquals(Setlist.Unavailable(problem), mapped.jams.single().jam.setlist)
            assertEquals(listOf(SetlistIssue.SetlistError(DATE, code)), mapped.issues)
        }
    }

    @Test
    fun `a published jam with neither a setlist nor an error is unavailable for an unknown reason`() {
        val mapped = JamsMapper.map(listOf(jam(setlist = null)))

        assertEquals(Setlist.Unavailable(SetlistProblem.UNKNOWN), mapped.jams.single().jam.setlist)
        assertEquals(listOf(SetlistIssue.SetlistMissing(DATE)), mapped.issues)
    }

    @Test
    fun `a published jam with an empty tab has an empty available setlist`() {
        val mapped = JamsMapper.map(listOf(jam(setlist = emptyList())))

        assertEquals(Setlist.Available(emptyList()), mapped.jams.single().jam.setlist)
        assertEquals(emptyList<SetlistIssue>(), mapped.issues)
    }

    @Test
    fun `a bad row drops only itself and the other positions are kept as the Sheet numbers them`() {
        val mapped = JamsMapper.map(
            listOf(jam(setlist = listOf(row("3", "got-my-mojo-working"), row("2", key = "Bb m"), row("1")))),
        )

        val setlist = mapped.jams.single().jam.available()
        assertEquals(listOf(1, 3), setlist.songs.map { it.position })
        assertEquals(listOf("crossroads", "got-my-mojo-working"), setlist.songs.map { it.songId.value })
        assertEquals(1, setlist.droppedRows)
        assertEquals(listOf(SetlistIssue.RowIssues(DATE, 2, listOf(SetlistRowIssue.InvalidKey))), mapped.issues)
        assertEquals(setOf(1, 3), mapped.jams.single().slotColumns.keys)
    }

    @Test
    fun `every setlist row rule drops the row with its issue`() {
        val cases = listOf(
            row(position = null) to listOf(SetlistRowIssue.MissingPosition),
            row(position = "2.5") to listOf(SetlistRowIssue.InvalidPosition),
            row(position = "0") to listOf(SetlistRowIssue.InvalidPosition),
            row(position = "-1") to listOf(SetlistRowIssue.InvalidPosition),
            row(position = "dos") to listOf(SetlistRowIssue.InvalidPosition),
            row(songId = null) to listOf(SetlistRowIssue.MissingSongId),
            row(songId = "Crossroads") to listOf(SetlistRowIssue.InvalidSongId),
            row(title = null) to listOf(SetlistRowIssue.MissingTitle),
            row(artist = " ") to listOf(SetlistRowIssue.MissingArtist),
            row(key = null) to listOf(SetlistRowIssue.MissingKey),
            row(key = "Bb m") to listOf(SetlistRowIssue.InvalidKey),
            row(key = "H") to listOf(SetlistRowIssue.InvalidKey),
            row(position = "x", songId = null, title = null, artist = null, key = "do") to listOf(
                SetlistRowIssue.InvalidPosition,
                SetlistRowIssue.MissingSongId,
                SetlistRowIssue.MissingTitle,
                SetlistRowIssue.MissingArtist,
                SetlistRowIssue.InvalidKey,
            ),
        )
        cases.forEach { (bad, issues) ->
            val mapped = JamsMapper.map(listOf(jam(setlist = listOf(row("5", "got-my-mojo-working"), bad))))

            val setlist = mapped.jams.single().jam.available()
            assertEquals("$bad", listOf(5), setlist.songs.map { it.position })
            assertEquals("$bad", 1, setlist.droppedRows)
            assertEquals("$bad", listOf(SetlistIssue.RowIssues(DATE, 2, issues)), mapped.issues)
        }
    }

    @Test
    fun `a missing key is never filled from anywhere, even for a catalog song with a default key`() {
        // the-thrill-is-gone has tono_default Bm in the catalog; the mapper has no catalog to read (D-08).
        val mapped = JamsMapper.map(listOf(jam(setlist = listOf(row("1", "the-thrill-is-gone", key = null)))))

        assertEquals(Setlist.Unavailable(SetlistProblem.INVALID_ROWS), mapped.jams.single().jam.setlist)
        assertEquals(listOf(SetlistIssue.RowIssues(DATE, 1, listOf(SetlistRowIssue.MissingKey))), mapped.issues)
    }

    @Test
    fun `every row sharing a position is dropped, an invalid one included`() {
        val mapped = JamsMapper.map(
            listOf(jam(setlist = listOf(row("1"), row("2", "got-my-mojo-working"), row("2"), row("2", key = "X")))),
        )

        val setlist = mapped.jams.single().jam.available()
        assertEquals(listOf(1), setlist.songs.map { it.position })
        assertEquals(3, setlist.droppedRows)
        assertEquals(
            listOf(
                SetlistIssue.RowIssues(DATE, 2, listOf(SetlistRowIssue.DuplicatePosition)),
                SetlistIssue.RowIssues(DATE, 3, listOf(SetlistRowIssue.DuplicatePosition)),
                SetlistIssue.RowIssues(DATE, 4, listOf(SetlistRowIssue.InvalidKey, SetlistRowIssue.DuplicatePosition)),
            ),
            mapped.issues,
        )
    }

    @Test
    fun `a setlist whose every row is invalid is unavailable, never an empty list`() {
        val mapped = JamsMapper.map(listOf(jam(setlist = listOf(row("1", key = null), row("1"), row("1")))))

        assertEquals(Setlist.Unavailable(SetlistProblem.INVALID_ROWS), mapped.jams.single().jam.setlist)
        assertEquals(3, mapped.issues.size)
        assertTrue(mapped.issues.all { (it as SetlistIssue.RowIssues).dropped })
        assertEquals(emptyMap<Int, List<Int>>(), mapped.jams.single().slotColumns)
    }

    @Test
    fun `slot cells give open, filled or no slot, and slot columns follow the dash`() {
        val dashes = slots(" - ", "Pedro", "-", "-", "-", "-", "-")
        val noLineup = slots("-", "-", "-", "-", "-", "-", "-")
        val mapped = JamsMapper.map(
            listOf(jam(setlist = listOf(row("1", slots = dashes), row("2", "got-my-mojo-working", slots = noLineup)))),
        ).jams.single()

        val songs = mapped.jam.available().songs
        assertEquals(listOf(Slot(Instrument.GUITAR, "Pedro")), songs[0].lineup.slots)
        assertEquals(Lineup(emptyList()), songs[1].lineup)
        assertEquals(listOf(1), mapped.slotColumns.getValue(1))
        assertEquals(emptyList<Int>(), mapped.slotColumns.getValue(2))
    }

    @Test
    fun `Otros accepts entries with or without a space and drops only a malformed entry`() {
        val cases = mapOf(
            "Juan(saxo)" to (listOf(ExtraParticipant("Juan", "saxo")) to 0),
            " Ana ( percusión ) " to (listOf(ExtraParticipant("Ana", "percusión")) to 0),
            "María José (voz 2); Juan (saxo);" to
                (listOf(ExtraParticipant("María José", "voz 2"), ExtraParticipant("Juan", "saxo")) to 0),
            ";;" to (emptyList<ExtraParticipant>() to 0),
            "Juan saxo" to (emptyList<ExtraParticipant>() to 1),
            "(saxo); Juan (); Ana (bajo)" to (listOf(ExtraParticipant("Ana", "bajo")) to 2),
            "Juan (saxo) x; Juan (sa(x)o); Juan ( )" to (emptyList<ExtraParticipant>() to 3),
        )
        cases.forEach { (cell, expected) ->
            val (extras, malformed) = expected
            val mapped = JamsMapper.map(listOf(jam(setlist = listOf(row(extras = cell)))))

            val song = mapped.jams.single().jam.available().songs.single()
            assertEquals(cell, extras, song.extraParticipants)
            val issues = if (malformed == 0) {
                emptyList()
            } else {
                listOf(SetlistIssue.RowIssues(DATE, 1, List(malformed) { SetlistRowIssue.MalformedExtraParticipant }))
            }
            assertEquals(cell, issues, mapped.issues)
            assertTrue(cell, mapped.issues.none { (it as SetlistIssue.RowIssues).dropped })
        }
    }

    private fun Jam.available(): Setlist.Available = setlist as Setlist.Available

    private fun jam(
        date: String? = "2026-07-25",
        startTime: String? = "21:00",
        venue: String? = "La Macanuda",
        status: String? = "PUBLICADA",
        setlist: List<SetlistRowDto>? = listOf(row()),
        error: SetlistErrorDto? = null,
    ) = JamDto(date, startTime, venue, status, setlist, error)

    private fun row(
        position: String? = "1",
        songId: String? = "crossroads",
        title: String? = "Crossroads",
        artist: String? = "Eric Clapton",
        key: String? = "A",
        slots: SlotsDto = slots(null, null, null, null, null, null, null),
        extras: String? = null,
    ) = SetlistRowDto(position, songId, title, artist, key, slots, extras)

    private fun slots(vararg cells: String?) =
        SlotsDto(cells[0], cells[1], cells[2], cells[3], cells[4], cells[5], cells[6])

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 7, 25)
    }
}
