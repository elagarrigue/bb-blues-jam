package com.bbbjam.feature.info

import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel

/** Everything the Info screen draws, as plain values: the copy, the links and the two notices. */
data class InfoUiModel(
    val title: String,
    val tagline: String,
    val sections: List<InfoSectionUiModel>,
    val linksTitle: String,
    val links: List<InfoLinkUiModel>,
    /** Shown when a link could not be opened; null otherwise. */
    val linkError: String?,
    val adminEntry: AdminEntryUiModel,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        data class OpenLink(val link: SocialLink) : Event

        data object DismissLinkError : Event

        data object AdminEntryTapped : Event
    }
}

data class InfoSectionUiModel(val title: String, val body: String) : UiModel

/** [openLabel] is the accessibility action label for the row ("Abrir Instagram"). */
data class InfoLinkUiModel(val link: SocialLink, val label: String, val destination: String, val openLabel: String) :
    UiModel

/** [notice] is null until the entry is tapped. */
data class AdminEntryUiModel(val label: String, val notice: String?) : UiModel
