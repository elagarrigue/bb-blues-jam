package com.bbbjam.feature.pastjams

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.theme.BluesJamTheme
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * One preview per state of Anteriores: loading, the offline error, empty, the list (with a past
 * draft) and the list offline with the staleness notice. Each model is built by the presenter's own
 * mapping from a snapshot, so a preview shows what the device would draw.
 */
@Composable
private fun StatePreview(snapshot: JamsSnapshot?) {
    val model = snapshot?.toUiModel(now = PREVIEW_NOW)
        ?: PastJamsUiModel.Loading(PastJamsCopy.TITLE, PastJamsCopy.LOADING)
    BluesJamTheme { PastJamsContent(model = model, modifier = Modifier, contentPadding = PaddingValues()) }
}

/** 5 October 2026, 12:00 in Buenos Aires. */
private val PREVIEW_NOW: Instant = Instant.parse("2026-10-05T15:00:00Z")
private val THREE_HOURS_AGO: Instant = PREVIEW_NOW.minus(Duration.ofHours(3))

private val previewTitles = listOf(
    "Sweet Home Chicago",
    "The Thrill Is Gone",
    "Pride and Joy",
    "Crossroads",
    "Got My Mojo Working",
)

private fun previewSongs(count: Int) = previewTitles.take(count).mapIndexed { index, title ->
    JamSong(
        position = index + 1,
        songId = SongId(title.lowercase().replace(' ', '-')),
        title = title,
        artist = "B.B. King",
        key = Key("A"),
        lineup = Lineup.default(),
    )
}

private fun previewJam(date: String, status: JamStatus, setlist: Setlist) = Jam(
    date = LocalDate.parse(date),
    startTime = LocalTime.parse("21:00"),
    venue = "La Macanuda",
    status = status,
    setlist = setlist,
)

private val previewPast = listOf(
    previewJam("2026-09-26", JamStatus.PUBLISHED, Setlist.Available(previewSongs(5))),
    previewJam("2026-08-29", JamStatus.DRAFT, Setlist.Withheld),
    previewJam("2026-07-25", JamStatus.PUBLISHED, Setlist.Available(previewSongs(1))),
)

private val fresh = Freshness(THREE_HOURS_AGO, lastFailure = null, isRefreshing = false)

private fun snapshot(past: List<Jam>, freshness: Freshness) = JamsSnapshot(upcoming = null, past, freshness)

@Preview
@Composable
private fun LoadingPreview() = StatePreview(snapshot = null)

@Preview
@Composable
private fun ErrorOfflinePreview() =
    StatePreview(snapshot(emptyList(), Freshness(null, DataFailure.Offline, isRefreshing = false)))

@Preview
@Composable
private fun EmptyPreview() = StatePreview(snapshot(emptyList(), fresh))

@Preview
@Composable
private fun JamsPreview() = StatePreview(snapshot(previewPast, fresh))

@Preview
@Composable
private fun OfflineWithRowsPreview() =
    StatePreview(snapshot(previewPast, Freshness(THREE_HOURS_AGO, DataFailure.Offline, isRefreshing = false)))
