package com.bbbjam.core.ui.nav

import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel

/**
 * The visible back control of a detail screen: [label] is what a screen reader says ("Volver"), and
 * [events] receives [Event.Back]. Where back goes is the caller's: the presenter forwards it to a
 * plain callback, and `:app` binds that to navigation. Nothing here knows the navigation library.
 */
data class BackUiModel(val label: String, val events: EventHandler<Event>) : UiModel {
    sealed interface Event : UiEvent {
        /** Leave this screen. Never a write. */
        data object Back : Event
    }
}

/** The back control with its approved label; [onBack] runs on [BackUiModel.Event.Back]. */
fun backUiModel(onBack: () -> Unit): BackUiModel = BackUiModel(
    label = NavCopy.BACK,
    events = EventHandler { event ->
        when (event) {
            BackUiModel.Event.Back -> onBack()
        }
    },
)
