package com.bbbjam.feature.assistant.apply

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.feature.assistant.run.ApplyRun
import com.bbbjam.feature.assistant.run.OrderStep
import com.bbbjam.feature.assistant.run.SongApply
import com.bbbjam.feature.assistant.run.SongRemoval

/**
 * The summary of a replace run. [ok] is [succeeded]: a run that finished clean but whose list does not
 * match the proposal reads as a mismatch, never as `Listo` (R3).
 */
internal fun ApplyRun.replaceSummary(
    events: EventHandler<ApplySummaryUiModel.Event>,
    ok: Boolean,
): ApplySummaryUiModel {
    val date = checkNotNull(jamDate)

    fun summary(text: String, tone: ApplyTone, stopLabel: String? = null) =
        ApplySummaryUiModel(text, tone, stopLabel, events)
    return when (val current = state) {
        ApplyRun.State.Running -> {
            val total = planned + removals.size + ((order as? OrderStep.Moving)?.total ?: 0)
            summary(
                ReplaceOutcomeCopy.running((stepsDone() + 1).coerceAtMost(total), total, date),
                ApplyTone.NEUTRAL,
                ApplyCopy.APPLY_STOP,
            )
        }

        ApplyRun.State.Finished -> when {
            ok -> summary(ReplaceOutcomeCopy.success(date), ApplyTone.SUCCESS)

            isSuccess -> summary(ReplaceCopy.MISMATCH, ApplyTone.FAILURE)

            else -> summary(
                ReplaceOutcomeCopy.cutLater(addedCount, removedCount, removals.size, firstFailure()),
                ApplyTone.FAILURE,
            )
        }

        is ApplyRun.State.Aborted -> summary(abortedText(current.reason), ApplyTone.FAILURE)

        ApplyRun.State.Stopped -> summary(ReplaceOutcomeCopy.stopped(addedCount, removedCount), ApplyTone.MUTED)

        ApplyRun.State.Interrupted, ApplyRun.State.ScriptOutdated ->
            summary(ReplaceOutcomeCopy.interrupted(addedCount, removedCount), ApplyTone.FAILURE)
    }
}

private fun ApplyRun.stepsDone(): Int {
    val adds = songs.count {
        it.state is SongApply.State.Added || it.state == SongApply.State.Skipped ||
            it.state is SongApply.State.Failed
    }
    val removed = removals.count { it.state == SongRemoval.State.Removed || it.state is SongRemoval.State.Failed }
    return adds + removed + ((order as? OrderStep.Moving)?.done ?: 0)
}

private fun ApplyRun.abortedText(reason: WriteOutcome): String = when {
    order is OrderStep.Failed -> ReplaceOutcomeCopy.notOrdered(ReplaceOutcomeCopy.moveFailure(reason))

    removals.none { it.state != SongRemoval.State.NotAttempted } ->
        ReplaceOutcomeCopy.cutInAdds(addedCount, planned, ApplyOutcomeCopy.writeFailure(reason))

    else -> ReplaceOutcomeCopy.cutLater(
        addedCount,
        removedCount,
        removals.size,
        ReplaceOutcomeCopy.removalFailure(reason),
    )
}

// The first thing that went wrong in a run that finished with failures.
private fun ApplyRun.firstFailure(): String {
    val song = songs.firstNotNullOfOrNull { (it.state as? SongApply.State.Failed)?.reason }
    val removal = removals.firstNotNullOfOrNull { (it.state as? SongRemoval.State.Failed)?.reason }
    return when {
        song != null -> ApplyOutcomeCopy.songFailure(song)
        removal != null -> ReplaceOutcomeCopy.removalFailure(removal)
        else -> ""
    }
}

/** The removed songs' lines: title and caption, none for a removal that has not started. */
internal fun ApplyRun.removalLines(): List<ApplyStatusLineUiModel> = removals.mapNotNull { removal ->
    val title = removal.song.title
    when (val state = removal.state) {
        SongRemoval.State.Waiting -> null

        SongRemoval.State.Removing -> ApplyStatusLineUiModel(title, ReplaceCopy.REMOVING, ApplyStatusKind.PROGRESS)

        SongRemoval.State.Removed -> ApplyStatusLineUiModel(title, ReplaceCopy.REMOVED, ApplyStatusKind.DONE)

        is SongRemoval.State.Failed ->
            ApplyStatusLineUiModel(title, ReplaceOutcomeCopy.removalFailure(state.reason), ApplyStatusKind.FAILED)

        SongRemoval.State.NotAttempted ->
            ApplyStatusLineUiModel(title, ReplaceCopy.REMOVAL_NOT_ATTEMPTED, ApplyStatusKind.NOT_ATTEMPTED)
    }
}

/** The order phase's line, drawn under the removals; null when there is nothing to say. */
internal fun ApplyRun.orderLine(): String? = when (order) {
    null, OrderStep.Waiting, OrderStep.Ordered -> null
    is OrderStep.Moving -> ReplaceCopy.ORDERING
    is OrderStep.Failed -> ReplaceCopy.ORDER_FAILED
    OrderStep.NotAttempted -> if (state == ApplyRun.State.Finished) ReplaceCopy.ORDER_NOT_ATTEMPTED else null
}
