package com.bbbjam.feature.assistant.jam

import com.bbbjam.core.assistant.AssistantReply
import com.bbbjam.core.assistant.ProposedJam
import com.bbbjam.core.data.jams.JamHeldBack
import com.bbbjam.core.data.jams.JamPlan
import com.bbbjam.core.data.jams.jamCreationPlan
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.assistant.AssistantHandlers
import com.bbbjam.feature.assistant.R
import com.bbbjam.feature.assistant.apply.ApplyInputs
import com.bbbjam.feature.assistant.apply.ApplyTone
import com.bbbjam.feature.assistant.apply.ConfirmationUiModel
import com.bbbjam.feature.assistant.apply.confirmation
import com.bbbjam.feature.assistant.chat.ChatTurn
import com.bbbjam.feature.assistant.run.ApplyRun
import com.bbbjam.feature.assistant.run.JamCreation

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
        heading = UiText.res(R.string.assistant_jam_card_heading),
        date = jamDateInWordsText(jam.date),
        detail = jamDetailText(jam.startTime, jam.venue),
        note = jamNote(plan, creation),
        // A proposal with songs creates its jam through the targets block (C5); the card is information.
        action = if (proposal.songs.isEmpty()) jamAction(turnId, jam, plan, creation, inputs, handlers) else null,
        result = creation?.result(),
    )
}

private fun jamNote(plan: JamPlan, creation: JamCreation?): UiText? = when (plan) {
    JamPlan.Unknown -> null

    is JamPlan.Blocked -> when (plan.reason) {
        JamPlan.Blocked.Reason.PAST -> UiText.res(R.string.assistant_jam_blocked_past)

        JamPlan.Blocked.Reason.EXISTS -> if (creation ==
            JamCreation.Created
        ) {
            null
        } else {
            UiText.res(R.string.assistant_jam_blocked_exists)
        }

        JamPlan.Blocked.Reason.EARLIER -> plan.upcomingDate?.let(::jamBlockedEarlierText)
    }

    is JamPlan.Ready -> (plan.heldBack as? JamHeldBack.NewWaits)?.let { jamHeldBackNoteText(it.upcomingDate) }
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
        label = UiText.res(
            if (creation ==
                null
            ) {
                R.string.assistant_jam_create
            } else {
                R.string.assistant_jam_create_retry
            },
        ),
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
    title = UiText.res(R.string.assistant_jam_dialog_title),
    lines = buildList {
        add(UiText.res(R.string.assistant_jam_dialog_date, jamDateInWordsLowerText(jam.date)))
        add(UiText.res(R.string.assistant_jam_dialog_time, jamTime(jam.startTime)))
        add(UiText.res(R.string.assistant_jam_dialog_venue, jam.venue))
        add(UiText.res(R.string.assistant_jam_draft_line))
        (heldBack as? JamHeldBack.NewWaits)?.let { add(jamHeldBackLineText(it.upcomingDate)) }
    },
    confirmLabel = UiText.res(R.string.assistant_jam_create),
    cancelLabel = UiText.res(R.string.assistant_cancel),
    events = events,
)

private fun JamCreation.result(): JamResultUiModel = when (this) {
    JamCreation.Creating -> JamResultUiModel(UiText.res(R.string.assistant_jam_creating), ApplyTone.NEUTRAL)
    JamCreation.Created -> JamResultUiModel(UiText.res(R.string.assistant_jam_created), ApplyTone.SUCCESS)
    is JamCreation.Failed -> JamResultUiModel(jamFailureText(reason), ApplyTone.FAILURE)
}
