package com.bbbjam.feature.assistant

import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.state.EmptyStateUiModel

/** The Asistente tab (`assistant-admin-tab`): either the not-configured block or the chat. */
sealed interface AssistantUiModel : UiModel {
    val title: String

    /** The build has no key: only the title and this block, no composer. */
    data class NotConfigured(override val title: String, val block: EmptyStateUiModel) : AssistantUiModel

    /** The chat. [intro] is set only while there is no turn. */
    data class Chat(
        override val title: String,
        val intro: EmptyStateUiModel?,
        val status: StatusUiModel,
        val turns: List<TurnUiModel>,
        val composer: ComposerUiModel,
    ) : AssistantUiModel
}

/** What the tab says about the service: nothing, a static checking line, or a banner with "Reintentar". */
sealed interface StatusUiModel : UiModel {
    data object None : StatusUiModel

    data class Checking(val text: String) : StatusUiModel

    data class Banner(val message: String, val retryLabel: String, val events: EventHandler<Event>) : StatusUiModel {
        sealed interface Event : UiEvent {
            /** Ask for the service status again. */
            data object Retry : Event
        }
    }
}

/** One request and its reply. [id] keys the lazy list; it is never drawn. */
data class TurnUiModel(val id: Long, val authorLabel: String, val request: String, val reply: ReplyUiModel)

/** What a turn shows under its request. */
sealed interface ReplyUiModel : UiModel {
    data class Pending(
        val status: String,
        val hint: String,
        val cancelLabel: String,
        val events: EventHandler<Event>,
    ) : ReplyUiModel {
        sealed interface Event : UiEvent {
            data object Cancel : Event
        }
    }

    data class Proposal(
        val authorLabel: String,
        val heading: String?,
        val songs: List<ProposedSongUiModel>,
        val message: String,
        val questionsHeading: String?,
        val questions: List<String>,
        val discardedNote: String?,
    ) : ReplyUiModel

    data class Failed(val message: String, val retry: RetryUiModel?) : ReplyUiModel

    data class Cancelled(val message: String, val retry: RetryUiModel?) : ReplyUiModel
}

/** The "Reintentar" action, present only on the last turn. */
data class RetryUiModel(val label: String, val events: EventHandler<Event>) : UiModel {
    sealed interface Event : UiEvent {
        data object Retry : Event
    }
}

data class ProposedSongUiModel(
    val positionLabel: String,
    val title: String,
    val artist: String,
    val key: String,
    val keyDescription: String,
    val reason: String,
)

/** The field and the "Enviar" button. */
data class ComposerUiModel(
    val text: String,
    val label: String,
    val placeholder: String,
    val sendLabel: String,
    val sendEnabled: Boolean,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        data class TextChanged(val text: String) : Event

        data object Send : Event
    }
}
