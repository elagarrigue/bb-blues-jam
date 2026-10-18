package com.bbbjam.feature.nextjam.admin

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.admin.WriteRejection
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.nextjam.R

/*
 * The failure cards' messages on Próxima jam (C1 of `admin-add-song-to-setlist`, extended by
 * `admin-remove-song-from-setlist` and `admin-set-key`).
 */

/** The failure card's message by outcome and code (C1). */
internal fun failureMessage(reason: WriteOutcome): UiText = when (reason) {
    WriteOutcome.AccessRefused -> UiText.res(CoreR.string.write_failure_access_refused)

    WriteOutcome.Offline -> UiText.res(CoreR.string.write_failure_offline)

    // Done never reaches a failure; the generic server line is the safe fallback.
    WriteOutcome.Unavailable, WriteOutcome.Done -> UiText.res(CoreR.string.write_failure_unavailable)

    is WriteOutcome.Rejected -> when (reason.rejection) {
        WriteRejection.SONG_ALREADY_IN_SETLIST -> UiText.res(R.string.next_jam_failure_already_listed)

        WriteRejection.SONG_NOT_IN_SETLIST -> UiText.res(R.string.next_jam_failure_not_in_setlist)

        WriteRejection.DUPLICATE_SONG -> UiText.res(R.string.next_jam_failure_duplicate_song)

        WriteRejection.UNKNOWN_SONG -> UiText.res(CoreR.string.write_failure_not_in_catalog)

        WriteRejection.UNKNOWN_JAM,
        WriteRejection.JAM_NOT_EDITABLE,
        WriteRejection.DUPLICATE_DATE,
        -> UiText.res(CoreR.string.write_failure_jam_changed)

        else -> UiText.res(CoreR.string.write_failure_sheet_refused)
    }
}

/** The typed rejection of a refused write, or null for every other outcome. */
private fun WriteOutcome.rejectionOrNull(): WriteRejection? = (this as? WriteOutcome.Rejected)?.rejection

/**
 * A failed key change's card message. The same outcomes as the other cards, except the two codes
 * whose remove-song wording would be wrong for a key change: a song that is no longer listed means
 * the jam changed in the Sheet (`song_not_in_setlist`), and a repeated song is the shared
 * duplicate line.
 */
internal fun keyFailureMessage(reason: WriteOutcome): UiText = when (reason.rejectionOrNull()) {
    WriteRejection.SONG_NOT_IN_SETLIST -> UiText.res(CoreR.string.write_failure_jam_changed)
    WriteRejection.DUPLICATE_SONG -> UiText.res(R.string.next_jam_failure_duplicate_song)
    else -> failureMessage(reason)
}

internal fun lineupFailureMessage(reason: WriteOutcome): UiText = when (reason.rejectionOrNull()) {
    WriteRejection.SLOT_FILLED -> UiText.res(R.string.next_jam_failure_slot_filled)
    WriteRejection.INSTRUMENT_NOT_EXPANDABLE -> UiText.res(R.string.next_jam_failure_not_expandable)
    else -> keyFailureMessage(reason)
}

internal fun assignmentFailureMessage(reason: WriteOutcome): UiText = when (reason.rejectionOrNull()) {
    WriteRejection.INVALID_NAME -> UiText.res(R.string.next_jam_failure_invalid_name)

    WriteRejection.SLOT_TAKEN -> UiText.res(R.string.next_jam_failure_slot_taken)

    WriteRejection.SLOT_NOT_IN_LINEUP, WriteRejection.SONG_NOT_IN_SETLIST -> UiText.res(
        CoreR.string.write_failure_jam_changed,
    )

    else -> failureMessage(reason)
}

internal fun slotClearFailureMessage(reason: WriteOutcome): UiText = when (reason.rejectionOrNull()) {
    WriteRejection.SLOT_EMPTY, WriteRejection.SLOT_CHANGED -> UiText.res(R.string.next_jam_failure_clear_stale)

    WriteRejection.SLOT_NOT_IN_LINEUP, WriteRejection.SONG_NOT_IN_SETLIST -> UiText.res(
        CoreR.string.write_failure_jam_changed,
    )

    else -> failureMessage(reason)
}

internal fun moveFailureMessage(reason: WriteOutcome): UiText = when (reason.rejectionOrNull()) {
    WriteRejection.SONG_NOT_IN_SETLIST -> UiText.res(CoreR.string.write_failure_jam_changed)
    WriteRejection.DUPLICATE_SONG -> UiText.res(R.string.next_jam_failure_duplicate_song)
    WriteRejection.UNORDERED_SETLIST -> UiText.res(R.string.next_jam_failure_unordered_setlist)
    else -> failureMessage(reason)
}
