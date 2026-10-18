package com.bbbjam.feature.pastjams.create

import com.bbbjam.core.ui.jamform.JamFormField
import com.bbbjam.core.ui.jamform.JamFormUiModel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel

/**
 * The "Nueva jam" action of the Jams tab (`admin-jams-tab`, J7/J9): the button ([label]), the form
 * dialog while [form] is set, and the result of the last creation in [progress]. One handler takes
 * every event; the creation goes through `JamCreator`, bound to the setlist repository (D-13, D-25).
 */
internal data class NewJamUiModel(
    val label: String,
    val form: JamFormUiModel?,
    val progress: NewJamProgressUiModel?,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        data object Open : Event
        data object Cancel : Event
        data class Edit(val field: JamFormField, val text: String) : Event
        data object Create : Event
        data object Retry : Event
        data object Dismiss : Event
    }
}

/** What the last creation did: still going, done, or a card. */
internal sealed interface NewJamProgressUiModel : UiModel {
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
