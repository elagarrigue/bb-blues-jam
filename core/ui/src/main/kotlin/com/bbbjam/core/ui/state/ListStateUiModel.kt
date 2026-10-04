package com.bbbjam.core.ui.state

import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel

/**
 * A list with nothing to list (DESIGN.md "Required States", Empty): a concrete [title] and an
 * invitation [message], never "No hay nada todavía". Not the filter's no-results case, which
 * belongs to the filter bar.
 */
data class EmptyStateUiModel(val title: String, val message: String) : UiModel

/**
 * Nothing is cached and the read failed (DESIGN.md "Required States", Error): a [title] naming
 * what could not be loaded, a [message] that says what to do (offline or any other failure; the
 * kind and its detail never reach the musician) and the [retryLabel] button.
 */
data class ListErrorUiModel(
    val title: String,
    val message: String,
    val retryLabel: String,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        /** Read the list again. A read, never a write. */
        data object Retry : Event
    }
}

/**
 * Cached data is shown but the latest refresh failed (DESIGN.md "Required States", Offline): a
 * [title] ("Sin conexión" or "No se pudo actualizar") and a [detail] with the data's age, or
 * "Actualizando…" while a retry runs. [retryLabel] is null while refreshing, so no action is drawn.
 */
data class StalenessNoticeUiModel(
    val title: String,
    val detail: String,
    val retryLabel: String?,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        /** Read the list again. A read, never a write. */
        data object Retry : Event
    }
}
