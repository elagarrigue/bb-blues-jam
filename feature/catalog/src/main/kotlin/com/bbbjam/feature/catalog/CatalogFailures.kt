package com.bbbjam.feature.catalog

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.admin.WriteRejection
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.text.UiText

/*
 * The copy of a failed catalog write (`admin-catalog-tab`): the shared `write_failure_*` lines for the
 * outcomes every admin write has, and the catalog's own lines for the codes only it raises.
 */

/** The failure card's message for a write the server did not run or refused. */
internal fun failureReason(reason: WriteOutcome): UiText = when (reason) {
    WriteOutcome.AccessRefused -> UiText.res(CoreR.string.write_failure_access_refused)

    WriteOutcome.Offline -> UiText.res(CoreR.string.write_failure_offline)

    // Done never reaches a failure; the generic server line is the safe fallback.
    WriteOutcome.Unavailable, WriteOutcome.Done -> UiText.res(CoreR.string.write_failure_unavailable)

    is WriteOutcome.Rejected -> when (reason.rejection) {
        WriteRejection.SONG_EXISTS -> UiText.res(R.string.catalog_failure_song_exists)
        WriteRejection.SONG_IN_FUTURE_JAM -> UiText.res(R.string.catalog_failure_in_future_jam)
        WriteRejection.UNKNOWN_SONG -> UiText.res(CoreR.string.write_failure_not_in_catalog)
        WriteRejection.DUPLICATE_ID -> UiText.res(R.string.catalog_failure_duplicate_id)
        else -> UiText.res(CoreR.string.write_failure_sheet_refused)
    }
}

/** Sending the same request again can only help when the write did not get a verdict from the server. */
internal fun isRetryable(reason: WriteOutcome): Boolean = reason !is WriteOutcome.Rejected

/** The failure card's title: what could not be done. */
internal fun CatalogAction.failureTitle(): UiText = UiText.res(
    when (this) {
        is CatalogAction.Create -> R.string.catalog_create_failed
        is CatalogAction.Update -> R.string.catalog_update_failed
        is CatalogAction.Delete -> R.string.catalog_delete_failed
    },
)

/** The sending line while the write runs. */
internal fun CatalogAction.progressLine(): UiText = UiText.res(
    if (this is CatalogAction.Delete) R.string.catalog_deleting else R.string.catalog_saving,
)

/** The result line when the write worked. */
internal fun CatalogAction.doneLine(): UiText = UiText.res(
    when (this) {
        is CatalogAction.Create -> R.string.catalog_added
        is CatalogAction.Update -> R.string.catalog_saved
        is CatalogAction.Delete -> R.string.catalog_deleted
    },
)
