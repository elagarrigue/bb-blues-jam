package com.bbbjam.feature.assistant

import com.bbbjam.core.data.assistant.AssistantReply
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.presenter.EventHandler
import java.time.LocalDate

/**
 * What the mapping needs to draw the apply parts: the runs, the jams snapshot and the open dialogs.
 * [confirming] is the open dialog key ([applyKey]), null when none.
 */
internal data class ApplyInputs(
    val runs: Map<Long, ApplyRun> = emptyMap(),
    val snapshot: JamsSnapshot? = null,
    val confirming: String? = null,
    val creations: Map<Long, JamCreation> = emptyMap(),
    val today: LocalDate? = null,
    val confirmingJamTurnId: Long? = null,
    val createForm: CreateFormInputs? = null,
)

/** The key of the open dialog for [mode] of [turnId]: one saveable string for the whole tab. */
internal fun applyKey(turnId: Long, mode: ApplyMode): String = "$turnId|$mode"

/** The turn id and mode of [key], or null when it is not an [applyKey]. */
internal fun parseApplyKey(key: String): Pair<Long, ApplyMode>? {
    val parts = key.split('|')
    val turnId = parts.getOrNull(0)?.toLongOrNull()
    val mode = parts.getOrNull(1)?.let { name -> ApplyMode.entries.firstOrNull { it.name == name } }
    return if (parts.size == 2 && turnId != null && mode != null) turnId to mode else null
}

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

/** The parts a proposal gets from a run: the targets, the summary, each song's and removal's status, the order. */
internal class ApplyParts(
    val apply: ApplyUiModel,
    val summary: ApplySummaryUiModel?,
    val statuses: Map<SongId, ApplyStatusUiModel>,
    val removals: List<ApplyStatusLineUiModel> = emptyList(),
    val order: String? = null,
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
    val apply = if (!isApplicable || run?.succeeded(inputs.snapshot) == true || run?.state == ApplyRun.State.Running) {
        ApplyUiModel.None
    } else {
        targets(turnId, proposal, run, anyRunning, inputs, handlers)
    }
    return ApplyParts(
        apply = apply,
        summary = run?.summary(handlers.stop(turnId), inputs.snapshot),
        statuses = statuses,
        removals = run?.removalLines().orEmpty(),
        order = run?.orderLine(),
    )
}

private fun SongApply.status(): ApplyStatusUiModel? = when (val current = state) {
    SongApply.State.Waiting -> null

    SongApply.State.Adding -> ApplyStatusUiModel(ApplyCopy.STATUS_ADDING, ApplyStatusKind.PROGRESS)

    is SongApply.State.Added -> ApplyStatusUiModel(ApplyCopy.STATUS_ADDED, ApplyStatusKind.DONE)

    SongApply.State.Skipped -> ApplyStatusUiModel(ApplyCopy.STATUS_SKIPPED, ApplyStatusKind.SKIPPED)

    SongApply.State.Kept -> ApplyStatusUiModel(ReplaceCopy.KEPT, ApplyStatusKind.SKIPPED)

    is SongApply.State.Failed ->
        ApplyStatusUiModel(ApplyOutcomeCopy.songFailure(current.reason), ApplyStatusKind.FAILED)

    SongApply.State.NotAttempted ->
        ApplyStatusUiModel(ApplyCopy.STATUS_NOT_ATTEMPTED, ApplyStatusKind.NOT_ATTEMPTED)
}

/**
 * True when the run is a success the admin should see as one: it finished clean and, for a replace, the
 * latest snapshot lists exactly the proposal (R3). A replace whose list does not match is not a success.
 */
internal fun ApplyRun.succeeded(snapshot: JamsSnapshot?): Boolean =
    isSuccess && (mode != ApplyMode.REPLACE || matchesTarget(snapshot))

private fun ApplyRun.matchesTarget(snapshot: JamsSnapshot?): Boolean {
    val upcoming = snapshot?.upcoming?.takeIf { it.date == jamDate }
    val listed = (upcoming?.setlist as? Setlist.Available)?.songs?.map { it.songId }
    return listed == target
}

internal fun ApplyRun.summary(
    events: EventHandler<ApplySummaryUiModel.Event>,
    snapshot: JamsSnapshot?,
): ApplySummaryUiModel = when (mode) {
    ApplyMode.ADD -> addSummary(events)
    ApplyMode.REPLACE -> replaceSummary(events, succeeded(snapshot))
    ApplyMode.CREATE -> createSummary(events, snapshot)
}
