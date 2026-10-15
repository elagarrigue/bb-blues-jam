package com.bbbjam.feature.pastjams

import com.bbbjam.core.data.admin.WriteOutcome

/*
 * The failure cards' messages on the Jams tab: the status change, the publication and (`admin-jams-tab`
 * Part B) the new jam. The wording is the one these cards had on Próxima jam.
 */

/** The failure card's message by outcome and code. */
internal fun failureMessage(reason: WriteOutcome): String = when (reason) {
    WriteOutcome.AccessRefused -> PastJamsCopy.ACCESS_REFUSED

    WriteOutcome.Offline -> PastJamsCopy.OFFLINE

    // Done never reaches a failure; the generic server line is the safe fallback.
    WriteOutcome.Unavailable, WriteOutcome.Done -> PastJamsCopy.UNAVAILABLE

    is WriteOutcome.Rejected -> when (reason.code) {
        "unknown_jam", "jam_not_editable", "duplicate_date" -> PastJamsCopy.JAM_CHANGED
        else -> PastJamsCopy.SHEET_REFUSED
    }
}

/** The failed edit or deletion reason: the edit codes first, then the shared outcomes. */
internal fun editFailureMessage(reason: WriteOutcome): String {
    val code = (reason as? WriteOutcome.Rejected)?.code.orEmpty()
    return when {
        code == "jam_exists" || code == "tab_exists" -> PastJamsCopy.CREATE_EXISTS
        code == "past_date" -> PastJamsCopy.DATE_PAST_ERROR
        code == "setlist_changed" -> JamEditCopy.SETLIST_CHANGED
        code.startsWith("invalid_") -> PastJamsCopy.CREATE_INVALID
        else -> failureMessage(reason)
    }
}

/** Publication warns that a timeout can follow a successful server write, so retry remains safe. */
internal fun publishFailureMessage(reason: WriteOutcome): String = when (reason) {
    WriteOutcome.Offline -> PastJamsCopy.PUBLISH_OFFLINE

    is WriteOutcome.Rejected -> if (reason.code == "empty_setlist") {
        PastJamsCopy.EMPTY_PUBLISH
    } else {
        failureMessage(reason)
    }

    else -> failureMessage(reason)
}
