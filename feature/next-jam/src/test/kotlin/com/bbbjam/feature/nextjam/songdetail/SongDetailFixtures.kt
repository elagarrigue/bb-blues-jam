package com.bbbjam.feature.nextjam.songdetail

import com.bbbjam.core.data.DataFailure
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
import com.bbbjam.core.ui.lineup.LineupLineUiModel
import com.bbbjam.core.ui.lineup.LineupPanelUiModel
import com.bbbjam.core.ui.nav.BackUiModel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.state.EmptyStateUiModel
import com.bbbjam.core.ui.strip.InstrumentChipKind
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.core.ui.text.fullName
import com.bbbjam.feature.nextjam.R
import com.bbbjam.feature.nextjam.keyDescription
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Shared test data. Every user-facing text is a `UiText` written out with its resource id and
 * arguments (`docs/specs/song-detail-screen.md` C1 and the `:core:ui` components' specs), never built
 * by the code under test, so a change of id or of arguments fails a test.
 */
internal object SongDetailFixtures {
    val upcomingDate: LocalDate = LocalDate.of(2026, 10, 31)
    val pastDate: LocalDate = LocalDate.of(2026, 7, 25)

    val fetched = Freshness(Instant.parse("2026-10-02T14:59:00Z"), lastFailure = null, isRefreshing = false)

    /** Handlers without a key compare equal, so this matches any back model with the label. */
    val back = BackUiModel(UiText.res(CoreR.string.nav_back), EventHandler {})

    val notFound = SongDetailUiModel.NotFound(
        EmptyStateUiModel(
            UiText.res(R.string.song_detail_not_found_title),
            UiText.res(R.string.song_detail_not_found_message),
        ),
        back,
    )

    val loading = SongDetailUiModel.Loading(UiText.res(R.string.song_detail_loading), back)

    val mixedLineup = Lineup(
        listOf(
            Slot(Instrument.GUITAR, "Tincho"),
            Slot(Instrument.GUITAR),
            Slot(Instrument.BASS, "Nico"),
        ),
    )

    /** The id [song] gives the song at [position]. */
    fun id(position: Int) = SongId("song-$position")

    fun song(
        position: Int,
        title: String = "The Thrill Is Gone",
        artist: String = "B.B. King",
        key: String = "Bm",
        lineup: Lineup = mixedLineup,
        extras: List<ExtraParticipant> = listOf(ExtraParticipant("Juan", "saxo")),
    ) = JamSong(position, id(position), title, artist, Key(key), lineup, extras)

    fun jam(date: LocalDate, setlist: Setlist, status: JamStatus = JamStatus.PUBLISHED) =
        Jam(date, LocalTime.of(21, 0), "La Macanuda", status, setlist)

    fun snapshot(upcoming: Jam?, past: List<Jam> = emptyList(), freshness: Freshness = fetched) =
        JamsSnapshot(upcoming, past, freshness)

    /** What `JamsRepository.observeJams` emits after a failed cache read: no jams, a storage failure. */
    val failedRead = JamsSnapshot(null, emptyList(), Freshness(null, DataFailure.Storage("SQLiteException"), false))

    /** The panel of [mixedLineup] with the saxo extra, written out: open first, then filled, then Otros. */
    val mixedPanel = LineupPanelUiModel(
        openSlots = listOf(
            LineupLineUiModel(
                Instrument.GUITAR.fullName(),
                UiText.res(CoreR.string.lineup_open_detail),
                UiText.res(CoreR.string.strip_open_description, Instrument.GUITAR.fullName()),
                InstrumentChipKind.OPEN_SLOT,
            ),
        ),
        filledSlots = listOf(
            LineupLineUiModel(
                Instrument.GUITAR.fullName(),
                UiText.raw("Tincho"),
                UiText.res(CoreR.string.strip_filled_description, Instrument.GUITAR.fullName(), "Tincho"),
                InstrumentChipKind.FILLED_SLOT,
            ),
            LineupLineUiModel(
                Instrument.BASS.fullName(),
                UiText.raw("Nico"),
                UiText.res(CoreR.string.strip_filled_description, Instrument.BASS.fullName(), "Nico"),
                InstrumentChipKind.FILLED_SLOT,
            ),
        ),
        extras = listOf(
            LineupLineUiModel(
                UiText.res(CoreR.string.lineup_extra_instrument, "saxo"),
                UiText.raw("Juan"),
                UiText.res(CoreR.string.strip_extra_description, "saxo", "Juan"),
                InstrumentChipKind.EXTRA,
            ),
        ),
        noOpenSlotsNote = null,
        hint = UiText.res(CoreR.string.lineup_hint),
    )

    fun songModel(title: String = "The Thrill Is Gone", artist: String? = "B.B. King", key: String = "Bm") =
        SongDetailUiModel.Song(
            title = title,
            artist = artist,
            keyLabel = UiText.res(R.string.song_detail_key_label),
            key = key,
            keyDescription = keyDescription(key),
            lineup = mixedPanel,
            back = back,
        )
}
