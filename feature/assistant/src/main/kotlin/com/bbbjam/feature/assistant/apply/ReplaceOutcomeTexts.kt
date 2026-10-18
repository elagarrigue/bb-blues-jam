package com.bbbjam.feature.assistant.apply

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.admin.WriteRejection
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.assistant.R
import java.time.LocalDate

// The texts of a replace run: progress, summary and failures (`assistant-proposal-targets`).

internal fun replaceRunningText(step: Int, total: Int, date: LocalDate): UiText =
    UiText.res(R.string.assistant_replace_running, step, total, jamDateText(date))

internal fun replaceSuccessText(date: LocalDate): UiText =
    UiText.res(R.string.assistant_replace_success, jamDateText(date))

internal fun replaceCutInAddsText(added: Int, planned: Int, reason: UiText): UiText =
    UiText.res(R.string.assistant_replace_cut_in_adds, added, planned, reason)

internal fun replaceCutLaterText(added: Int, removed: Int, toRemove: Int, reason: UiText): UiText =
    UiText.res(R.string.assistant_replace_cut_later, added, removed, toRemove, reason)

internal fun replaceNotOrderedText(reason: UiText): UiText = UiText.res(R.string.assistant_replace_not_ordered, reason)

internal fun replaceStoppedText(added: Int, removed: Int): UiText =
    UiText.res(R.string.assistant_replace_stopped, added, removed)

internal fun replaceInterruptedText(added: Int, removed: Int): UiText =
    UiText.res(R.string.assistant_replace_interrupted, added, removed)

/** The reason a move did not happen: the sheet's own duplicate/empty positions get their own line. */
internal fun moveFailureText(reason: WriteOutcome): UiText =
    if (reason is WriteOutcome.Rejected && reason.rejection == WriteRejection.UNORDERED_SETLIST) {
        UiText.res(R.string.assistant_replace_unordered)
    } else {
        writeFailureText(reason)
    }

/** A removal's failure caption: the offline sentence already says nothing was confirmed. */
internal fun removalFailureText(reason: WriteOutcome): UiText = if (reason == WriteOutcome.Offline) {
    UiText.res(R.string.assistant_replace_removal_offline)
} else {
    UiText.res(R.string.assistant_replace_removal_failure, writeFailureText(reason))
}
