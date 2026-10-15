package com.bbbjam.feature.assistant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.Presenter

/**
 * Presents the Asistente tab. The conversation is [AssistantChat]'s (it outlives this composition, so
 * leaving the tab does not cancel a request); this presenter holds only the composer's text. Nothing
 * here writes anywhere (D-24).
 *
 * The draft lives in one saveable state object created once, so an earlier model's handler (Compose
 * may keep it) writes through the same state. Send clears the draft only when the chat accepted the
 * request, so a double tap sends once.
 *
 * Loading a proposal (`assistant-apply-proposal`): the presenter also holds which turn's confirmation
 * dialog is open (one saveable `String?` key, `applyKey`: the turn and the target). The run itself
 * belongs to [ProposalApplier]; a write starts only
 * from the dialog's Confirm event, which re-plans from the latest jams snapshot (D-24).
 *
 * Creating a proposed jam (`assistant-create-jam`) works the same way with its own saveable `Long?` for
 * the open dialog and [JamCreationRunner]; "today" is read once per snapshot from [calendar].
 */
class AssistantPresenter(
    private val chat: AssistantChat,
    private val applier: ProposalApplier,
    private val jams: JamsRepository,
    private val creator: JamCreationRunner,
    private val calendar: JamCalendar,
) : Presenter<AssistantUiModel, Unit> {

    @Composable
    override fun present(params: Unit): AssistantUiModel {
        val turns by chat.turns.collectAsState()
        val service by chat.service.collectAsState()
        // Asked each time the tab is shown; the chat runs it in its own scope and ignores a repeat.
        LaunchedEffect(Unit) { if (chat.isConfigured) chat.checkStatus() }
        val draft = rememberSaveable { mutableStateOf("") }
        val runs by applier.runs.collectAsState()
        val snapshot by remember { jams.observeJams() }.collectAsState(initial = null)
        val latestSnapshot by rememberUpdatedState(snapshot)
        val confirming = rememberSaveable { mutableStateOf<String?>(null) }
        val creations by creator.creations.collectAsState()
        val today = remember(snapshot) { calendar.today() }
        val latestToday by rememberUpdatedState(today)
        val createForm = rememberCreateFormState()
        val applyHandlers = remember {
            ApplyHandlers(chat, applier, confirming, createForm, { latestSnapshot }, { latestToday })
        }
        val confirmingJam = rememberSaveable { mutableStateOf<Long?>(null) }
        val jamHandlers = remember {
            JamHandlers(chat, creator, confirmingJam, { latestSnapshot }, { latestToday })
        }
        val handlers = remember {
            AssistantHandlers(
                cancel = { id -> EventHandler(id) { chat.cancel(id) } },
                retry = { id -> EventHandler(id) { if (chat.isServiceAvailable) chat.retry(id) } },
                composer = EventHandler { event ->
                    when (event) {
                        is ComposerUiModel.Event.TextChanged -> draft.value = event.text

                        ComposerUiModel.Event.Send -> if (chat.isServiceAvailable &&
                            chat.send(draft.value)
                        ) {
                            draft.value = ""
                        }
                    }
                },
                status = EventHandler { event ->
                    when (event) {
                        StatusUiModel.Banner.Event.Retry -> chat.checkStatus()
                    }
                },
                apply = applyHandlers.apply,
                confirmation = applyHandlers.confirmation,
                createForm = applyHandlers.createForm,
                stop = applyHandlers.stop,
                jam = jamHandlers.request,
                jamConfirmation = jamHandlers.confirmation,
            )
        }
        return assistantModel(
            chat.isConfigured,
            turns,
            service,
            draft.value,
            handlers,
            ApplyInputs(runs, snapshot, confirming.value, creations, today, confirmingJam.value, createForm.open),
        )
    }
}
