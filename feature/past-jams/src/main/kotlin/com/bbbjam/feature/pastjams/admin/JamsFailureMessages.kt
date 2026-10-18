package com.bbbjam.feature.pastjams.admin

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.admin.WriteRejection
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.pastjams.R

/*
 * The failure cards' messages on the Jams tab: the status change, the publication and (`admin-jams-tab`
 * Part B) the new jam. The wording is the one these cards had on Próxima jam.
 */

/** The failure card's message by outcome and code. */
internal fun failureMessage(reason: WriteOutcome): UiText = when (reason) {
    WriteOutcome.AccessRefused -> UiText.res(CoreR.string.write_failure_access_refused)

    WriteOutcome.Offline -> UiText.res(CoreR.string.write_failure_offline)

    // Done never reaches a failure; the generic server line is the safe fallback.
    WriteOutcome.Unavailable, WriteOutcome.Done -> UiText.res(CoreR.string.write_failure_unavailable)

    is WriteOutcome.Rejected -> when (reason.rejection) {
        WriteRejection.UNKNOWN_JAM,
        WriteRejection.JAM_NOT_EDITABLE,
        WriteRejection.DUPLICATE_DATE,
        -> UiText.res(CoreR.string.write_failure_jam_changed)

        else -> UiText.res(CoreR.string.write_failure_sheet_refused)
    }
}

/** The failed edit or deletion reason: the edit codes first, then the shared outcomes. */
internal fun editFailureMessage(reason: WriteOutcome): UiText {
    val rejected = reason as? WriteOutcome.Rejected
    return when {
        rejected?.rejection == WriteRejection.JAM_EXISTS || rejected?.rejection == WriteRejection.TAB_EXISTS ->
            UiText.res(R.string.past_jams_create_exists)

        rejected?.rejection == WriteRejection.PAST_DATE -> UiText.res(CoreR.string.jam_form_date_past_error)

        rejected?.rejection == WriteRejection.SETLIST_CHANGED -> UiText.res(R.string.past_jams_setlist_changed)

        rejected?.isInvalidInput == true -> UiText.res(R.string.past_jams_create_invalid)

        else -> failureMessage(reason)
    }
}

/** Publication warns that a timeout can follow a successful server write, so retry remains safe. */
internal fun publishFailureMessage(reason: WriteOutcome): UiText = when (reason) {
    WriteOutcome.Offline -> UiText.res(R.string.past_jams_publish_offline)

    is WriteOutcome.Rejected -> if (reason.rejection == WriteRejection.EMPTY_SETLIST) {
        UiText.res(R.string.past_jams_empty_publish)
    } else {
        failureMessage(reason)
    }

    else -> failureMessage(reason)
}
