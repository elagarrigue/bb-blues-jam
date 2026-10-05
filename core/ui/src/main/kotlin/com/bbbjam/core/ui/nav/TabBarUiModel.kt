package com.bbbjam.core.ui.nav

import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel

/** The bottom bar (`bottom-navigation`): its tabs in bar order, exactly one of them selected. */
data class TabBarUiModel(val tabs: List<TabUiModel>) : UiModel

/**
 * One tab of the bottom bar: [label] is drawn under the [icon] and names the item for a screen
 * reader; [events] receives [Event.Select]. Where a tab goes is the caller's (`:app` binds it to
 * navigation); nothing here knows the navigation library.
 */
data class TabUiModel(val label: String, val icon: TabIcon, val selected: Boolean, val events: EventHandler<Event>) :
    UiModel {
    sealed interface Event : UiEvent {
        /** Show this tab. Navigation only, never a write. */
        data object Select : Event
    }
}

/**
 * The tab icons (user decision I1, 5 October 2026), mapped to vectors inside `:core:ui` because
 * nothing else depends on the icons library.
 */
enum class TabIcon {
    /** Próxima jam: the setlist. */
    SETLIST,

    /** Anteriores: the calendar of past jams. */
    ARCHIVE,

    /** Info. */
    INFO,
}
