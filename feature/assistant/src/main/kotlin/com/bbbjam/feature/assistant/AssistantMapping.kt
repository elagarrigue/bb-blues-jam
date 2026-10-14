package com.bbbjam.feature.assistant

import com.bbbjam.core.data.assistant.AssistantFailure
import com.bbbjam.core.data.assistant.AssistantReply
import com.bbbjam.core.data.assistant.AssistantStatus
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.state.EmptyStateUiModel
import com.bbbjam.core.ui.text.SongListText

/** The pure mapping from the chat's state to the model; no Compose, tested on the JVM. */
internal fun assistantModel(
    isConfigured: Boolean,
    turns: List<ChatTurn>,
    service: ServiceState,
    draft: String,
    handlers: AssistantHandlers,
    apply: ApplyInputs = ApplyInputs(),
): AssistantUiModel {
    if (!isConfigured) {
        return AssistantUiModel.NotConfigured(
            AssistantCopy.TITLE,
            EmptyStateUiModel(AssistantCopy.NOT_CONFIGURED_TITLE, AssistantCopy.NOT_CONFIGURED_MESSAGE),
        )
    }
    val pending = turns.any { it.state == ChatTurn.State.Pending }
    val applicableTurnId = turns.applicableTurnId()
    val creatableTurnId = turns.creatableTurnId()
    val available = service == ServiceState.Known(AssistantStatus.Available)
    return AssistantUiModel.Chat(
        title = AssistantCopy.TITLE,
        intro = if (turns.isEmpty()) {
            EmptyStateUiModel(AssistantCopy.INTRO_TITLE, AssistantCopy.INTRO_MESSAGE)
        } else {
            null
        },
        status = service.toStatus(handlers),
        turns = turns.mapIndexed { index, turn ->
            TurnUiModel(
                id = turn.id,
                authorLabel = AssistantCopy.AUTHOR_YOU,
                request = turn.request,
                reply = turn.state.toReply(
                    turn.id,
                    TurnFlags(
                        isLast = index == turns.lastIndex && available,
                        isApplicable = turn.id == applicableTurnId,
                        isCreatable = turn.id == creatableTurnId,
                    ),
                    handlers,
                    apply,
                ),
            )
        },
        composer = ComposerUiModel(
            text = draft,
            label = AssistantCopy.COMPOSER_LABEL,
            placeholder = AssistantCopy.COMPOSER_PLACEHOLDER,
            sendLabel = if (pending) AssistantCopy.SEND_WAITING else AssistantCopy.SEND,
            sendEnabled = available && !pending && draft.isNotBlank(),
            events = handlers.composer,
        ),
    )
}

// Available and NotConfigured (the screen already shows its own block) draw nothing.
private fun ServiceState.toStatus(handlers: AssistantHandlers): StatusUiModel = when (this) {
    ServiceState.Checking -> StatusUiModel.Checking(AssistantCopy.CHECKING)

    is ServiceState.Known -> when (status) {
        AssistantStatus.Available, AssistantStatus.NotConfigured -> StatusUiModel.None
        AssistantStatus.ServiceDown -> banner(AssistantCopy.SERVICE_DOWN, handlers)
        AssistantStatus.Offline -> banner(AssistantCopy.OFFLINE, handlers)
        AssistantStatus.Unauthorized -> banner(AssistantCopy.UNAUTHORIZED, handlers)
    }
}

private fun banner(message: String, handlers: AssistantHandlers) =
    StatusUiModel.Banner(message, AssistantCopy.RETRY, handlers.status)

/** What a turn may offer: a retry (the last turn), loading its list and creating its jam. */
private class TurnFlags(val isLast: Boolean, val isApplicable: Boolean, val isCreatable: Boolean)

private fun ChatTurn.State.toReply(
    id: Long,
    flags: TurnFlags,
    handlers: AssistantHandlers,
    apply: ApplyInputs,
): ReplyUiModel {
    fun retry() = if (flags.isLast) RetryUiModel(AssistantCopy.RETRY, handlers.retry(id)) else null
    return when (this) {
        ChatTurn.State.Pending -> ReplyUiModel.Pending(
            status = AssistantCopy.PENDING_STATUS,
            hint = AssistantCopy.PENDING_HINT,
            cancelLabel = AssistantCopy.CANCEL,
            events = handlers.cancel(id),
        )

        is ChatTurn.State.Answered -> when (val answer = reply) {
            is AssistantReply.Proposal -> answer.toModel(
                applyParts(id, answer, flags.isApplicable, apply, handlers),
                jamCard(id, answer, flags.isCreatable, apply, handlers),
            )

            is AssistantReply.Failed -> ReplyUiModel.Failed(answer.failure.message(), retry())

            AssistantReply.NotConfigured -> ReplyUiModel.Failed(AssistantCopy.NOT_CONFIGURED_MESSAGE, null)
        }

        ChatTurn.State.Cancelled -> ReplyUiModel.Cancelled(AssistantCopy.CANCELLED, retry())

        ChatTurn.State.Interrupted -> ReplyUiModel.Failed(AssistantCopy.INTERRUPTED, retry())
    }
}

private fun AssistantReply.Proposal.toModel(parts: ApplyParts, jamCard: JamCardUiModel?) = ReplyUiModel.Proposal(
    authorLabel = AssistantCopy.AUTHOR_ASSISTANT,
    heading = if (songs.isEmpty()) null else AssistantCopy.proposalHeading(songs.size),
    songs = songs.mapIndexed { index, song ->
        ProposedSongUiModel(
            positionLabel = SongListText.positionLabel(index + 1),
            title = song.title,
            artist = song.artist,
            key = song.key.value,
            keyDescription = SongListText.keyDescription(song.key.value),
            reason = song.reason,
            applyStatus = parts.statuses[song.songId],
        )
    },
    message = message,
    questionsHeading = if (questions.isEmpty()) null else AssistantCopy.QUESTIONS_HEADING,
    questions = questions,
    discardedNote = if (rejectedSongIds.isEmpty()) null else AssistantCopy.discarded(rejectedSongIds.size),
    apply = parts.apply,
    summary = parts.summary,
    jam = jamCard,
    jamNote = jamRejection?.let(JamCopy::rejection),
)

// The status code of Unavailable is never shown.
private fun AssistantFailure.message(): String = when (this) {
    AssistantFailure.Offline -> AssistantCopy.OFFLINE
    AssistantFailure.Unauthorized -> AssistantCopy.UNAUTHORIZED
    is AssistantFailure.Unavailable -> AssistantCopy.UNAVAILABLE
    AssistantFailure.InvalidResponse -> AssistantCopy.INVALID_RESPONSE
    AssistantFailure.TooManySteps -> AssistantCopy.TOO_MANY_STEPS
    AssistantFailure.CatalogEmpty -> AssistantCopy.CATALOG_EMPTY
}
