package com.bbbjam.feature.assistant.create

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.feature.assistant.apply.ApplyCopy
import com.bbbjam.feature.assistant.apply.ApplyOutcomeCopy
import com.bbbjam.feature.assistant.apply.ApplySummaryUiModel
import com.bbbjam.feature.assistant.apply.ApplyTone
import com.bbbjam.feature.assistant.jam.JamCopy
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
    val stopLabel = ApplyCopy.APPLY_STOP.takeIf { state == ApplyRun.State.Running }
    val (text, tone) = createText(snapshot)
    return ApplySummaryUiModel(text, tone, stopLabel, events)
}

private fun ApplyRun.createText(snapshot: JamsSnapshot?): Pair<String, ApplyTone> {
    val date = jamDate
    val loaded = addedCount + skippedCount
    return when (val current = state) {
        ApplyRun.State.Running ->
            (if (date == null) CreateCopy.CREATING else CreateCopy.loading(date, loadedOrFailed(), planned)) to
                ApplyTone.NEUTRAL

        ApplyRun.State.Finished -> finishedText(date, snapshot)

        is ApplyRun.State.Aborted -> abortedText(current.reason, date, snapshot) to ApplyTone.FAILURE

        ApplyRun.State.ScriptOutdated ->
            CreateCopy.notCreated(JamCopy.reason(JamFailure.ScriptOutdated)) to ApplyTone.FAILURE

        ApplyRun.State.Stopped ->
            (date?.let { CreateCopy.stopped(it, loaded, planned) } ?: notCreatedText()) to ApplyTone.MUTED

        ApplyRun.State.Interrupted ->
            (date?.let { CreateCopy.interrupted(it, loaded, planned) } ?: notCreatedText()) to ApplyTone.FAILURE
    }
}

private fun notCreatedText() = CreateCopy.notCreated(ApplyOutcomeCopy.WRITE_UNAVAILABLE)

private fun ApplyRun.finishedText(date: LocalDate?, snapshot: JamsSnapshot?): Pair<String, ApplyTone> = when {
    date == null -> notCreatedText() to ApplyTone.FAILURE

    isSuccess -> CreateCopy.success(date, planned, behind(date, snapshot)) to ApplyTone.SUCCESS

    else -> CreateCopy.partial(date, addedCount + skippedCount, planned, CreateCopy.failedCount(failedCount)) to
        ApplyTone.FAILURE
}

private fun ApplyRun.loadedOrFailed() = songs.count {
    it.state is SongApply.State.Added || it.state == SongApply.State.Skipped ||
        it.state is SongApply.State.Failed
}

private fun ApplyRun.abortedText(reason: WriteOutcome, date: LocalDate?, snapshot: JamsSnapshot?): String {
    val creation = jam
    return when {
        creation is JamStep.Failed -> CreateCopy.notCreated(JamCopy.reason(creation.reason))

        date == null -> CreateCopy.notCreated(ApplyOutcomeCopy.writeFailure(reason))

        // The first add refused as `jam_not_editable` on the jam just created: the script predates D-29.
        addedCount == 0 && behind(date, snapshot) != null && (reason as? WriteOutcome.Rejected)?.code == NOT_EDITABLE ->
            CreateCopy.OUTDATED_HELD_BACK

        else -> CreateCopy.partial(date, addedCount + skippedCount, planned, ApplyOutcomeCopy.writeFailure(reason))
    }
}

/** The upcoming jam [date] waits behind, or null when [date] is the upcoming jam (or the snapshot is unknown). */
internal fun behind(date: LocalDate, snapshot: JamsSnapshot?): LocalDate? =
    snapshot?.upcoming?.date?.takeIf { it != date && date > it }

private const val NOT_EDITABLE = "jam_not_editable"
