package com.bbbjam.feature.nextjam.admin

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.admin.WriteRejection
import com.bbbjam.core.ui.text.WriteFailureCopy
import com.bbbjam.feature.nextjam.NextJamCopy

/*
 * The failure cards' messages on Próxima jam (C1 of `admin-add-song-to-setlist`, extended by
 * `admin-remove-song-from-setlist` and `admin-set-key`).
 */

/** The failure card's message by outcome and code (C1). */
internal fun failureMessage(reason: WriteOutcome): String = when (reason) {
    WriteOutcome.AccessRefused -> WriteFailureCopy.ACCESS_REFUSED

    WriteOutcome.Offline -> WriteFailureCopy.OFFLINE

    // Done never reaches a failure; the generic server line is the safe fallback.
    WriteOutcome.Unavailable, WriteOutcome.Done -> WriteFailureCopy.UNAVAILABLE

    is WriteOutcome.Rejected -> when (reason.rejection) {
        WriteRejection.SONG_ALREADY_IN_SETLIST -> NextJamCopy.ALREADY_LISTED

        WriteRejection.SONG_NOT_IN_SETLIST -> NextJamCopy.NOT_IN_SETLIST

        WriteRejection.DUPLICATE_SONG -> NextJamCopy.DUPLICATE_SONG

        WriteRejection.UNKNOWN_SONG -> WriteFailureCopy.NOT_IN_CATALOG

        WriteRejection.UNKNOWN_JAM,
        WriteRejection.JAM_NOT_EDITABLE,
        WriteRejection.DUPLICATE_DATE,
        -> WriteFailureCopy.JAM_CHANGED

        else -> WriteFailureCopy.SHEET_REFUSED
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
internal fun keyFailureMessage(reason: WriteOutcome): String = when (reason.rejectionOrNull()) {
    WriteRejection.SONG_NOT_IN_SETLIST -> WriteFailureCopy.JAM_CHANGED
    WriteRejection.DUPLICATE_SONG -> NextJamCopy.DUPLICATE_SONG
    else -> failureMessage(reason)
}

internal fun lineupFailureMessage(reason: WriteOutcome): String = when (reason.rejectionOrNull()) {
    WriteRejection.SLOT_FILLED -> NextJamCopy.SLOT_FILLED
    WriteRejection.INSTRUMENT_NOT_EXPANDABLE -> NextJamCopy.NOT_EXPANDABLE
    else -> keyFailureMessage(reason)
}

internal fun assignmentFailureMessage(reason: WriteOutcome): String = when (reason.rejectionOrNull()) {
    WriteRejection.INVALID_NAME -> NextJamCopy.INVALID_ASSIGNMENT_NAME
    WriteRejection.SLOT_TAKEN -> NextJamCopy.SLOT_TAKEN
    WriteRejection.SLOT_NOT_IN_LINEUP, WriteRejection.SONG_NOT_IN_SETLIST -> WriteFailureCopy.JAM_CHANGED
    else -> failureMessage(reason)
}

internal fun slotClearFailureMessage(reason: WriteOutcome): String = when (reason.rejectionOrNull()) {
    WriteRejection.SLOT_EMPTY, WriteRejection.SLOT_CHANGED -> NextJamCopy.CLEAR_STALE
    WriteRejection.SLOT_NOT_IN_LINEUP, WriteRejection.SONG_NOT_IN_SETLIST -> WriteFailureCopy.JAM_CHANGED
    else -> failureMessage(reason)
}

internal fun moveFailureMessage(reason: WriteOutcome): String = when (reason.rejectionOrNull()) {
    WriteRejection.SONG_NOT_IN_SETLIST -> WriteFailureCopy.JAM_CHANGED
    WriteRejection.DUPLICATE_SONG -> NextJamCopy.DUPLICATE_SONG
    WriteRejection.UNORDERED_SETLIST -> NextJamCopy.UNORDERED_SETLIST
    else -> failureMessage(reason)
}
