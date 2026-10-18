package com.bbbjam.feature.assistant

import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.state.EmptyStateUiModel
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.assistant.apply.ApplyStatusLineUiModel
import com.bbbjam.feature.assistant.apply.ApplyStatusUiModel
import com.bbbjam.feature.assistant.apply.ApplySummaryUiModel
import com.bbbjam.feature.assistant.apply.ApplyUiModel
import com.bbbjam.feature.assistant.jam.JamCardUiModel

/** The Asistente tab (`assistant-admin-tab`): either the not-configured block or the chat. */
internal sealed interface AssistantUiModel : UiModel {
    val title: UiText

    /** The build has no key: only the title and this block, no composer. */
    data class NotConfigured(override val title: UiText, val block: EmptyStateUiModel) : AssistantUiModel

    /** The chat. [intro] is set only while there is no turn. */
    data class Chat(
        override val title: UiText,
        val intro: EmptyStateUiModel?,
        val status: StatusUiModel,
        val turns: List<TurnUiModel>,
        val composer: ComposerUiModel,
    ) : AssistantUiModel
}

/** What the tab says about the service: nothing, a static checking line, or a banner with "Reintentar". */
internal sealed interface StatusUiModel : UiModel {
    data object None : StatusUiModel

    data class Checking(val text: UiText) : StatusUiModel

    data class Banner(val message: UiText, val retryLabel: UiText, val events: EventHandler<Event>) : StatusUiModel {
        sealed interface Event : UiEvent {
            /** Ask for the service status again. */
            data object Retry : Event
        }
    }
}

/** One request and its reply. [id] keys the lazy list; it is never drawn. */
internal data class TurnUiModel(val id: Long, val authorLabel: UiText, val request: String, val reply: ReplyUiModel)

/** What a turn shows under its request. */
internal sealed interface ReplyUiModel : UiModel {
    data class Pending(
        val loadingLabel: UiText,
        val songsHeading: UiText?,
        val songs: List<ProposedSongUiModel>,
        val cancelLabel: UiText,
        val events: EventHandler<Event>,
    ) : ReplyUiModel {
        sealed interface Event : UiEvent {
            data object Cancel : Event
        }
    }

    data class Proposal(
        val authorLabel: UiText,
        val heading: UiText?,
        val songs: List<ProposedSongUiModel>,
        val message: String,
        val questionsHeading: UiText?,
        val questions: List<String>,
        val discardedNote: UiText?,
        val apply: ApplyUiModel = ApplyUiModel.None,
        val summary: ApplySummaryUiModel? = null,
        val removals: List<ApplyStatusLineUiModel> = emptyList(),
        val order: UiText? = null,
        val jam: JamCardUiModel? = null,
        val jamNote: UiText? = null,
    ) : ReplyUiModel

    /**
     * What arrived of an answer that broke or was cancelled: the songs, marked incomplete, and a retry.
     * Never a target or a jam card (`assistant-streaming`). [isCancelled] picks the muted message colour.
     */
    data class Incomplete(
        val heading: UiText,
        val songs: List<ProposedSongUiModel>,
        val message: UiText,
        val isCancelled: Boolean,
        val retry: RetryUiModel?,
    ) : ReplyUiModel

    data class Failed(val message: UiText, val retry: RetryUiModel?) : ReplyUiModel

    data class Cancelled(val message: UiText, val retry: RetryUiModel?) : ReplyUiModel
}

/** The "Reintentar" action, present only on the last turn. */
internal data class RetryUiModel(val label: UiText, val events: EventHandler<Event>) : UiModel {
    sealed interface Event : UiEvent {
        data object Retry : Event
    }
}

internal data class ProposedSongUiModel(
    val positionLabel: String,
    val title: String,
    val artist: String,
    val key: String,
    val keyDescription: UiText,
    val reason: String,
    val applyStatus: ApplyStatusUiModel? = null,
)

/** The field and the "Enviar" button. */
internal data class ComposerUiModel(
    val text: String,
    val label: UiText,
    val placeholder: UiText,
    val sendLabel: UiText,
    val sendEnabled: Boolean,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        data class TextChanged(val text: String) : Event

        data object Send : Event
    }
}
