package com.bbbjam.feature.assistant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
 */
class AssistantPresenter(private val chat: AssistantChat) : Presenter<AssistantUiModel, Unit> {

    @Composable
    override fun present(params: Unit): AssistantUiModel {
        val turns by chat.turns.collectAsState()
        val service by chat.service.collectAsState()
        // Asked each time the tab is shown; the chat runs it in its own scope and ignores a repeat.
        LaunchedEffect(Unit) { if (chat.isConfigured) chat.checkStatus() }
        val draft = rememberSaveable { mutableStateOf("") }
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
            )
        }
        return assistantModel(chat.isConfigured, turns, service, draft.value, handlers)
    }
}
