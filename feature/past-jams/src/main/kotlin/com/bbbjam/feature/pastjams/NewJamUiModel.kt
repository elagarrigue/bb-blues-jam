package com.bbbjam.feature.pastjams

import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel

/**
 * The "Nueva jam" action of the Jams tab (`admin-jams-tab`, J7/J9): the button ([label]), the form
 * dialog while [form] is set, and the result of the last creation in [progress]. One handler takes
 * every event; the creation goes through `JamCreator`, the action registry's entry (D-13, D-25).
 */
data class NewJamUiModel(
    val label: String,
    val form: NewJamFormUiModel?,
    val progress: NewJamProgressUiModel?,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        data object Open : Event
        data object Cancel : Event
        data class Edit(val field: NewJamField, val text: String) : Event
        data object Create : Event
        data object Retry : Event
        data object Dismiss : Event
    }
}

/** Which field an [NewJamUiModel.Event.Edit] is for. */
enum class NewJamField { DATE, TIME, VENUE }

/**
 * The dialog: the typed values, one optional error per field, the notes and whether `Crear la jam` is
 * enabled. [waitsNote] is set only for a date behind the upcoming jam; [draftNote] is always there.
 */
data class NewJamFormUiModel(
    val title: String,
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
    val waitsNote: String?,
    val draftNote: String,
    val createLabel: String,
    val cancelLabel: String,
    val canCreate: Boolean,
) : UiModel

/** What the last creation did: still going, done, or a card. */
sealed interface NewJamProgressUiModel : UiModel {
    data class Creating(val status: String) : NewJamProgressUiModel

    data class Created(val message: String) : NewJamProgressUiModel

    /** [retryLabel] is null when retrying is pointless (the script is outdated). */
    data class Failed(
        val title: String,
        val message: String,
        val consequence: String,
        val retryLabel: String?,
        val dismissLabel: String,
    ) : NewJamProgressUiModel
}
