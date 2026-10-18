package com.bbbjam.feature.pastjams.admin

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.admin.WriteRejection
import com.bbbjam.core.ui.jamform.JamFormText
import com.bbbjam.core.ui.text.WriteFailureCopy
import com.bbbjam.feature.pastjams.PastJamsCopy
import com.bbbjam.feature.pastjams.edit.JamEditCopy

/*
 * The failure cards' messages on the Jams tab: the status change, the publication and (`admin-jams-tab`
 * Part B) the new jam. The wording is the one these cards had on Próxima jam.
 */

/** The failure card's message by outcome and code. */
internal fun failureMessage(reason: WriteOutcome): String = when (reason) {
    WriteOutcome.AccessRefused -> WriteFailureCopy.ACCESS_REFUSED

    WriteOutcome.Offline -> WriteFailureCopy.OFFLINE

    // Done never reaches a failure; the generic server line is the safe fallback.
    WriteOutcome.Unavailable, WriteOutcome.Done -> WriteFailureCopy.UNAVAILABLE

    is WriteOutcome.Rejected -> when (reason.rejection) {
        WriteRejection.UNKNOWN_JAM,
        WriteRejection.JAM_NOT_EDITABLE,
        WriteRejection.DUPLICATE_DATE,
        -> WriteFailureCopy.JAM_CHANGED

        else -> WriteFailureCopy.SHEET_REFUSED
    }
}

/** The failed edit or deletion reason: the edit codes first, then the shared outcomes. */
internal fun editFailureMessage(reason: WriteOutcome): String {
    val rejected = reason as? WriteOutcome.Rejected
    return when {
        rejected?.rejection == WriteRejection.JAM_EXISTS || rejected?.rejection == WriteRejection.TAB_EXISTS ->
            PastJamsCopy.CREATE_EXISTS

        rejected?.rejection == WriteRejection.PAST_DATE -> JamFormText.DATE_PAST_ERROR

        rejected?.rejection == WriteRejection.SETLIST_CHANGED -> JamEditCopy.SETLIST_CHANGED

        rejected?.isInvalidInput == true -> PastJamsCopy.CREATE_INVALID

        else -> failureMessage(reason)
    }
}

/** Publication warns that a timeout can follow a successful server write, so retry remains safe. */
internal fun publishFailureMessage(reason: WriteOutcome): String = when (reason) {
    WriteOutcome.Offline -> PastJamsCopy.PUBLISH_OFFLINE

    is WriteOutcome.Rejected -> if (reason.rejection == WriteRejection.EMPTY_SETLIST) {
        PastJamsCopy.EMPTY_PUBLISH
    } else {
        failureMessage(reason)
    }

    else -> failureMessage(reason)
}
