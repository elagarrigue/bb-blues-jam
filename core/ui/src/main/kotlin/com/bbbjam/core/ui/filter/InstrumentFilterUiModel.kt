package com.bbbjam.core.ui.filter

import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel

/**
 * The instrument filter bar (DESIGN.md "Filter chip"): a [heading], the `Todos` chip and one chip
 * per instrument ([chips], in that order), then either [summary] ("2 de 13 temas con cupo libre
 * para bajo") when the selection matches at least one song, or [noResults] with the [clearLabel]
 * action when it matches none. Both are null while nothing is selected; never both set.
 */
data class InstrumentFilterBarUiModel(
    val heading: String,
    val chips: List<FilterChipUiModel>,
    val summary: String?,
    val noResults: String?,
    val clearLabel: String,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        /** "Ver todos los temas": deselect every instrument. */
        data object Clear : Event
    }
}

/**
 * One chip: [label] ("Bajo") and [count] ("2") drawn side by side, [contentDescription] for a
 * screen reader ("Bajo: 2 temas con cupo libre"). Chips are checkboxes: [isSelected] is the checked
 * state. An instrument chip toggles its instrument; `Todos` is checked exactly when nothing is
 * selected and tapping it clears the selection.
 */
data class FilterChipUiModel(
    val label: String,
    val count: String,
    val contentDescription: String,
    val isSelected: Boolean,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        data object Toggle : Event
    }
}
