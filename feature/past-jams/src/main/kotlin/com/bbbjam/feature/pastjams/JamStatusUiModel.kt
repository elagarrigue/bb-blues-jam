package com.bbbjam.feature.pastjams

import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel

/**
 * Changing the upcoming jam's status (`admin-set-jam-status`, D-26), moved here from Próxima jam by
 * `admin-jams-tab`: the neutral [actions] (empty while a change or a publication is sending), the
 * modal [confirming] dialog when leaving PUBLICADA, the [sending] line, a [failure] card and one
 * handler for every event.
 */
data class JamStatusUiModel(
    val actions: List<StatusActionUiModel>,
    val confirming: StatusConfirmUiModel?,
    val sending: String?,
    val failure: StatusFailureUiModel?,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        data class Request(val target: JamStatus) : Event
        data object Confirm : Event
        data object Cancel : Event
        data class Retry(val id: Long) : Event
        data class Dismiss(val id: Long) : Event
    }
}

data class StatusActionUiModel(val label: String, val target: JamStatus) : UiModel

data class StatusConfirmUiModel(
    val prompt: String,
    val body: String,
    val confirmLabel: String,
    val cancelLabel: String,
) : UiModel

/** [retryLabel] is null when retrying is pointless (the script is outdated). */
data class StatusFailureUiModel(
    val id: Long,
    val title: String,
    val message: String,
    val consequence: String,
    val retryLabel: String?,
    val dismissLabel: String,
) : UiModel

/** Publishing the upcoming jam's list: idle button, inline confirmation, sending line, failure card. */
sealed interface PublishUiModel : UiModel {
    data class Idle(val label: String, val events: EventHandler<Event>) : PublishUiModel
    data class Confirming(
        val prompt: String,
        val details: String,
        val reversibleNote: String,
        val confirmLabel: String,
        val cancelLabel: String,
        val events: EventHandler<Event>,
    ) : PublishUiModel
    data class Publishing(val status: String) : PublishUiModel
    data class Failed(
        val id: Long,
        val title: String,
        val message: String,
        val consequence: String,
        val retryLabel: String,
        val dismissLabel: String,
        val events: EventHandler<Event>,
    ) : PublishUiModel

    sealed interface Event : UiEvent {
        data object Request : Event
        data object Confirm : Event
        data object Cancel : Event
        data object Retry : Event
        data object Dismiss : Event
    }
}
