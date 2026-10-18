package com.bbbjam.feature.assistant.chat

import com.bbbjam.core.assistant.AssistantFailure
import com.bbbjam.core.assistant.AssistantReply
import com.bbbjam.core.assistant.AssistantStatus
import com.bbbjam.core.assistant.ProposedSong
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.state.EmptyStateUiModel
import com.bbbjam.core.ui.text.SongListText
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.assistant.AssistantHandlers
import com.bbbjam.feature.assistant.AssistantUiModel
import com.bbbjam.feature.assistant.ComposerUiModel
import com.bbbjam.feature.assistant.ProposedSongUiModel
import com.bbbjam.feature.assistant.R
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
import com.bbbjam.feature.assistant.jam.creatableTurnId
import com.bbbjam.feature.assistant.jam.jamCard
import com.bbbjam.feature.assistant.jam.jamRejectionText

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
            UiText.res(CoreR.string.tab_assistant),
            EmptyStateUiModel(
                UiText.res(R.string.assistant_not_configured_title),
                UiText.res(R.string.assistant_not_configured_message),
            ),
        )
    }
    val pending = turns.any { it.state == ChatTurn.State.Pending }
    val applicableTurnId = turns.applicableTurnId()
    val creatableTurnId = turns.creatableTurnId()
    val available = service == ServiceState.Known(AssistantStatus.Available)
    return AssistantUiModel.Chat(
        title = UiText.res(CoreR.string.tab_assistant),
        intro = if (turns.isEmpty()) {
            EmptyStateUiModel(UiText.res(R.string.assistant_intro_title), UiText.res(R.string.assistant_intro_message))
        } else {
            null
        },
        status = service.toStatus(handlers),
        turns = turns.mapIndexed { index, turn ->
            TurnUiModel(
                id = turn.id,
                authorLabel = UiText.res(R.string.assistant_author_you),
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
            label = UiText.res(R.string.assistant_composer_label),
            placeholder = UiText.res(R.string.assistant_composer_placeholder),
            sendLabel = UiText.res(if (pending) R.string.assistant_send_waiting else R.string.assistant_send),
            sendEnabled = available && !pending && draft.isNotBlank(),
            events = handlers.composer,
        ),
    )
}

// Available and NotConfigured (the screen already shows its own block) draw nothing.
private fun ServiceState.toStatus(handlers: AssistantHandlers): StatusUiModel = when (this) {
    ServiceState.Checking -> StatusUiModel.Checking(UiText.res(R.string.assistant_checking))

    is ServiceState.Known -> when (status) {
        AssistantStatus.Available, AssistantStatus.NotConfigured -> StatusUiModel.None
        AssistantStatus.ServiceDown -> banner(UiText.res(R.string.assistant_service_down), handlers)
        AssistantStatus.Offline -> banner(UiText.res(R.string.assistant_offline), handlers)
        AssistantStatus.Unauthorized -> banner(UiText.res(R.string.assistant_unauthorized), handlers)
    }
}

private fun banner(message: UiText, handlers: AssistantHandlers) =
    StatusUiModel.Banner(message, UiText.res(R.string.assistant_retry), handlers.status)

/** What a turn may offer: a retry (the last turn), loading its list and creating its jam. */
private class TurnFlags(val isLast: Boolean, val isApplicable: Boolean, val isCreatable: Boolean)

private fun ChatTurn.State.toReply(
    turn: ChatTurn,
    flags: TurnFlags,
    handlers: AssistantHandlers,
    apply: ApplyInputs,
): ReplyUiModel {
    val id = turn.id
    val retry = if (flags.isLast) RetryUiModel(UiText.res(R.string.assistant_retry), handlers.retry(id)) else null
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
            loadingLabel = UiText.res(R.string.assistant_pending_label),
            songsHeading = if (songs.isEmpty()) null else proposalHeading(songs.size),
            songs = songs,
            cancelLabel = UiText.res(R.string.assistant_cancel),
            events = handlers.cancel(id),
        )

        songs.isEmpty() -> ending.plain

        else -> ReplyUiModel.Incomplete(
            heading = UiText.plural(R.plurals.assistant_incomplete_heading, songs.size),
            songs = songs,
            message = ending.incompleteMessage,
            isCancelled = ending.isCancelled,
            retry = retry,
        )
    }
}

/** How a turn that is over and is not a proposal reads alone ([plain]) and when songs arrived before it ended. */
private class Ending(val plain: ReplyUiModel, val incompleteMessage: UiText, val isCancelled: Boolean = false)

// Null for a pending turn and for a proposal.
private fun ChatTurn.State.ending(retry: RetryUiModel?): Ending? = when (this) {
    ChatTurn.State.Pending -> null

    is ChatTurn.State.Answered -> when (val answer = reply) {
        is AssistantReply.Proposal -> null

        is AssistantReply.Failed ->
            Ending(ReplyUiModel.Failed(answer.failure.message(), retry), answer.failure.messageWithDraft())

        AssistantReply.NotConfigured ->
            Ending(
                ReplyUiModel.Failed(UiText.res(R.string.assistant_not_configured_message), null),
                UiText.res(R.string.assistant_not_configured_message),
            )
    }

    ChatTurn.State.Cancelled ->
        Ending(
            ReplyUiModel.Cancelled(UiText.res(R.string.assistant_cancelled), retry),
            UiText.res(R.string.assistant_cancelled),
            isCancelled = true,
        )

    ChatTurn.State.Interrupted ->
        Ending(
            ReplyUiModel.Failed(UiText.res(R.string.assistant_interrupted), retry),
            UiText.res(R.string.assistant_incomplete),
        )
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
    authorLabel = UiText.res(CoreR.string.tab_assistant),
    heading = if (songs.isEmpty()) null else proposalHeading(songs.size),
    songs = songs.mapIndexed { index, song -> song.toRow(index, parts.statuses[song.songId]) },
    message = message,
    questionsHeading = if (questions.isEmpty()) null else UiText.res(R.string.assistant_questions_heading),
    questions = questions,
    discardedNote = if (rejectedSongIds.isEmpty()) {
        null
    } else {
        UiText.plural(
            R.plurals.assistant_discarded,
            rejectedSongIds.size,
        )
    },
    apply = parts.apply,
    summary = parts.summary,
    removals = parts.removals,
    order = parts.order,
    jam = jamCard,
    jamNote = jamRejection?.let(::jamRejectionText),
)

// The status code of Unavailable is never shown.
private fun AssistantFailure.message(): UiText = UiText.res(
    when (this) {
        AssistantFailure.Offline -> R.string.assistant_offline
        AssistantFailure.Unauthorized -> R.string.assistant_unauthorized
        is AssistantFailure.Unavailable -> R.string.assistant_unavailable
        AssistantFailure.InvalidResponse -> R.string.assistant_invalid_response
        AssistantFailure.TooManySteps -> R.string.assistant_too_many_steps
        AssistantFailure.CatalogEmpty -> R.string.assistant_catalog_empty
        AssistantFailure.Incomplete -> R.string.assistant_incomplete_retry
    },
)

private fun proposalHeading(count: Int): UiText = UiText.plural(R.plurals.assistant_proposal_heading, count)

// What broke after some songs arrived: the answer is not complete, whatever the cause (the status is never shown).
private fun AssistantFailure.messageWithDraft(): UiText = when (this) {
    AssistantFailure.Incomplete, AssistantFailure.InvalidResponse -> UiText.res(R.string.assistant_incomplete)
    AssistantFailure.CatalogEmpty -> message()
    else -> UiText.res(R.string.assistant_not_complete_suffix, message())
}
