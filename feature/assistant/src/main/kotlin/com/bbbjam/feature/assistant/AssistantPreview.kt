package com.bbbjam.feature.assistant

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.data.assistant.AssistantFailure
import com.bbbjam.core.data.assistant.AssistantReply
import com.bbbjam.core.data.assistant.AssistantStatus
import com.bbbjam.core.data.assistant.ProposedSong
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.theme.BluesJamTheme

/** One preview per state: intro, pending, proposal, failure, not configured. Built by the real mapping. */
private val previewHandlers = AssistantHandlers(
    cancel = { id -> EventHandler(id) {} },
    retry = { id -> EventHandler(id) {} },
    composer = EventHandler {},
    status = EventHandler {},
    apply = { id, mode -> EventHandler(applyKey(id, mode)) {} },
    confirmation = { id -> EventHandler(id) {} },
    stop = { id -> EventHandler(id) {} },
    createForm = { id -> EventHandler(id) {} },
    jam = { id -> EventHandler(id) {} },
    jamConfirmation = { id -> EventHandler(id) {} },
)

private val previewProposal = AssistantReply.Proposal(
    songs = listOf(
        ProposedSong(SongId("a"), "Sweet Home Chicago", "Robert Johnson", Key("E"), "Para arrancar arriba."),
        ProposedSong(SongId("b"), "The Thrill Is Gone", "B.B. King", Key("Bm"), "Un respiro en el medio."),
    ),
    message = "Cierre arriba con un shuffle.",
    questions = listOf("¿Querés sumar una armónica?"),
    rejectedSongIds = listOf("x"),
)

@Composable
private fun StatePreview(
    turns: List<ChatTurn>,
    draft: String = "",
    isConfigured: Boolean = true,
    service: ServiceState = ServiceState.Known(AssistantStatus.Available),
) {
    BluesJamTheme { AssistantContent(assistantModel(isConfigured, turns, service, draft, previewHandlers)) }
}

@Preview
@Composable
private fun IntroPreview() = StatePreview(emptyList(), draft = "12 temas de blues nacional")

@Preview
@Composable
private fun PendingPreview() = StatePreview(listOf(ChatTurn(1, "12 temas de blues nacional", ChatTurn.State.Pending)))

@Preview
@Composable
private fun StreamingPreview() = StatePreview(
    listOf(ChatTurn(1, "12 temas de blues nacional", ChatTurn.State.Pending, previewProposal.songs)),
)

@Preview
@Composable
private fun IncompletePreview() = StatePreview(
    listOf(
        ChatTurn(
            1,
            "12 temas de blues nacional",
            ChatTurn.State.Answered(AssistantReply.Failed(AssistantFailure.Incomplete)),
            previewProposal.songs,
        ),
    ),
)

@Preview
@Composable
private fun ProposalPreview() = StatePreview(
    listOf(ChatTurn(1, "12 temas de blues nacional", ChatTurn.State.Answered(previewProposal))),
)

@Preview
@Composable
private fun FailedPreview() = StatePreview(
    listOf(ChatTurn(1, "12 temas", ChatTurn.State.Answered(AssistantReply.Failed(AssistantFailure.Offline)))),
)

@Preview
@Composable
private fun NotConfiguredPreview() = StatePreview(emptyList(), isConfigured = false)

@Preview
@Composable
private fun ServiceDownPreview() =
    StatePreview(emptyList(), draft = "12 temas", service = ServiceState.Known(AssistantStatus.ServiceDown))

@Preview
@Composable
private fun CheckingPreview() = StatePreview(emptyList(), service = ServiceState.Checking)
