package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.admin.WriteOutcome

/*
 * The failure cards' messages on Próxima jam (C1 of `admin-add-song-to-setlist`, extended by
 * `admin-remove-song-from-setlist` and `admin-set-key`).
 */

/** The failure card's message by outcome and code (C1). */
internal fun failureMessage(reason: WriteOutcome): String = when (reason) {
    WriteOutcome.AccessRefused -> NextJamCopy.ACCESS_REFUSED

    WriteOutcome.Offline -> NextJamCopy.OFFLINE

    // Done never reaches a failure; the generic server line is the safe fallback.
    WriteOutcome.Unavailable, WriteOutcome.Done -> NextJamCopy.UNAVAILABLE

    is WriteOutcome.Rejected -> when (reason.code) {
        "song_already_in_setlist" -> NextJamCopy.ALREADY_LISTED
        "song_not_in_setlist" -> NextJamCopy.NOT_IN_SETLIST
        "duplicate_song" -> NextJamCopy.DUPLICATE_SONG
        "unknown_song" -> NextJamCopy.NOT_IN_CATALOG
        "unknown_jam", "jam_not_editable", "duplicate_date" -> NextJamCopy.JAM_CHANGED
        else -> NextJamCopy.SHEET_REFUSED
    }
}

/**
 * A failed key change's card message. The same outcomes as the other cards, except the two codes
 * whose remove-song wording would be wrong for a key change: a song that is no longer listed means
 * the jam changed in the Sheet (`song_not_in_setlist`), and a repeated song is the shared
 * duplicate line.
 */
internal fun keyFailureMessage(reason: WriteOutcome): String = when {
    reason is WriteOutcome.Rejected && reason.code == "song_not_in_setlist" -> NextJamCopy.JAM_CHANGED
    reason is WriteOutcome.Rejected && reason.code == "duplicate_song" -> NextJamCopy.DUPLICATE_SONG
    else -> failureMessage(reason)
}

internal fun lineupFailureMessage(reason: WriteOutcome): String = when {
    reason is WriteOutcome.Rejected && reason.code == "slot_filled" -> NextJamCopy.SLOT_FILLED
    else -> keyFailureMessage(reason)
}

internal fun assignmentFailureMessage(reason: WriteOutcome): String = when {
    reason is WriteOutcome.Rejected && reason.code == "invalid_name" -> NextJamCopy.INVALID_ASSIGNMENT_NAME
    reason is WriteOutcome.Rejected && reason.code == "slot_taken" -> NextJamCopy.SLOT_TAKEN
    reason is WriteOutcome.Rejected && reason.code == "slot_not_in_lineup" -> NextJamCopy.JAM_CHANGED
    reason is WriteOutcome.Rejected && reason.code == "song_not_in_setlist" -> NextJamCopy.JAM_CHANGED
    else -> failureMessage(reason)
}

internal fun slotClearFailureMessage(reason: WriteOutcome): String = when {
    reason is WriteOutcome.Rejected && reason.code in setOf("slot_empty", "slot_changed") -> NextJamCopy.CLEAR_STALE

    reason is WriteOutcome.Rejected && reason.code in setOf(
        "slot_not_in_lineup",
        "song_not_in_setlist",
    ) -> NextJamCopy.JAM_CHANGED

    else -> failureMessage(reason)
}

internal fun moveFailureMessage(reason: WriteOutcome): String = when {
    reason is WriteOutcome.Rejected && reason.code == "song_not_in_setlist" -> NextJamCopy.JAM_CHANGED
    reason is WriteOutcome.Rejected && reason.code == "duplicate_song" -> NextJamCopy.DUPLICATE_SONG
    reason is WriteOutcome.Rejected && reason.code == "unordered_setlist" -> NextJamCopy.UNORDERED_SETLIST
    else -> failureMessage(reason)
}

/** Publication warns that a timeout can follow a successful server write, so retry remains safe. */
internal fun publishFailureMessage(reason: WriteOutcome): String = when (reason) {
    WriteOutcome.Offline -> NextJamCopy.PUBLISH_OFFLINE

    is WriteOutcome.Rejected -> if (reason.code ==
        "empty_setlist"
    ) {
        NextJamCopy.EMPTY_PUBLISH
    } else {
        failureMessage(reason)
    }

    else -> failureMessage(reason)
}
