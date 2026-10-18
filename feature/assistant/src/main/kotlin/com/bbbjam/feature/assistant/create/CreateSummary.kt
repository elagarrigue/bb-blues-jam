package com.bbbjam.feature.assistant.create

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.admin.WriteRejection
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.assistant.R
import com.bbbjam.feature.assistant.apply.ApplySummaryUiModel
import com.bbbjam.feature.assistant.apply.ApplyTone
import com.bbbjam.feature.assistant.apply.failedCountText
import com.bbbjam.feature.assistant.apply.writeFailureText
import com.bbbjam.feature.assistant.jam.jamFailureReason
import com.bbbjam.feature.assistant.run.ApplyRun
import com.bbbjam.feature.assistant.run.JamFailure
import com.bbbjam.feature.assistant.run.JamStep
import com.bbbjam.feature.assistant.run.SongApply
import java.time.LocalDate

/**
 * The summary of a create run (C2): the jam is created first and the songs are loaded into the date the
 * server echoed. It never reads `Listo` unless the jam was created and every song was added.
 */
internal fun ApplyRun.createSummary(
    events: EventHandler<ApplySummaryUiModel.Event>,
    snapshot: JamsSnapshot?,
): ApplySummaryUiModel {
    val stopLabel = UiText.res(R.string.assistant_apply_stop).takeIf { state == ApplyRun.State.Running }
    val (text, tone) = createText(snapshot)
    return ApplySummaryUiModel(text, tone, stopLabel, events)
}

private fun ApplyRun.createText(snapshot: JamsSnapshot?): Pair<UiText, ApplyTone> {
    val date = jamDate
    val loaded = addedCount + skippedCount
    return when (val current = state) {
        ApplyRun.State.Running ->
            (
                if (date ==
                    null
                ) {
                    UiText.res(R.string.assistant_create_creating)
                } else {
                    createLoadingText(date, loadedOrFailed(), planned)
                }
                ) to
                ApplyTone.NEUTRAL

        ApplyRun.State.Finished -> finishedText(date, snapshot)

        is ApplyRun.State.Aborted -> abortedText(current.reason, date, snapshot) to ApplyTone.FAILURE

        ApplyRun.State.ScriptOutdated ->
            createNotCreatedText(jamFailureReason(JamFailure.ScriptOutdated)) to ApplyTone.FAILURE

        ApplyRun.State.Stopped ->
            (date?.let { createStoppedText(it, loaded, planned) } ?: notCreatedText()) to ApplyTone.MUTED

        ApplyRun.State.Interrupted ->
            (date?.let { createInterruptedText(it, loaded, planned) } ?: notCreatedText()) to ApplyTone.FAILURE
    }
}

private fun notCreatedText() = createNotCreatedText(UiText.res(CoreR.string.write_failure_unavailable))

private fun ApplyRun.finishedText(date: LocalDate?, snapshot: JamsSnapshot?): Pair<UiText, ApplyTone> = when {
    date == null -> notCreatedText() to ApplyTone.FAILURE

    isSuccess -> createSuccessText(date, planned, behind(date, snapshot)) to ApplyTone.SUCCESS

    else -> createPartialText(date, addedCount + skippedCount, planned, failedCountText(failedCount)) to
        ApplyTone.FAILURE
}

private fun ApplyRun.loadedOrFailed() = songs.count {
    it.state is SongApply.State.Added || it.state == SongApply.State.Skipped ||
        it.state is SongApply.State.Failed
}

private fun ApplyRun.abortedText(reason: WriteOutcome, date: LocalDate?, snapshot: JamsSnapshot?): UiText {
    val creation = jam
    return when {
        creation is JamStep.Failed -> createNotCreatedText(jamFailureReason(creation.reason))

        date == null -> createNotCreatedText(writeFailureText(reason))

        // The first add refused as `jam_not_editable` on the jam just created: the script predates D-29.
        addedCount == 0 && behind(date, snapshot) != null &&
            (reason as? WriteOutcome.Rejected)?.rejection == WriteRejection.JAM_NOT_EDITABLE ->
            UiText.res(R.string.assistant_create_outdated_held_back)

        else -> createPartialText(date, addedCount + skippedCount, planned, writeFailureText(reason))
    }
}

/** The upcoming jam [date] waits behind, or null when [date] is the upcoming jam (or the snapshot is unknown). */
internal fun behind(date: LocalDate, snapshot: JamsSnapshot?): LocalDate? =
    snapshot?.upcoming?.date?.takeIf { it != date && date > it }
