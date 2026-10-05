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
 * errors, and cached data with the staleness notice, idle and while refreshing. The draft jam's two
 * previews are in `NextJamDraftPreview.kt` and reuse the fixtures marked `internal` here. Each model is built
 * by the presenter's own mapping from a snapshot, so a preview shows what the device would draw.
 */
@Composable
internal fun StatePreview(snapshot: JamsSnapshot?) {
    val model = snapshot?.toUiModel(PREVIEW_TODAY, now = PREVIEW_NOW) ?: NextJamUiModel.Loading(NextJamCopy.LOADING)
    BluesJamTheme { NextJamContent(model = model, modifier = Modifier, contentPadding = PaddingValues()) }
}

/** 2 October 2026, 12:00 in Buenos Aires. */
private val PREVIEW_NOW: Instant = Instant.parse("2026-10-02T15:00:00Z")
private val PREVIEW_TODAY: LocalDate = LocalDate.of(2026, 10, 2)
internal val THREE_HOURS_AGO: Instant = PREVIEW_NOW.minus(Duration.ofHours(3))

private fun previewSong(position: Int, title: String, key: String) = JamSong(
    position = position,
    songId = SongId(title.lowercase().replace(' ', '-')),
    title = title,
    artist = "B.B. King",
    key = Key(key),
    lineup = Lineup.default(),
)

internal fun previewJam(songs: List<JamSong>, status: JamStatus = JamStatus.PUBLISHED) = Jam(
    date = LocalDate.parse("2026-10-31"),
    startTime = LocalTime.parse("21:00"),
    venue = "La Macanuda",
    status = status,
    setlist = Setlist.Available(songs),
)

internal val previewSongs = listOf(
    previewSong(1, "Sweet Little Angel", "B"),
    previewSong(2, "Walking Thru the Park", "A"),
    previewSong(3, "The Thrill Is Gone", "Bm"),
)

internal fun previewSnapshot(upcoming: Jam?, freshness: Freshness) = JamsSnapshot(upcoming, emptyList(), freshness)

internal val previewFresh = Freshness(THREE_HOURS_AGO, lastFailure = null, isRefreshing = false)

@Preview
@Composable
private fun LoadingPreview() = StatePreview(snapshot = null)

@Preview
@Composable
private fun EmptyNoUpcomingJamPreview() = StatePreview(previewSnapshot(upcoming = null, previewFresh))

@Preview
@Composable
private fun EmptySetlistPreview() = StatePreview(previewSnapshot(previewJam(emptyList()), previewFresh))

@Preview
@Composable
private fun ErrorOfflinePreview() =
    StatePreview(previewSnapshot(upcoming = null, Freshness(null, DataFailure.Offline, isRefreshing = false)))

@Preview
@Composable
private fun ErrorOtherPreview() = StatePreview(
    previewSnapshot(upcoming = null, Freshness(null, DataFailure.Service("internal"), isRefreshing = false)),
)

@Preview
@Composable
private fun OfflineWithDataPreview() = StatePreview(
    previewSnapshot(previewJam(previewSongs), Freshness(THREE_HOURS_AGO, DataFailure.Offline, isRefreshing = false)),
)

@Preview
@Composable
private fun OfflineRefreshingPreview() = StatePreview(
    previewSnapshot(previewJam(previewSongs), Freshness(THREE_HOURS_AGO, DataFailure.Offline, isRefreshing = true)),
)
