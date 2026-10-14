package com.bbbjam.feature.assistant

import androidx.compose.runtime.MutableState
import com.bbbjam.core.data.assistant.AssistantReply
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.ui.presenter.EventHandler

/**
 * The handlers for loading a proposal, keyed by turn id (an earlier model's handler may stay in use).
 * Requesting only opens the dialog. Confirm is the one place [ProposalApplier.apply] is called
 * (D-24): it acts only while that turn's dialog is open, closes it first (a second Confirm in the
 * same frame finds it closed), and re-plans from [snapshot], the latest jams snapshot.
 */
internal class ApplyHandlers(
    private val chat: AssistantChat,
    private val applier: ProposalApplier,
    private val confirming: MutableState<Long?>,
    private val snapshot: () -> JamsSnapshot?,
) {
    val apply: (Long) -> EventHandler<ApplyUiModel.Action.Event> = { id ->
        EventHandler(id) { event ->
            when (event) {
                ApplyUiModel.Action.Event.Request -> confirming.value = id
            }
        }
    }

    val confirmation: (Long) -> EventHandler<ConfirmationUiModel.Event> = { id ->
        EventHandler(id) { event ->
            if (confirming.value == id) {
                confirming.value = null
                if (event == ConfirmationUiModel.Event.Confirm) confirm(id)
            }
        }
    }

    val stop: (Long) -> EventHandler<ApplySummaryUiModel.Event> = { id ->
        EventHandler(id) { event ->
            when (event) {
                ApplySummaryUiModel.Event.Stop -> applier.stop(id)
            }
        }
    }

    private fun confirm(turnId: Long) {
        val turns = chat.turns.value
        if (turns.applicableTurnId() != turnId) return
        val answered = turns.first { it.id == turnId }.state as ChatTurn.State.Answered
        val proposal = answered.reply as AssistantReply.Proposal
        val plan = applyPlan(proposal.songs, snapshot())
        if (plan is ApplyPlan.Ready) applier.apply(turnId, plan)
    }
}
