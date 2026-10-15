package com.bbbjam.feature.assistant

import com.bbbjam.core.data.assistant.AssistantReply
import com.bbbjam.core.data.assistant.ProposedJam
import com.bbbjam.core.data.jams.JamHeldBack
import com.bbbjam.core.data.jams.JamPlan
import com.bbbjam.core.data.jams.jamCreationPlan
import com.bbbjam.core.ui.presenter.EventHandler

/**
 * The id of the turn whose jam may be created: the latest answered proposal with a valid jam, and only
 * while no turn is pending. Older turns show their jam as text without the button. Null otherwise.
 */
internal fun List<ChatTurn>.creatableTurnId(): Long? {
    if (any { it.state == ChatTurn.State.Pending }) return null
    return lastOrNull { turn ->
        val answered = turn.state as? ChatTurn.State.Answered
        (answered?.reply as? AssistantReply.Proposal)?.jam != null
    }?.id
}

/** The "NUEVA JAM" card of [proposal], or null when it carries no jam. */
internal fun jamCard(
    turnId: Long,
    proposal: AssistantReply.Proposal,
    isCreatable: Boolean,
    inputs: ApplyInputs,
    handlers: AssistantHandlers,
): JamCardUiModel? {
    val jam = proposal.jam ?: return null
    val creation = inputs.creations[turnId]
    val today = inputs.today
    val plan = if (isCreatable && today != null) jamCreationPlan(jam.date, inputs.snapshot, today) else JamPlan.Unknown
    return JamCardUiModel(
        heading = JamCopy.HEADING,
        date = JamCopy.dateInWords(jam.date),
        detail = JamCopy.detail(jam.startTime, jam.venue),
        note = jamNote(plan, creation),
        action = jamAction(turnId, jam, plan, creation, inputs, handlers),
        result = creation?.result(),
    )
}

private fun jamNote(plan: JamPlan, creation: JamCreation?): String? = when (plan) {
    JamPlan.Unknown -> null

    is JamPlan.Blocked -> when (plan.reason) {
        JamPlan.Blocked.Reason.PAST -> JamCopy.BLOCKED_PAST
        JamPlan.Blocked.Reason.EXISTS -> if (creation == JamCreation.Created) null else JamCopy.BLOCKED_EXISTS
        JamPlan.Blocked.Reason.EARLIER -> plan.upcomingDate?.let(JamCopy::blockedEarlier)
    }

    is JamPlan.Ready -> (plan.heldBack as? JamHeldBack.NewWaits)?.let { JamCopy.heldBackNote(it.upcomingDate) }
}

private fun jamAction(
    turnId: Long,
    jam: ProposedJam,
    plan: JamPlan,
    creation: JamCreation?,
    inputs: ApplyInputs,
    handlers: AssistantHandlers,
): JamActionUiModel? {
    if (plan !is JamPlan.Ready || creation == JamCreation.Creating || creation == JamCreation.Created) return null
    val busy = inputs.runs.values.any { it.state == ApplyRun.State.Running } ||
        inputs.creations.values.any { it == JamCreation.Creating }
    return JamActionUiModel(
        label = if (creation == null) JamCopy.CREATE else JamCopy.CREATE_RETRY,
        enabled = !busy,
        events = handlers.jam(turnId),
        confirmation = if (inputs.confirmingJamTurnId == turnId) {
            plan.confirmation(jam, handlers.jamConfirmation(turnId))
        } else {
            null
        },
    )
}

private fun JamPlan.Ready.confirmation(
    jam: ProposedJam,
    events: EventHandler<ConfirmationUiModel.Event>,
): ConfirmationUiModel = ConfirmationUiModel(
    title = JamCopy.DIALOG_TITLE,
    lines = buildList {
        add("Fecha: ${JamCopy.dateInWords(jam.date).lowercase()}")
        add("Hora: ${JamCopy.time(jam.startTime)}")
        add("Lugar: ${jam.venue}")
        add(JamCopy.DRAFT_LINE)
        (heldBack as? JamHeldBack.NewWaits)?.let { add(JamCopy.heldBackLine(it.upcomingDate)) }
    },
    confirmLabel = JamCopy.CREATE,
    cancelLabel = JamCopy.CANCEL,
    events = events,
)

private fun JamCreation.result(): JamResultUiModel = when (this) {
    JamCreation.Creating -> JamResultUiModel(JamCopy.CREATING, ApplyTone.NEUTRAL)
    JamCreation.Created -> JamResultUiModel(JamCopy.CREATED, ApplyTone.SUCCESS)
    is JamCreation.Failed -> JamResultUiModel(JamCopy.failure(reason), ApplyTone.FAILURE)
}
