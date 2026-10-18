package com.bbbjam.feature.pastjams.detail

import com.bbbjam.core.data.Freshness
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
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.nav.BackUiModel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.state.EmptyStateUiModel
import com.bbbjam.core.ui.text.SongListText
import com.bbbjam.core.ui.text.SpanishDateNames
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.pastjams.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Shared fixtures of the detail tests. Every user-facing text is the `UiText` (resource id and
 * arguments) that the approved copy table of `docs/specs/past-jam-detail.md` (C1) maps to.
 */
internal object PastJamDetailFixtures {
    val julyDate: LocalDate = LocalDate.of(2026, 7, 25)

    val back = BackUiModel(UiText.res(CoreR.string.nav_back), EventHandler {})
    val loading = PastJamDetailUiModel.Loading(UiText.res(R.string.past_jams_detail_loading), back)
    val notFound = PastJamDetailUiModel.NotFound(
        EmptyStateUiModel(
            UiText.res(R.string.past_jams_not_found_title),
            UiText.res(R.string.past_jams_not_found_message),
        ),
        back,
    )

    private val fetched = Freshness(Instant.parse("2026-10-05T14:00:00Z"), lastFailure = null, isRefreshing = false)

    /** Filled and open slots plus an extra: none of it may reach the detail. */
    val busyLineup = Lineup(
        listOf(
            Slot(Instrument.GUITAR, "Tincho"),
            Slot(Instrument.GUITAR),
            Slot(Instrument.BASS, "Nico"),
            Slot(Instrument.DRUMS),
            Slot(Instrument.VOCALS, "Laura"),
            Slot(Instrument.HARMONICA, "Mono"),
        ),
    )

    fun song(
        position: Int,
        title: String = "Song $position",
        artist: String = "Artist $position",
        key: String = "A",
        lineup: Lineup = Lineup.default(),
        extras: List<ExtraParticipant> = emptyList(),
    ) = JamSong(position, SongId("song-$position"), title, artist, Key(key), lineup, extras)

    fun jam(
        date: LocalDate = julyDate,
        setlist: Setlist = Setlist.Available(listOf(song(1))),
        status: JamStatus = JamStatus.PUBLISHED,
        venue: String = "La Macanuda",
    ) = Jam(date, LocalTime.of(21, 0), venue, status, setlist)

    fun snapshot(past: List<Jam>, upcoming: Jam? = null) = JamsSnapshot(upcoming, past, fetched)

    /** The header of the July jam; [songs] is the count it shows, null when there is none. */
    fun header(songs: Int? = 1) = PastJamHeaderUiModel(
        UiText.res(R.string.past_jams_date_with_year, SpanishDateNames.label(julyDate), julyDate.year),
        "La Macanuda",
        songs?.let { UiText.plural(R.plurals.past_jams_song_count, it) },
    )

    fun row(position: Int, label: String, key: String = "A") = PastSongRowUiModel(
        position = position,
        positionLabel = label,
        title = "Song $position",
        artist = "Artist $position",
        key = key,
        keyDescription = SongListText.keyDescription(key),
    )
}
