package com.bbbjam.core.ui.filter

import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.text.UiText

/**
 * The musician filter block (DESIGN.md "Filter chip"): a [heading], then either the [pickLabel]
 * button (nothing chosen) or the chosen name as a chip ([selectedName]) with [clearLabel]; both open
 * the picker whose rows are [options]. [summary] ("Quedan 3 de 13 temas con Juan") or [noResults]
 * with [noResultsClearLabel] follow a chosen name; never both set.
 */
data class MusicianFilterUiModel(
    val heading: UiText,
    val selectedName: String?,
    val pickLabel: UiText,
    val clearLabel: UiText,
    val pickerTitle: UiText,
    val allLabel: UiText,
    val options: List<MusicianOptionUiModel>,
    val summary: UiText?,
    val noResults: UiText?,
    val noResultsClearLabel: UiText,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        /** "Quitar filtro" and "Todos los músicos": drop the chosen musician. */
        data object Clear : Event

        /** "Ver todos los temas": drop the musician, and the instruments when they contributed. */
        data object ClearAll : Event
    }
}

/** One picker row: [label] ("Juan, 3 temas") for [name]; [isSelected] when it is the chosen one. */
data class MusicianOptionUiModel(
    val name: String,
    val label: UiText,
    val isSelected: Boolean,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        data object Select : Event
    }
}

/** What the musician filter counted: [total] songs, [withMusician] involve the chosen name, [shown] remain. */
data class MusicianFilterCounts(val total: Int, val withMusician: Int, val shown: Int)
