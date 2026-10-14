package com.bbbjam.feature.assistant

import com.bbbjam.core.data.assistant.AssistantReply
import com.bbbjam.core.data.assistant.ProposedJam
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.presenter.EventHandler
import java.time.LocalDate

/** What the mapping needs to draw the apply parts: the runs, the jams snapshot and the open dialog's turn. */
internal data class ApplyInputs(
    val runs: Map<Long, ApplyRun> = emptyMap(),
    val snapshot: JamsSnapshot? = null,
    val confirmingTurnId: Long? = null,
    val creations: Map<Long, JamCreation> = emptyMap(),
    val today: LocalDate? = null,
    val confirmingJamTurnId: Long? = null,
)

/**
 * The id of the turn that may be loaded: the latest whose reply is a proposal with songs, and only
 * while no turn is pending (A9). Null otherwise.
 */
internal fun List<ChatTurn>.applicableTurnId(): Long? {
    if (any { it.state == ChatTurn.State.Pending }) return null
    return lastOrNull { turn ->
        val answered = turn.state as? ChatTurn.State.Answered
        (answered?.reply as? AssistantReply.Proposal)?.songs?.isNotEmpty() == true
    }?.id
}

/** The three parts a proposal gets from a run: the action, the summary and each song's status. */
internal class ApplyParts(
    val apply: ApplyUiModel,
    val summary: ApplySummaryUiModel?,
    val statuses: Map<SongId, ApplyStatusUiModel>,
)

internal fun applyParts(
    turnId: Long,
    proposal: AssistantReply.Proposal,
    isApplicable: Boolean,
    inputs: ApplyInputs,
    handlers: AssistantHandlers,
): ApplyParts {
    val run = inputs.runs[turnId]
    val anyRunning = inputs.runs.values.any { it.state == ApplyRun.State.Running } ||
        inputs.creations.values.any { it == JamCreation.Creating }
    val statuses = run?.songs.orEmpty().mapNotNull { apply -> apply.status()?.let { apply.song.songId to it } }.toMap()
    val apply = if (!isApplicable || run?.isSuccess == true || run?.state == ApplyRun.State.Running) {
        ApplyUiModel.None
    } else {
        actionFor(turnId, proposal, run, anyRunning, inputs, handlers)
    }
    return ApplyParts(apply, run?.summary(handlers.stop(turnId)), statuses)
}

private fun actionFor(
    turnId: Long,
    proposal: AssistantReply.Proposal,
    run: ApplyRun?,
    anyRunning: Boolean,
    inputs: ApplyInputs,
    handlers: AssistantHandlers,
): ApplyUiModel = when (val plan = applyPlan(proposal.songs, inputs.snapshot)) {
    ApplyPlan.Unknown -> ApplyUiModel.None

    // A proposal that also carries a jam loads its list only into that jam (assistant-create-jam, D3).
    is ApplyPlan.Ready, is ApplyPlan.Blocked -> {
        val jam = proposal.jam
        if (jam != null && inputs.snapshot != null) {
            jamListGate(turnId, jam, plan, run, anyRunning, inputs, handlers)
        } else {
            planAction(turnId, plan, run, anyRunning, inputs, handlers)
        }
    }
}

private fun jamListGate(
    turnId: Long,
    jam: ProposedJam,
    plan: ApplyPlan,
    run: ApplyRun?,
    anyRunning: Boolean,
    inputs: ApplyInputs,
    handlers: AssistantHandlers,
): ApplyUiModel {
    val upcoming = inputs.snapshot?.upcoming
    val creation = inputs.creations[turnId]
    return when {
        upcoming?.date == jam.date -> planAction(turnId, plan, run, anyRunning, inputs, handlers)

        creation == JamCreation.Creating -> ApplyUiModel.None

        creation == JamCreation.Created && upcoming != null ->
            ApplyUiModel.Blocked(JamCopy.listHeldBack(upcoming.date))

        else -> ApplyUiModel.Blocked(JamCopy.LIST_AFTER_CREATE)
    }
}

