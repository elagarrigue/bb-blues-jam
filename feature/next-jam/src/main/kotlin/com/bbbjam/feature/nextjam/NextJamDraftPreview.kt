package com.bbbjam.feature.nextjam

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.model.JamStatus

/**
 * A draft upcoming jam (`unpublished-setlist-state`), fresh and offline. The draft holds songs on
 * purpose: the presenter's own mapping must turn it into the draft card, with no row.
 */
@Preview
@Composable
private fun DraftSetlistPreview() =
    StatePreview(previewSnapshot(previewJam(previewSongs, JamStatus.DRAFT), previewFresh))

@Preview
@Composable
private fun DraftSetlistOfflinePreview() = StatePreview(
    previewSnapshot(
        previewJam(previewSongs, JamStatus.DRAFT),
        Freshness(THREE_HOURS_AGO, DataFailure.Offline, isRefreshing = false),
    ),
)
