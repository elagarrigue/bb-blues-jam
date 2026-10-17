package com.bbbjam.feature.assistant.jam

import androidx.compose.runtime.MutableState
import com.bbbjam.core.data.assistant.AssistantReply
import com.bbbjam.core.data.jams.JamPlan
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.jams.jamCreationPlan
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.feature.assistant.apply.ConfirmationUiModel
import com.bbbjam.feature.assistant.chat.AssistantChat
import com.bbbjam.feature.assistant.chat.ChatTurn
import com.bbbjam.feature.assistant.run.JamCreationRunner
import java.time.LocalDate

/**
 * The handlers for creating a proposed jam, keyed by turn id (an earlier model's handler may stay in
 * use). Requesting only opens the dialog. Confirm is the one place [JamCreationRunner.create] is called
 * (D-24): it acts only while that turn's dialog is open, closes it first (a second Confirm in the same
 * frame finds it closed), and re-plans from [snapshot], the latest jams snapshot.
 */
internal class JamHandlers(
    private val chat: AssistantChat,
    private val runner: JamCreationRunner,
    private val confirming: MutableState<Long?>,
    private val snapshot: () -> JamsSnapshot?,
    private val today: () -> LocalDate,
) {
    val request: (Long) -> EventHandler<JamActionUiModel.Event> = { id ->
        EventHandler(id) { event ->
            when (event) {
                JamActionUiModel.Event.Request -> confirming.value = id
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

    private fun confirm(turnId: Long) {
        val turns = chat.turns.value
        if (turns.creatableTurnId() != turnId) return
        val answered = turns.first { it.id == turnId }.state as ChatTurn.State.Answered
        val jam = (answered.reply as AssistantReply.Proposal).jam ?: return
        if (jamCreationPlan(jam.date, snapshot(), today()) is JamPlan.Ready) runner.create(turnId, jam)
    }
}
