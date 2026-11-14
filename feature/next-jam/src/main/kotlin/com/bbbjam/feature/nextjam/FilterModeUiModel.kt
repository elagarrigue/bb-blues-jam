package com.bbbjam.feature.nextjam

import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel

internal enum class JamFilterMode { INSTRUMENT, MUSICIAN }

/** Exactly one filtering mode applies, even when both modes have a remembered selection. */
internal data class FilterModeUiModel(
    val selected: JamFilterMode = JamFilterMode.INSTRUMENT,
    val events: EventHandler<Event> = EventHandler {},
) : UiModel {
    data class Event(val mode: JamFilterMode) : UiEvent
}
