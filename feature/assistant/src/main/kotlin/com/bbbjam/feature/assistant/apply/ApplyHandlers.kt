package com.bbbjam.feature.assistant.apply

import androidx.compose.runtime.MutableState
import com.bbbjam.core.assistant.AssistantReply
import com.bbbjam.core.data.jams.JamFormInput
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.jams.checkJamForm
import com.bbbjam.core.data.jams.formatFormDate
import com.bbbjam.core.data.jams.formatFormTime
import com.bbbjam.core.data.jams.jamFormSeed
import com.bbbjam.core.ui.jamform.JamFormUiModel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.feature.assistant.chat.AssistantChat
import com.bbbjam.feature.assistant.chat.ChatTurn
import com.bbbjam.feature.assistant.create.CreateFormState
import com.bbbjam.feature.assistant.run.ApplyMode
import com.bbbjam.feature.assistant.run.ApplyPlan
import com.bbbjam.feature.assistant.run.JamStep
import com.bbbjam.feature.assistant.run.ProposalApplier
import com.bbbjam.feature.assistant.run.ReplacePlan
import com.bbbjam.feature.assistant.run.applyPlan
import com.bbbjam.feature.assistant.run.applyPlanFor
import com.bbbjam.feature.assistant.run.replacePlan
import java.time.LocalDate

/**
 * The handlers for using a proposal, keyed by turn id and target (an earlier model's handler may stay in
 * use). Requesting only opens that target's dialog, or the create form. Confirm is the one place
 * [ProposalApplier.apply] and [ProposalApplier.replace] are called, and the form's submit the one place
 * [ProposalApplier.create] is (D-24): each acts only while its dialog or form is open for that turn, closes
 * it first (a second tap in the same frame finds it closed), and re-plans from [snapshot], the latest jams
 * snapshot, for the target it was opened for.
 */
internal class ApplyHandlers(
    private val chat: AssistantChat,
    private val applier: ProposalApplier,
    private val confirming: MutableState<String?>,
    private val form: CreateFormState,
    private val snapshot: () -> JamsSnapshot?,
    private val today: () -> LocalDate,
) {
    val apply: (Long, ApplyMode) -> EventHandler<TargetActionUiModel.Event> = { id, mode ->
        EventHandler(applyKey(id, mode)) { event ->
            when (event) {
                TargetActionUiModel.Event.Request ->
                    if (mode == ApplyMode.CREATE) openForm(id) else confirming.value = applyKey(id, mode)
            }
        }
    }

    val confirmation: (Long) -> EventHandler<ConfirmationUiModel.Event> = { id ->
        EventHandler(id) { event ->
            val open = confirming.value?.let(::parseApplyKey)
            if (open?.first == id) {
                confirming.value = null
                if (event == ConfirmationUiModel.Event.Confirm) confirm(id, open.second)
            }
        }
    }

    val createForm: (Long) -> EventHandler<JamFormUiModel.Event> = { id ->
        EventHandler(id) { event ->
            if (form.open?.turnId == id) {
                when (event) {
                    is JamFormUiModel.Event.Edit -> form.edit(event.field, event.text)
                    JamFormUiModel.Event.Cancel -> form.close()
                    JamFormUiModel.Event.Submit -> submit(id)
                }
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

    private fun proposal(turnId: Long): AssistantReply.Proposal? {
        val turns = chat.turns.value
        if (turns.applicableTurnId() != turnId) return null
        val answered = turns.first { it.id == turnId }.state as ChatTurn.State.Answered
        return answered.reply as AssistantReply.Proposal
    }

    private fun confirm(turnId: Long, mode: ApplyMode) {
        val songs = proposal(turnId)?.songs ?: return
        when (mode) {
            ApplyMode.ADD -> (addPlan(turnId, songs) as? ApplyPlan.Ready)?.let { applier.apply(turnId, it) }

            ApplyMode.REPLACE ->
                (replacePlan(songs, snapshot()) as? ReplacePlan.Ready)?.let { applier.replace(turnId, it) }

            // Crear goes through its own form and submit.
            ApplyMode.CREATE -> Unit
        }
    }

    // After a create run that made the jam, "Reintentar los que faltan" targets that jam, held back or not (C6).
    private fun addPlan(turnId: Long, songs: List<com.bbbjam.core.assistant.ProposedSong>): ApplyPlan {
        val created = applier.runs.value[turnId]?.takeIf { it.mode == ApplyMode.CREATE && it.jam is JamStep.Created }
        val date = created?.jamDate
        return if (date != null) applyPlanFor(songs, snapshot(), date) else applyPlan(songs, snapshot())
    }

    private fun openForm(turnId: Long) {
        val proposal = proposal(turnId) ?: return
        val seed = jamFormSeed(snapshot())
        val jam = proposal.jam
        form.open(
            turnId,
            JamFormInput(
                date = jam?.date?.let(::formatFormDate).orEmpty(),
                time = formatFormTime(jam?.startTime ?: seed.time),
                venue = jam?.venue ?: seed.venue,
            ),
        )
    }

    private fun submit(turnId: Long) {
        val open = form.open
        val songs = proposal(turnId)?.songs
        if (open == null || open.turnId != turnId || songs == null) return
        form.close()
        val values = checkJamForm(open.input, snapshot(), today()).values ?: return
        applier.create(turnId, values.date, values.time, values.venue, songs)
    }
}
