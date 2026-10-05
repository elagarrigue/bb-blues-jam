package com.bbbjam.feature.songdetail

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
import com.bbbjam.core.ui.lineup.InstrumentGroupUiModel
import com.bbbjam.core.ui.lineup.InstrumentGroupsUiModel
import com.bbbjam.core.ui.lineup.LineupLineUiModel
import com.bbbjam.core.ui.nav.BackUiModel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.state.EmptyStateUiModel
import com.bbbjam.core.ui.strip.InstrumentChipKind
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Shared test data. Every user-facing string is written out from the approved copy tables
 * (`docs/specs/song-detail-screen.md` C1 and the `:core:ui` components' specs), never read from
 * [SongDetailCopy], so a change to the shipped copy fails a test.
 */
internal object SongDetailFixtures {
    val upcomingDate: LocalDate = LocalDate.of(2026, 10, 31)
    val pastDate: LocalDate = LocalDate.of(2026, 7, 25)

    val fetched = Freshness(Instant.parse("2026-10-02T14:59:00Z"), lastFailure = null, isRefreshing = false)

    /** Handlers without a key compare equal, so this matches any back model with the label. */
    val back = BackUiModel("Volver", EventHandler {})

    val notFound = SongDetailUiModel.NotFound(
        EmptyStateUiModel(
            "Este tema ya no está en la lista",
            "Puede que la organización haya cambiado la lista. Volvé a la próxima jam para ver la actual.",
        ),
        back,
    )

    val loading = SongDetailUiModel.Loading("Cargando el tema", back)

    val mixedLineup = Lineup(
        listOf(
            Slot(Instrument.GUITAR, "Tincho"),
            Slot(Instrument.GUITAR),
            Slot(Instrument.BASS, "Nico"),
        ),
    )

    fun song(
        position: Int,
        title: String = "The Thrill Is Gone",
        artist: String = "B.B. King",
        key: String = "Bm",
        lineup: Lineup = mixedLineup,
        extras: List<ExtraParticipant> = listOf(ExtraParticipant("Juan", "saxo")),
    ) = JamSong(position, SongId("song-$position"), title, artist, Key(key), lineup, extras)

    fun jam(date: LocalDate, setlist: Setlist, status: JamStatus = JamStatus.PUBLISHED) =
        Jam(date, LocalTime.of(21, 0), "La Macanuda", status, setlist)

    fun snapshot(upcoming: Jam?, past: List<Jam> = emptyList(), freshness: Freshness = fetched) =
        JamsSnapshot(upcoming, past, freshness)

    /** What `JamsRepository.observeJams` emits after a failed cache read: no jams, a storage failure. */
    val failedRead = JamsSnapshot(null, emptyList(), Freshness(null, DataFailure.Storage("SQLiteException"), false))

    /** The grouped [mixedLineup] with the saxo extra, written out. */
    val mixedGroups = InstrumentGroupsUiModel(
        groups = listOf(
            InstrumentGroupUiModel(
                "Guitarra",
                listOf(
                    LineupLineUiModel("Guitarra", "LIBRE", "Guitarra: libre", InstrumentChipKind.OPEN_SLOT),
                    LineupLineUiModel("Guitarra", "Tincho", "Guitarra: Tincho", InstrumentChipKind.FILLED_SLOT),
                ),
            ),
            InstrumentGroupUiModel(
                "Bajo",
                listOf(LineupLineUiModel("Bajo", "Nico", "Bajo: Nico", InstrumentChipKind.FILLED_SLOT)),
            ),
        ),
        extras = listOf(LineupLineUiModel("+ saxo", "Juan", "Otros: saxo, Juan", InstrumentChipKind.EXTRA)),
        noOpenSlotsNote = null,
        hint = "Para tocar, anotate en la jam: la organización te suma a un tema.",
    )

    fun songModel(title: String = "The Thrill Is Gone", artist: String? = "B.B. King", key: String = "Bm") =
        SongDetailUiModel.Song(
            title = title,
            artist = artist,
            keyLabel = "Tonalidad",
            key = key,
            keyDescription = "Tonalidad $key",
            lineup = mixedGroups,
            back = back,
        )
}
