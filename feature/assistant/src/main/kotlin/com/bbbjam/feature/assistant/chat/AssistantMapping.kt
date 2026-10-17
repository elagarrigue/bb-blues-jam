package com.bbbjam.feature.assistant.chat

import com.bbbjam.core.data.assistant.AssistantFailure
import com.bbbjam.core.data.assistant.AssistantReply
import com.bbbjam.core.data.assistant.AssistantStatus
import com.bbbjam.core.data.assistant.ProposedSong
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.state.EmptyStateUiModel
import com.bbbjam.core.ui.text.SongListText
import com.bbbjam.feature.assistant.AssistantCopy
import com.bbbjam.feature.assistant.AssistantHandlers
import com.bbbjam.feature.assistant.AssistantUiModel
import com.bbbjam.feature.assistant.ComposerUiModel
import com.bbbjam.feature.assistant.ProposedSongUiModel
import com.bbbjam.feature.assistant.ReplyUiModel
import com.bbbjam.feature.assistant.RetryUiModel
import com.bbbjam.feature.assistant.StatusUiModel
import com.bbbjam.feature.assistant.TurnUiModel
import com.bbbjam.feature.assistant.apply.ApplyInputs
import com.bbbjam.feature.assistant.apply.ApplyParts
import com.bbbjam.feature.assistant.apply.ApplyStatusUiModel
import com.bbbjam.feature.assistant.apply.applicableTurnId
import com.bbbjam.feature.assistant.apply.applyParts
import com.bbbjam.feature.assistant.jam.JamCardUiModel
import com.bbbjam.feature.assistant.jam.JamCopy
import com.bbbjam.feature.assistant.jam.creatableTurnId
import com.bbbjam.feature.assistant.jam.jamCard

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
                    turn,
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
    turn: ChatTurn,
    flags: TurnFlags,
    handlers: AssistantHandlers,
    apply: ApplyInputs,
): ReplyUiModel {
    val id = turn.id
    val retry = if (flags.isLast) RetryUiModel(AssistantCopy.RETRY, handlers.retry(id)) else null
    val songs = turn.draft.rows()
    val proposal = (this as? ChatTurn.State.Answered)?.reply as? AssistantReply.Proposal
    val ending = ending(retry)
    // A broken or cancelled answer that streamed songs shows them as incomplete, never as a proposal.
    return when {
        proposal != null -> proposal.toModel(
            applyParts(id, proposal, flags.isApplicable, apply, handlers),
            jamCard(id, proposal, flags.isCreatable, apply, handlers),
        )

        ending == null -> ReplyUiModel.Pending(
            loadingLabel = AssistantCopy.PENDING_LABEL,
            songsHeading = if (songs.isEmpty()) null else AssistantCopy.proposalHeading(songs.size),
            songs = songs,
            cancelLabel = AssistantCopy.CANCEL,
            events = handlers.cancel(id),
        )

        songs.isEmpty() -> ending.plain

        else -> ReplyUiModel.Incomplete(
            heading = AssistantCopy.incompleteHeading(songs.size),
            songs = songs,
            message = ending.incompleteMessage,
            isCancelled = ending.isCancelled,
            retry = retry,
        )
    }
}

/** How a turn that is over and is not a proposal reads alone ([plain]) and when songs arrived before it ended. */
private class Ending(val plain: ReplyUiModel, val incompleteMessage: String, val isCancelled: Boolean = false)

// Null for a pending turn and for a proposal.
private fun ChatTurn.State.ending(retry: RetryUiModel?): Ending? = when (this) {
    ChatTurn.State.Pending -> null

    is ChatTurn.State.Answered -> when (val answer = reply) {
        is AssistantReply.Proposal -> null

        is AssistantReply.Failed ->
            Ending(ReplyUiModel.Failed(answer.failure.message(), retry), answer.failure.messageWithDraft())

        AssistantReply.NotConfigured ->
            Ending(
                ReplyUiModel.Failed(AssistantCopy.NOT_CONFIGURED_MESSAGE, null),
                AssistantCopy.NOT_CONFIGURED_MESSAGE,
            )
    }

    ChatTurn.State.Cancelled ->
        Ending(ReplyUiModel.Cancelled(AssistantCopy.CANCELLED, retry), AssistantCopy.CANCELLED, isCancelled = true)

    ChatTurn.State.Interrupted ->
        Ending(ReplyUiModel.Failed(AssistantCopy.INTERRUPTED, retry), AssistantCopy.INCOMPLETE)
}

private fun List<ProposedSong>.rows(): List<ProposedSongUiModel> = mapIndexed { index, song -> song.toRow(index, null) }

private fun ProposedSong.toRow(index: Int, applyStatus: ApplyStatusUiModel?) = ProposedSongUiModel(
    positionLabel = SongListText.positionLabel(index + 1),
    title = title,
    artist = artist,
    key = key.value,
    keyDescription = SongListText.keyDescription(key.value),
    reason = reason,
    applyStatus = applyStatus,
)

private fun AssistantReply.Proposal.toModel(parts: ApplyParts, jamCard: JamCardUiModel?) = ReplyUiModel.Proposal(
    authorLabel = AssistantCopy.AUTHOR_ASSISTANT,
    heading = if (songs.isEmpty()) null else AssistantCopy.proposalHeading(songs.size),
    songs = songs.mapIndexed { index, song -> song.toRow(index, parts.statuses[song.songId]) },
    message = message,
    questionsHeading = if (questions.isEmpty()) null else AssistantCopy.QUESTIONS_HEADING,
    questions = questions,
    discardedNote = if (rejectedSongIds.isEmpty()) null else AssistantCopy.discarded(rejectedSongIds.size),
    apply = parts.apply,
    summary = parts.summary,
    removals = parts.removals,
    order = parts.order,
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
    AssistantFailure.Incomplete -> AssistantCopy.INCOMPLETE_RETRY
}

// What broke after some songs arrived: the answer is not complete, whatever the cause (the status is never shown).
private fun AssistantFailure.messageWithDraft(): String = when (this) {
    AssistantFailure.Incomplete, AssistantFailure.InvalidResponse -> AssistantCopy.INCOMPLETE
    AssistantFailure.CatalogEmpty -> message()
    else -> message() + AssistantCopy.NOT_COMPLETE_SUFFIX
}
