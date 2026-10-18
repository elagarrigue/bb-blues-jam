package com.bbbjam.core.ui.jamform

import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.text.UiText

/** Which field a [JamFormUiModel.Event.Edit] is for. */
enum class JamFormField { DATE, TIME, VENUE }

/** One labelled text field of the form: the typed [value], an optional [hint] and an optional [error]. */
data class JamFormFieldUiModel(val label: UiText, val value: String, val hint: UiText?, val error: UiText?) : UiModel

/**
 * The jam form dialog (`assistant-proposal-targets`, shared by the Jams tab's "Nueva jam" and its edit
 * dialog, and by the assistant's "Crear una jam nueva con esta lista"): [title], the three fields, the
 * [notes] under them, a [submitLabel] enabled only when [canSubmit], and a [cancelLabel]. Back and a tap
 * outside send [Event.Cancel]; nothing is written until the host handles [Event.Submit].
 */
data class JamFormUiModel(
    val title: UiText,
    val date: JamFormFieldUiModel,
    val time: JamFormFieldUiModel,
    val venue: JamFormFieldUiModel,
    val notes: List<UiText>,
    val submitLabel: UiText,
    val cancelLabel: UiText,
    val canSubmit: Boolean,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        data class Edit(val field: JamFormField, val text: String) : Event

        data object Submit : Event

        data object Cancel : Event
    }
}
