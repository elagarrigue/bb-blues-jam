package com.bbbjam.core.assistant

import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.catalog.CatalogRepository
import com.bbbjam.core.data.catalog.CatalogSnapshot
import com.bbbjam.core.data.catalog.RefreshOutcome
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRefreshOutcome
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.Slot
import com.bbbjam.core.model.Song
import com.bbbjam.core.model.SongId
import com.bbbjam.core.model.Tempo
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

private val FRESH = Freshness(fetchedAt = null, lastFailure = null, isRefreshing = false)

/** A calendar fixed on 2026-10-14 (Buenos Aires). */
internal fun fixedCalendar(): JamCalendar =
    JamCalendar(Clock.fixed(Instant.parse("2026-10-14T15:00:00Z"), ZoneOffset.UTC), JamCalendar.BUENOS_AIRES)

/** A catalog song; the slug is the title with hyphens unless given. */
internal fun song(id: String, title: String, artist: String, key: String = "A", tempo: Tempo? = null): Song =
    Song(SongId(id), title, artist, Key(key), tempo)

/** A read-only catalog fake that counts how often it is subscribed to. */
internal class FakeCatalog(private val songs: List<Song>) : CatalogRepository {
    var subscriptions = 0
        private set

    override fun observeCatalog(): Flow<CatalogSnapshot> {
        subscriptions++
        return flowOf(CatalogSnapshot(songs, FRESH))
    }

    override suspend fun refresh(): RefreshOutcome = RefreshOutcome.Failed(DataFailure.Offline)
}

/** A read-only jams fake holding an upcoming jam and past jams (newest first). */
internal class FakePastJams(private val past: List<Jam> = emptyList(), private val upcoming: Jam? = null) :
    JamsRepository {
    override fun observeJams(): Flow<JamsSnapshot> = flowOf(JamsSnapshot(upcoming, past, FRESH))

    override suspend fun refresh(): JamsRefreshOutcome = JamsRefreshOutcome.Failed(DataFailure.Offline)
}

/** A song of a jam; [players] fill the slots in order of [Lineup.default]'s instruments given by name. */
internal fun jamSong(
    position: Int,
    id: String,
    title: String,
    artist: String,
    key: String = "A",
    players: Map<Instrument, String> = emptyMap(),
    extras: List<ExtraParticipant> = emptyList(),
): JamSong {
    val seen = HashSet<Instrument>()
    val slots = Lineup.DEFAULT_INSTRUMENTS.map { instrument ->
        val first = seen.add(instrument)
        Slot(instrument, if (first) players[instrument] else null)
    }
    return JamSong(position, SongId(id), title, artist, Key(key), Lineup(slots), extras)
}

internal fun jam(date: String, songs: List<JamSong>, status: JamStatus = JamStatus.PUBLISHED): Jam =
    Jam(LocalDate.parse(date), LocalTime.of(20, 0), "Bar", status, Setlist.Available(songs))
