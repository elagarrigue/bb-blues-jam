package com.bbbjam.feature.assistant.apply

import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.feature.assistant.run.ApplyRun
import com.bbbjam.feature.assistant.run.SongApply

/** The summary of an Agregar run (`assistant-apply-proposal`). */
internal fun ApplyRun.addSummary(events: EventHandler<ApplySummaryUiModel.Event>): ApplySummaryUiModel {
    val date = checkNotNull(jamDate)
    val added = addedCount
    return when (val current = state) {
        ApplyRun.State.Running -> ApplySummaryUiModel(
            text = ApplyOutcomeCopy.running(songs.count { it.state.isDone() } - skippedBeforeRun(), planned, date),
            tone = ApplyTone.NEUTRAL,
            stopLabel = ApplyCopy.APPLY_STOP,
            events = events,
        )

        ApplyRun.State.Finished -> if (failedCount == 0) {
            ApplySummaryUiModel(ApplyOutcomeCopy.success(added, skippedCount, date), ApplyTone.SUCCESS, null, events)
        } else {
            ApplySummaryUiModel(ApplyOutcomeCopy.partial(added, planned, failedCount), ApplyTone.FAILURE, null, events)
        }

        is ApplyRun.State.Aborted -> ApplySummaryUiModel(
            ApplyOutcomeCopy.aborted(added, planned, ApplyOutcomeCopy.writeFailure(current.reason)),
            ApplyTone.FAILURE,
            null,
            events,
        )

        ApplyRun.State.Stopped ->
            ApplySummaryUiModel(ApplyOutcomeCopy.stopped(added, planned), ApplyTone.MUTED, null, events)

        ApplyRun.State.Interrupted, ApplyRun.State.ScriptOutdated ->
            ApplySummaryUiModel(ApplyOutcomeCopy.interrupted(added, planned), ApplyTone.FAILURE, null, events)
    }
}

// Songs the run has finished with (added, skipped by the server, or failed), not counting the ones the plan skipped.
private fun SongApply.State.isDone() =
    this is SongApply.State.Added || this == SongApply.State.Skipped || this is SongApply.State.Failed

private fun ApplyRun.skippedBeforeRun() = songs.size - planned
