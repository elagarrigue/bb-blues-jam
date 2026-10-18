package com.bbbjam.feature.nextjam.songdetail

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
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
import com.bbbjam.core.ui.theme.BluesJamTheme
import java.time.LocalDate
import java.time.LocalTime

private val previewDate: LocalDate = LocalDate.of(2026, 10, 31)

/** The preview's model, built through the real mapping from a jam that holds [song]; [admin] for the admin's. */
private fun previewModel(song: JamSong, admin: SongAdminState? = null): SongDetailUiModel {
    val jam = Jam(previewDate, LocalTime.of(21, 0), "La Macanuda", JamStatus.PUBLISHED, Setlist.Available(listOf(song)))
    return JamsSnapshot(jam, emptyList(), Freshness(null, null, isRefreshing = false))
        .toSongDetail(previewDate, song.songId, song.position, onBack = {}, admin = admin)
}

@Composable
private fun DetailPreview(model: SongDetailUiModel) {
    BluesJamTheme { SongDetailContent(model = model, modifier = Modifier, contentPadding = PaddingValues()) }
}

/** Only the required fields: blank artist, no extras, the default lineup. */
@Preview
@Composable
private fun RequiredFieldsPreview() = DetailPreview(
    previewModel(
        JamSong(
            position = 1,
            songId = SongId("sweet-little-angel"),
            title = "Sweet Little Angel",
            artist = "",
            key = Key("B"),
            lineup = Lineup.default(),
        ),
    ),
)

private val fullSong = JamSong(
    position = 5,
    songId = SongId("the-thrill-is-gone"),
    title = "The Thrill Is Gone",
    artist = "B.B. King",
    key = Key("Bbm"),
    lineup = Lineup(
        listOf(
            Slot(Instrument.GUITAR, "Tincho"),
            Slot(Instrument.GUITAR),
            Slot(Instrument.BASS, "Nico"),
            Slot(Instrument.DRUMS),
            Slot(Instrument.VOCALS, "Laura"),
            Slot(Instrument.HARMONICA, "Mono"),
        ),
    ),
    extraParticipants = listOf(ExtraParticipant("Juan", "saxo")),
)

@Preview
@Composable
private fun FullSongPreview() = DetailPreview(previewModel(fullSong))

/** The admin's detail: the same song with "Cambiar tonalidad", the line actions and the editors. */
@Preview(heightDp = PREVIEW_TALL)
@Composable
private fun AdminSongPreview() =
    DetailPreview(previewModel(fullSong, SongAdminState(reservations = LineupReservations())))

private const val PREVIEW_TALL = 1400

@Preview
@Composable
private fun NotFoundPreview() = DetailPreview(
    JamsSnapshot(
        null,
        emptyList(),
        Freshness(null, null, isRefreshing = false),
    ).toSongDetail(previewDate, SongId("sweet-little-angel"), 1, onBack = {}),
)
