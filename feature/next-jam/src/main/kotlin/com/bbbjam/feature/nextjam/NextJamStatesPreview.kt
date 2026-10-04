package com.bbbjam.feature.nextjam

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
 * One preview per state of Próxima jam (`list-states`): loading, the two empty cases, the two
 * errors, and cached data with the staleness notice, idle and while refreshing. Each model is built
 * by the presenter's own mapping from a snapshot, so a preview shows what the device would draw.
 */
@Composable
private fun StatePreview(snapshot: JamsSnapshot?) {
    val model = snapshot?.toUiModel(PREVIEW_TODAY, now = PREVIEW_NOW) ?: NextJamUiModel.Loading(NextJamCopy.LOADING)
    BluesJamTheme { NextJamContent(model = model, modifier = Modifier, contentPadding = PaddingValues()) }
}

/** 2 October 2026, 12:00 in Buenos Aires. */
private val PREVIEW_NOW: Instant = Instant.parse("2026-10-02T15:00:00Z")
private val PREVIEW_TODAY: LocalDate = LocalDate.of(2026, 10, 2)
private val THREE_HOURS_AGO: Instant = PREVIEW_NOW.minus(Duration.ofHours(3))

private fun previewSong(position: Int, title: String, key: String) = JamSong(
    position = position,
    songId = SongId(title.lowercase().replace(' ', '-')),
    title = title,
    artist = "B.B. King",
    key = Key(key),
    lineup = Lineup.default(),
)

private fun previewJam(songs: List<JamSong>) = Jam(
    date = LocalDate.parse("2026-10-31"),
    startTime = LocalTime.parse("21:00"),
    venue = "La Macanuda",
    status = JamStatus.PUBLISHED,
    setlist = Setlist.Available(songs),
)

private val previewSongs = listOf(
    previewSong(1, "Sweet Little Angel", "B"),
    previewSong(2, "Walking Thru the Park", "A"),
    previewSong(3, "The Thrill Is Gone", "Bm"),
)

private fun snapshot(upcoming: Jam?, freshness: Freshness) = JamsSnapshot(upcoming, emptyList(), freshness)

private val fresh = Freshness(THREE_HOURS_AGO, lastFailure = null, isRefreshing = false)

@Preview
@Composable
private fun LoadingPreview() = StatePreview(snapshot = null)

@Preview
@Composable
private fun EmptyNoUpcomingJamPreview() = StatePreview(snapshot(upcoming = null, fresh))

@Preview
@Composable
private fun EmptySetlistPreview() = StatePreview(snapshot(previewJam(emptyList()), fresh))

@Preview
@Composable
private fun ErrorOfflinePreview() =
    StatePreview(snapshot(upcoming = null, Freshness(null, DataFailure.Offline, isRefreshing = false)))

@Preview
@Composable
private fun ErrorOtherPreview() =
    StatePreview(snapshot(upcoming = null, Freshness(null, DataFailure.Service("internal"), isRefreshing = false)))

@Preview
@Composable
private fun OfflineWithDataPreview() = StatePreview(
    snapshot(previewJam(previewSongs), Freshness(THREE_HOURS_AGO, DataFailure.Offline, isRefreshing = false)),
)

@Preview
@Composable
private fun OfflineRefreshingPreview() = StatePreview(
    snapshot(previewJam(previewSongs), Freshness(THREE_HOURS_AGO, DataFailure.Offline, isRefreshing = true)),
)