private fun planAction(
    turnId: Long,
    plan: ApplyPlan,
    run: ApplyRun?,
    anyRunning: Boolean,
    inputs: ApplyInputs,
    handlers: AssistantHandlers,
): ApplyUiModel = when (plan) {
    ApplyPlan.Unknown -> ApplyUiModel.None

    is ApplyPlan.Blocked -> ApplyUiModel.Blocked(
        when (plan.reason) {
            ApplyPlan.Blocked.Reason.NO_UPCOMING -> ApplyCopy.BLOCKED_NO_UPCOMING
            ApplyPlan.Blocked.Reason.UNREADABLE -> ApplyCopy.BLOCKED_UNREADABLE
            ApplyPlan.Blocked.Reason.ALL_PRESENT -> ApplyCopy.BLOCKED_ALL_PRESENT
        },
    )

    is ApplyPlan.Ready -> ApplyUiModel.Action(
        label = if (run == null) ApplyCopy.APPLY else ApplyCopy.APPLY_RETRY,
        enabled = !anyRunning,
        events = handlers.apply(turnId),
        confirmation = if (inputs.confirmingTurnId ==
            turnId
        ) {
            plan.confirmation(handlers.confirmation(turnId))
        } else {
            null
        },
    )
}

private fun ApplyPlan.Ready.confirmation(events: EventHandler<ConfirmationUiModel.Event>): ConfirmationUiModel {
    val lines = buildList {
        add(ApplyCopy.applyIntro(toAdd.size, jamDate))
        if (existingCount > 0) add(ApplyCopy.applyExisting(existingCount))
        if (skipped.isNotEmpty()) add(ApplyCopy.applySkipped(skipped.map { it.title }))
        if (status == JamStatus.PUBLISHED) add(ApplyCopy.APPLY_PUBLISHED)
    }
    return ConfirmationUiModel(
        title = ApplyCopy.APPLY_DIALOG_TITLE,
        lines = lines,
        confirmLabel = ApplyCopy.applyConfirm(toAdd.size),
        cancelLabel = ApplyCopy.APPLY_CANCEL,
        events = events,
    )
}

private fun SongApply.status(): ApplyStatusUiModel? = when (val current = state) {
    SongApply.State.Waiting -> null

    SongApply.State.Adding -> ApplyStatusUiModel(ApplyCopy.STATUS_ADDING, ApplyStatusKind.PROGRESS)

    is SongApply.State.Added -> ApplyStatusUiModel(ApplyCopy.STATUS_ADDED, ApplyStatusKind.DONE)

    SongApply.State.Skipped -> ApplyStatusUiModel(ApplyCopy.STATUS_SKIPPED, ApplyStatusKind.SKIPPED)

    is SongApply.State.Failed ->
        ApplyStatusUiModel(ApplyOutcomeCopy.songFailure(current.reason), ApplyStatusKind.FAILED)

    SongApply.State.NotAttempted ->
        ApplyStatusUiModel(ApplyCopy.STATUS_NOT_ATTEMPTED, ApplyStatusKind.NOT_ATTEMPTED)
}

private fun ApplyRun.summary(events: EventHandler<ApplySummaryUiModel.Event>): ApplySummaryUiModel {
    val added = addedCount
    return when (val current = state) {
        ApplyRun.State.Running -> ApplySummaryUiModel(
            text = ApplyOutcomeCopy.running(songs.count { it.state.isDone() } - skippedBeforeRun(), planned, jamDate),
            tone = ApplyTone.NEUTRAL,
            stopLabel = ApplyCopy.APPLY_STOP,
            events = events,
        )

        ApplyRun.State.Finished -> if (failedCount == 0) {
            ApplySummaryUiModel(ApplyOutcomeCopy.success(added, skippedCount, jamDate), ApplyTone.SUCCESS, null, events)
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

        ApplyRun.State.Interrupted ->
            ApplySummaryUiModel(ApplyOutcomeCopy.interrupted(added, planned), ApplyTone.FAILURE, null, events)
    }
}

// Songs the run has finished with (added, skipped by the server, or failed), not counting the ones the plan skipped.
private fun SongApply.State.isDone() =
    this is SongApply.State.Added || this == SongApply.State.Skipped || this is SongApply.State.Failed

private fun ApplyRun.skippedBeforeRun() = songs.size - planned
