package com.bbbjam.feature.assistant.jam

import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.assistant.apply.ApplyTone
import com.bbbjam.feature.assistant.apply.ConfirmationUiModel
import com.bbbjam.feature.assistant.apply.confirmation

/**
 * The "NUEVA JAM" card of a proposal that carries a jam (`assistant-create-jam`). [action] is null on
 * every turn but the creatable one, while a turn is pending, when the plan is blocked and once the
 * jam is created. [note] says what is held back or why it cannot be created.
 */
internal data class JamCardUiModel(
    val heading: UiText,
    val date: UiText,
    val detail: UiText,
    val note: UiText?,
    val action: JamActionUiModel?,
    val result: JamResultUiModel?,
) : UiModel

/** "Crear la jam" (or "Reintentar"): tapping it only opens [confirmation]; nothing is written (D-24). */
internal data class JamActionUiModel(
    val label: UiText,
    val enabled: Boolean,
    val events: EventHandler<Event>,
    val confirmation: ConfirmationUiModel?,
) : UiModel {
    sealed interface Event : UiEvent {
        data object Request : Event
    }
}

/** The creation's one-line status, a polite live region. */
internal data class JamResultUiModel(val text: UiText, val tone: ApplyTone) : UiModel
