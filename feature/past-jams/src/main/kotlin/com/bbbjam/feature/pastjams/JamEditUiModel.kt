package com.bbbjam.feature.pastjams

import com.bbbjam.core.ui.jamform.JamFormField
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel

/**
 * Editing and deleting a future jam (`admin-edit-delete-jam`, D-28) on one card: the two neutral actions
 * ([editLabel], [deleteLabel], null while hidden), the [sending] line, a [failure] card and one handler for
 * every event. [deleteLabel] is null too when the setlist's song count is not known.
 */
data class JamManageUiModel(
    val editLabel: String?,
    val deleteLabel: String?,
    val sending: String?,
    val failure: JamEditFailureUiModel?,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        data object Edit : Event
        data object Delete : Event
        data class Retry(val id: Long) : Event
        data class Dismiss(val id: Long) : Event
    }
}

/** [retryLabel] is null when retrying is pointless (the script is outdated, or the list changed). */
data class JamEditFailureUiModel(
    val id: Long,
    val title: String,
    val message: String,
    val consequence: String,
    val retryLabel: String?,
    val dismissLabel: String,
) : UiModel

/**
 * The edit dialog: the typed values, one optional error per field, the notes and whether [saveLabel] is
 * enabled. [dateNote] is set only when the date differs from the jam's; [visibilityNote] only for a jam
 * musicians already see.
 */
data class EditJamFormUiModel(
    val title: String,
    val jamDate: java.time.LocalDate,
    val dateLabel: String,
    val dateHint: String,
    val timeLabel: String,
    val timeHint: String,
    val venueLabel: String,
    val date: String,
    val time: String,
    val venue: String,
    val dateError: String?,
    val timeError: String?,
    val venueError: String?,
    val dateNote: String?,
    val visibilityNote: String?,
    val saveLabel: String,
    val cancelLabel: String,
    val canSave: Boolean,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        data class Edit(val field: JamFormField, val text: String) : Event
        data object Save : Event
        data object Cancel : Event
    }
}

/** The delete confirmation: the [title], the [lines] of consequences in order and the two buttons. */
data class DeleteJamUiModel(
    val title: String,
    val jamDate: java.time.LocalDate,
    val lines: List<String>,
    val confirmLabel: String,
    val cancelLabel: String,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        data object Confirm : Event
        data object Cancel : Event
    }
}
