package com.bbbjam.feature.pastjams

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.nav.backUiModel
import com.bbbjam.core.ui.theme.BluesJamTheme
import java.time.LocalDate
import java.time.LocalTime

/**
 * One preview per state of a past jam's detail: loading, not found, the songs (with a blank artist
 * and a dropped-rows note) and a past draft. Each model but loading is built by the presenter's own
 * mapping from a snapshot, so a preview shows what the device would draw.
 */
@Composable
private fun DetailPreview(model: PastJamDetailUiModel) {
    BluesJamTheme { PastJamDetailContent(model = model, modifier = Modifier, contentPadding = PaddingValues()) }
}

private val previewDate: LocalDate = LocalDate.of(2026, 7, 25)

private val previewSongs = listOf(
    "Sweet Home Chicago" to "Robert Johnson",
    "The Thrill Is Gone" to "B.B. King",
    "Pride and Joy" to "Stevie Ray Vaughan",
    "Blues de Rosario" to "",
).mapIndexed { index, (title, artist) ->
    JamSong(
        position = index + 1,
        songId = SongId(title.lowercase().replace(' ', '-')),
        title = title,
        artist = artist,
        key = Key(listOf("E", "Bm", "E", "A")[index]),
        lineup = Lineup.default(),
    )
}

private fun previewSnapshot(status: JamStatus, setlist: Setlist) = JamsSnapshot(
    upcoming = null,
    past = listOf(Jam(previewDate, LocalTime.parse("21:00"), "La Macanuda", status, setlist)),
    freshness = Freshness(null, null, isRefreshing = false),
)

@Preview
@Composable
private fun LoadingPreview() = DetailPreview(PastJamDetailUiModel.Loading(PastJamsCopy.DETAIL_LOADING, backUiModel {}))

@Preview
@Composable
private fun NotFoundPreview() = DetailPreview(
    JamsSnapshot(null, emptyList(), Freshness(null, null, isRefreshing = false)).toPastJamDetail(previewDate),
)

@Preview
@Composable
private fun JamPreview() = DetailPreview(
    previewSnapshot(JamStatus.PUBLISHED, Setlist.Available(previewSongs, droppedRows = 2)).toPastJamDetail(previewDate),
)

@Preview
@Composable
private fun WithheldPreview() =
    DetailPreview(previewSnapshot(JamStatus.DRAFT, Setlist.Withheld).toPastJamDetail(previewDate))
