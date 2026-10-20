package com.bbbjam.feature.catalog

import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.catalog.CatalogSnapshot
import com.bbbjam.core.data.catalog.edit.CatalogEditRepository
import com.bbbjam.core.data.catalog.edit.CreateSongOutcome
import com.bbbjam.core.data.catalog.edit.DeleteSongOutcome
import com.bbbjam.core.data.catalog.edit.SongFields
import com.bbbjam.core.data.catalog.edit.UpdateSongOutcome
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.Song
import com.bbbjam.core.model.SongId
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CompletableDeferred

internal val FETCHED = Freshness(Instant.parse("2026-10-02T14:59:00Z"), lastFailure = null, isRefreshing = false)

internal val CROSSROADS = Song(SongId("crossroads"), "Crossroads", "Eric Clapton", Key("A"))
internal val THRILL = Song(SongId("the-thrill-is-gone"), "The Thrill Is Gone", "B.B. King", Key("Bm"))
internal val AGUA = Song(SongId("agua-de-rio"), "Ágüa de río", "Pappo", Key("E"))
internal val RED_HOUSE = Song(SongId("red-house"), "Red House", "Jimi Hendrix", Key("Bb"))
internal val SONGS = listOf(THRILL, AGUA, CROSSROADS, RED_HOUSE)

internal fun catalogSnapshot(songs: List<Song> = SONGS, freshness: Freshness = FETCHED) =
    CatalogSnapshot(songs, freshness)

internal fun jamWith(date: LocalDate, setlist: Setlist, status: JamStatus = JamStatus.DRAFT) =
    Jam(date, LocalTime.of(21, 0), "La Macanuda", status, setlist)

internal fun setlistOf(vararg songs: Song): Setlist.Available = Setlist.Available(
    songs.mapIndexed { index, song ->
        JamSong(index + 1, song.id, song.title, song.artist, song.defaultKey, Lineup.default())
    },
)

internal fun jamsSnapshot(upcoming: Jam? = null, heldBack: List<Jam> = emptyList(), past: List<Jam> = emptyList()) =
    JamsSnapshot(upcoming, past, FETCHED, heldBack)
