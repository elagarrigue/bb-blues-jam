package com.bbbjam.feature.assistant

import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel

/**
 * The "NUEVA JAM" card of a proposal that carries a jam (`assistant-create-jam`). [action] is null on
 * every turn but the creatable one, while a turn is pending, when the plan is blocked and once the
 * jam is created. [note] says what is held back or why it cannot be created.
 */
data class JamCardUiModel(
    val heading: String,
    val date: String,
    val detail: String,
    val note: String?,
    val action: JamActionUiModel?,
    val result: JamResultUiModel?,
) : UiModel

/** "Crear la jam" (or "Reintentar"): tapping it only opens [confirmation]; nothing is written (D-24). */
data class JamActionUiModel(
    val label: String,
    val enabled: Boolean,
    val events: EventHandler<Event>,
    val confirmation: ConfirmationUiModel?,
) : UiModel {
    sealed interface Event : UiEvent {
        data object Request : Event
    }
}

/** The creation's one-line status, a polite live region. */
data class JamResultUiModel(val text: String, val tone: ApplyTone) : UiModel
