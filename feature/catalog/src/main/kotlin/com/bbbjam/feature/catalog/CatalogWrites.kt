package com.bbbjam.feature.catalog

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.admin.WriteRejection
import com.bbbjam.core.data.catalog.edit.CatalogEditRepository
import com.bbbjam.core.data.catalog.edit.CreateSongOutcome
import com.bbbjam.core.data.catalog.edit.DeleteSongOutcome
import com.bbbjam.core.data.catalog.edit.UpdateSongOutcome
import com.bbbjam.core.ui.text.UiText

/**
 * Runs a [CatalogAction] through [CatalogEditRepository] (D-13: every mutation is a repository function) and
 * turns the outcome into the line or card the tab shows. Nothing here writes to the Sheet or to Room.
 */
internal class CatalogWrites(private val edits: CatalogEditRepository) {
    suspend fun perform(action: CatalogAction): CatalogResult = when (action) {
        is CatalogAction.Create -> when (val outcome = edits.createSong(action.fields)) {
            is CreateSongOutcome.Created -> CatalogResult.Done(action.doneLine())
            is CreateSongOutcome.NotCreated -> failed(action, outcome.reason)
            CreateSongOutcome.ScriptOutdated -> outdated(action)
        }

        is CatalogAction.Update -> when (val outcome = edits.updateSong(action.id, action.fields)) {
            is UpdateSongOutcome.Updated -> CatalogResult.Done(action.doneLine())
            is UpdateSongOutcome.NotUpdated -> failed(action, outcome.reason)
            UpdateSongOutcome.ScriptOutdated -> outdated(action)
        }

        is CatalogAction.Delete -> when (val outcome = edits.deleteSong(action.id)) {
            DeleteSongOutcome.Deleted -> CatalogResult.Done(action.doneLine())
            is DeleteSongOutcome.NotDeleted -> failed(action, outcome.reason)
            DeleteSongOutcome.ScriptOutdated -> outdated(action)
        }
    }

    private fun failed(action: CatalogAction, reason: WriteOutcome) = CatalogResult.Failed(
        action = action,
        title = action.failureTitle(),
        message = failureReason(reason),
        retryable = isRetryable(reason),
        // The jams the admin sees are older than the server's: a song a held-back jam still lists.
        staleJams = (reason as? WriteOutcome.Rejected)?.rejection == WriteRejection.SONG_IN_FUTURE_JAM,
    )

    private fun outdated(action: CatalogAction) = CatalogResult.Failed(
        action = action,
        title = action.failureTitle(),
        message = UiText.res(R.string.catalog_failure_script_outdated),
        retryable = false,
    )
}
