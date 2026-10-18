package com.bbbjam.feature.assistant.apply

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.admin.WriteRejection
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.text.SpanishDateNames
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.assistant.R
import java.time.LocalDate

// The texts of an Agregar run: progress, summary and failures (`assistant-apply-proposal`).

private fun alreadyThereText(count: Int): UiText = UiText.plural(R.plurals.assistant_apply_already_there, count)

internal fun failedCountText(count: Int): UiText = UiText.plural(R.plurals.assistant_apply_failed_count, count)

internal fun applyRunningText(done: Int, total: Int, date: LocalDate): UiText =
    UiText.res(R.string.assistant_apply_running, done, total, jamDateText(date))

internal fun applySuccessText(added: Int, skipped: Int, date: LocalDate): UiText {
    val jam = jamDateText(date)
    val head = if (added == 0) {
        UiText.res(R.string.assistant_apply_success_none, jam)
    } else {
        UiText.plural(R.plurals.assistant_apply_success, added, added, jam)
    }
    return if (skipped > 0) {
        UiText.res(R.string.assistant_apply_success_with_skipped, head, alreadyThereText(skipped))
    } else {
        head
    }
}

internal fun applyPartialText(added: Int, planned: Int, failed: Int): UiText =
    UiText.res(R.string.assistant_apply_partial, added, themesText(planned), failedCountText(failed))

internal fun applyAbortedText(added: Int, planned: Int, reason: UiText): UiText =
    UiText.res(R.string.assistant_apply_aborted, added, themesText(planned), reason)

internal fun applyStoppedText(added: Int, planned: Int): UiText =
    UiText.res(R.string.assistant_apply_stopped, added, themesText(planned))

internal fun applyInterruptedText(added: Int, planned: Int): UiText =
    UiText.res(R.string.assistant_apply_interrupted, added, themesText(planned))

/** The reason a write did not happen, as a sentence. */
internal fun writeFailureText(reason: WriteOutcome): UiText = UiText.res(
    when (reason) {
        WriteOutcome.AccessRefused -> CoreR.string.write_failure_access_refused

        WriteOutcome.Offline -> R.string.assistant_apply_write_offline

        WriteOutcome.Unavailable, WriteOutcome.Done -> CoreR.string.write_failure_unavailable

        is WriteOutcome.Rejected -> when (reason.rejection) {
            WriteRejection.UNKNOWN_SONG -> CoreR.string.write_failure_not_in_catalog
            WriteRejection.UNKNOWN_JAM, WriteRejection.JAM_NOT_EDITABLE -> CoreR.string.write_failure_jam_changed
            else -> CoreR.string.write_failure_sheet_refused
        }
    },
)

/** A song's failure caption: the offline sentence already says nothing was confirmed. */
internal fun songFailureText(reason: WriteOutcome): UiText = if (reason == WriteOutcome.Offline) {
    writeFailureText(reason)
} else {
    UiText.res(R.string.assistant_apply_song_failure, writeFailureText(reason))
}
