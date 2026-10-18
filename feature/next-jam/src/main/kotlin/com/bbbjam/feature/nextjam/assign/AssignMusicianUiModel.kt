package com.bbbjam.feature.nextjam.assign

import com.bbbjam.core.model.Instrument
import com.bbbjam.core.ui.nav.BackUiModel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.state.EmptyStateUiModel
import com.bbbjam.core.ui.text.UiText

internal sealed interface AssignMusicianUiModel : UiModel {
    val back: BackUiModel

    data class Loading(override val back: BackUiModel) : AssignMusicianUiModel
    data class Gone(override val back: BackUiModel, val empty: EmptyStateUiModel) : AssignMusicianUiModel
    data class Content(
        override val back: BackUiModel,
        val title: UiText,
        val songTitle: String,
        val slotLabel: UiText,
        val instrument: Instrument,
        val ordinal: Int,
        val name: String,
        val nameLabel: UiText,
        val validationMessage: UiText?,
        val currentSection: SuggestionSectionUiModel,
        val pastSection: SuggestionSectionUiModel,
        val submitLabel: UiText,
        val submitEnabled: Boolean,
        val events: EventHandler<Event>,
    ) : AssignMusicianUiModel {
        sealed interface Event : UiEvent {
            data class NameChanged(val value: String) : Event
            data object Submit : Event
        }
    }
}

internal data class SuggestionSectionUiModel(
    val label: UiText,
    val emptyMessage: UiText?,
    val rows: List<SuggestionRowUiModel>,
) : UiModel

internal data class SuggestionRowUiModel(val name: String, val clickLabel: UiText, val events: EventHandler<Event>) :
    UiModel {
    sealed interface Event : UiEvent {
        data object Select : Event
    }
}
