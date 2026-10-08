package com.bbbjam.feature.nextjam

import com.bbbjam.core.model.Instrument
import com.bbbjam.core.ui.nav.BackUiModel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.state.EmptyStateUiModel

sealed interface AssignMusicianUiModel : UiModel {
    val back: BackUiModel

    data class Loading(override val back: BackUiModel) : AssignMusicianUiModel
    data class Gone(override val back: BackUiModel, val empty: EmptyStateUiModel) : AssignMusicianUiModel
    data class Content(
        override val back: BackUiModel,
        val title: String,
        val songTitle: String,
        val slotLabel: String,
        val instrument: Instrument,
        val ordinal: Int,
        val name: String,
        val nameLabel: String,
        val validationMessage: String?,
        val currentSection: SuggestionSectionUiModel,
        val pastSection: SuggestionSectionUiModel,
        val submitLabel: String,
        val submitEnabled: Boolean,
        val events: EventHandler<Event>,
    ) : AssignMusicianUiModel {
        sealed interface Event : UiEvent {
            data class NameChanged(val value: String) : Event
            data object Submit : Event
        }
    }
}

data class SuggestionSectionUiModel(
    val label: String,
    val emptyMessage: String?,
    val rows: List<SuggestionRowUiModel>,
) : UiModel

data class SuggestionRowUiModel(val name: String, val clickLabel: String, val events: EventHandler<Event>) : UiModel {
    sealed interface Event : UiEvent {
        data object Select : Event
    }
}
