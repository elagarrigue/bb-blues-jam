package com.bbbjam.feature.pastjams.status

import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.status.JamStatusChipsUiModel
import com.bbbjam.core.ui.text.UiText

/**
 * Changing a future jam's status (`admin-set-jam-status`, D-26; single-select [chips] by
 * `jam-status-chips`): the selected chip is the confirmed status, every chip is disabled while a change,
 * a publication or an edit is sending, [publishUnavailable] says why PUBLICADA cannot be chosen, the
 * modal [confirming] dialog when leaving PUBLICADA, a [failure] card and one handler for every event. The
 * chip being applied is marked pending inside [chips] (`jam-status-chip-loading`); no sending text.
 */
internal data class JamStatusUiModel(
    val chips: JamStatusChipsUiModel,
    val publishUnavailable: UiText?,
    val confirming: StatusConfirmUiModel?,
    val failure: StatusFailureUiModel?,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        data object Confirm : Event
        data object Cancel : Event
        data class Retry(val id: Long) : Event
        data class Dismiss(val id: Long) : Event
    }
}

internal data class StatusConfirmUiModel(
    val prompt: UiText,
    val body: UiText,
    val confirmLabel: UiText,
    val cancelLabel: UiText,
) : UiModel

/** [retryLabel] is null when retrying is pointless (the script is outdated). */
internal data class StatusFailureUiModel(
    val id: Long,
    val title: UiText,
    val message: UiText,
    val consequence: UiText,
    val retryLabel: UiText?,
    val dismissLabel: UiText,
) : UiModel

/** Publishing the upcoming jam: the modal confirmation (opened by the Publicada chip) and the failure card. */
internal sealed interface PublishUiModel : UiModel {
    data class Confirming(
        val prompt: UiText,
        val details: UiText,
        val reversibleNote: UiText,
        val confirmLabel: UiText,
        val cancelLabel: UiText,
        val events: EventHandler<Event>,
    ) : PublishUiModel
    data class Failed(
        val id: Long,
        val title: UiText,
        val message: UiText,
        val consequence: UiText,
        val retryLabel: UiText,
        val dismissLabel: UiText,
        val events: EventHandler<Event>,
    ) : PublishUiModel

    sealed interface Event : UiEvent {
        data object Confirm : Event
        data object Cancel : Event
        data object Retry : Event
        data object Dismiss : Event
    }
}
