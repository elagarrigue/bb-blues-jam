package com.bbbjam.feature.nextjam

import com.bbbjam.core.ui.nav.BackUiModel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.state.EmptyStateUiModel

/**
 * The key picker (`admin-set-key`, V1): full screen over the tabs, reached from an expanded admin
 * row's "Cambiar tonalidad". Every state has the back control.
 */
sealed interface SetKeyUiModel : UiModel {
    val back: BackUiModel

    /** The flag or the jams are not read yet: only the back control. */
    data class Loading(override val back: BackUiModel) : SetKeyUiModel

    /** Not admin, or the song is no longer in the upcoming jam's setlist: [empty] says so. */
    data class Gone(override val back: BackUiModel, val empty: EmptyStateUiModel) : SetKeyUiModel

    /**
     * The picker: [title] ("Cambiar tonalidad"), the [songTitle], [currentLabel] ("Tonalidad actual",
     * drawn uppercase) over [currentKey] (read as [currentKeyDescription]; the pending key when a
     * change is still saving, so the picker agrees with the row), then the two [sections].
     */
    data class Content(
        override val back: BackUiModel,
        val title: String,
        val songTitle: String,
        val currentLabel: String,
        val currentKey: String,
        val currentKeyDescription: String,
        val sections: List<KeySectionUiModel>,
    ) : SetKeyUiModel
}

/** One section ([label] "Mayores" or "Menores", drawn uppercase): 12 cells as [rows] of 4. */
data class KeySectionUiModel(val label: String, val rows: List<List<KeyCellUiModel>>) : UiModel

/**
 * One key. [description] is what a screen reader says ("Tonalidad Bb"). The cell equal to the
 * current key [isCurrent]: it shows [currentLabel] ("actual") and is not clickable. [pickLabel] is
 * the click label ("elegir esta tonalidad").
 */
data class KeyCellUiModel(
    val key: String,
    val description: String,
    val isCurrent: Boolean,
    val currentLabel: String?,
    val pickLabel: String,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        /** Set the song's key to this one. A repository call, never a UI-only change (D-13). */
        data object Pick : Event
    }
}
