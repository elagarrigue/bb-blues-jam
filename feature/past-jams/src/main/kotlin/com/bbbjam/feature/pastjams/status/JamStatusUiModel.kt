package com.bbbjam.feature.pastjams.status

import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.status.JamStatusChipsUiModel

/**
 * Changing a future jam's status (`admin-set-jam-status`, D-26; single-select [chips] by
 * `jam-status-chips`): the selected chip is the confirmed status, every chip is disabled while a change,
 * a publication or an edit is sending, [publishUnavailable] says why PUBLICADA cannot be chosen, the
 * modal [confirming] dialog when leaving PUBLICADA, a [failure] card and one handler for every event. The
 * chip being applied is marked pending inside [chips] (`jam-status-chip-loading`); no sending text.
 */
internal data class JamStatusUiModel(
    val chips: JamStatusChipsUiModel,
    val publishUnavailable: String?,
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
    val prompt: String,
    val body: String,
    val confirmLabel: String,
    val cancelLabel: String,
) : UiModel

/** [retryLabel] is null when retrying is pointless (the script is outdated). */
internal data class StatusFailureUiModel(
    val id: Long,
    val title: String,
    val message: String,
    val consequence: String,
    val retryLabel: String?,
    val dismissLabel: String,
) : UiModel

/** Publishing the upcoming jam: the modal confirmation (opened by the Publicada chip) and the failure card. */
internal sealed interface PublishUiModel : UiModel {
    data class Confirming(
        val prompt: String,
        val details: String,
        val reversibleNote: String,
        val confirmLabel: String,
        val cancelLabel: String,
        val events: EventHandler<Event>,
    ) : PublishUiModel
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
        data object Confirm : Event
        data object Cancel : Event
        data object Retry : Event
        data object Dismiss : Event
    }
}
