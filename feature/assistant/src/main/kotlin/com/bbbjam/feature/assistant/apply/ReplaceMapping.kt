package com.bbbjam.feature.assistant.apply

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.assistant.R
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

    fun summary(text: UiText, tone: ApplyTone, stopLabel: UiText? = null) =
        ApplySummaryUiModel(text, tone, stopLabel, events)
    return when (val current = state) {
        ApplyRun.State.Running -> {
            val total = planned + removals.size + ((order as? OrderStep.Moving)?.total ?: 0)
            summary(
                replaceRunningText((stepsDone() + 1).coerceAtMost(total), total, date),
                ApplyTone.NEUTRAL,
                UiText.res(R.string.assistant_apply_stop),
            )
        }

        ApplyRun.State.Finished -> when {
            ok -> summary(replaceSuccessText(date), ApplyTone.SUCCESS)

            isSuccess -> summary(UiText.res(R.string.assistant_replace_mismatch), ApplyTone.FAILURE)

            else -> summary(
                replaceCutLaterText(addedCount, removedCount, removals.size, firstFailure()),
                ApplyTone.FAILURE,
            )
        }

        is ApplyRun.State.Aborted -> summary(abortedText(current.reason), ApplyTone.FAILURE)

        ApplyRun.State.Stopped -> summary(replaceStoppedText(addedCount, removedCount), ApplyTone.MUTED)

        ApplyRun.State.Interrupted, ApplyRun.State.ScriptOutdated ->
            summary(replaceInterruptedText(addedCount, removedCount), ApplyTone.FAILURE)
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

private fun ApplyRun.abortedText(reason: WriteOutcome): UiText = when {
    order is OrderStep.Failed -> replaceNotOrderedText(moveFailureText(reason))

    removals.none { it.state != SongRemoval.State.NotAttempted } ->
        replaceCutInAddsText(addedCount, planned, writeFailureText(reason))

    else -> replaceCutLaterText(
        addedCount,
        removedCount,
        removals.size,
        removalFailureText(reason),
    )
}

// The first thing that went wrong in a run that finished with failures.
private fun ApplyRun.firstFailure(): UiText {
    val song = songs.firstNotNullOfOrNull { (it.state as? SongApply.State.Failed)?.reason }
    val removal = removals.firstNotNullOfOrNull { (it.state as? SongRemoval.State.Failed)?.reason }
    return when {
        song != null -> songFailureText(song)
        removal != null -> removalFailureText(removal)
        else -> UiText.raw("")
    }
}

/** The removed songs' lines: title and caption, none for a removal that has not started. */
internal fun ApplyRun.removalLines(): List<ApplyStatusLineUiModel> = removals.mapNotNull { removal ->
    val title = removal.song.title
    when (val state = removal.state) {
        SongRemoval.State.Waiting -> null

        SongRemoval.State.Removing -> ApplyStatusLineUiModel(
            title,
            UiText.res(R.string.assistant_replace_removing),
            ApplyStatusKind.PROGRESS,
        )

        SongRemoval.State.Removed -> ApplyStatusLineUiModel(
            title,
            UiText.res(R.string.assistant_replace_removed),
            ApplyStatusKind.DONE,
        )

        is SongRemoval.State.Failed ->
            ApplyStatusLineUiModel(title, removalFailureText(state.reason), ApplyStatusKind.FAILED)

        SongRemoval.State.NotAttempted ->
            ApplyStatusLineUiModel(
                title,
                UiText.res(R.string.assistant_replace_removal_not_attempted),
                ApplyStatusKind.NOT_ATTEMPTED,
            )
    }
}

/** The order phase's line, drawn under the removals; null when there is nothing to say. */
internal fun ApplyRun.orderLine(): UiText? = when (order) {
    null, OrderStep.Waiting, OrderStep.Ordered -> null

    is OrderStep.Moving -> UiText.res(R.string.assistant_replace_ordering)

    is OrderStep.Failed -> UiText.res(R.string.assistant_replace_order_failed)

    OrderStep.NotAttempted -> if (state ==
        ApplyRun.State.Finished
    ) {
        UiText.res(R.string.assistant_replace_order_not_attempted)
    } else {
        null
    }
}
