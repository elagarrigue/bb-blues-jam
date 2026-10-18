package com.bbbjam.feature.info

import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel

/** Everything the Info screen draws, as plain values: the copy, the links, the link error. */
internal data class InfoUiModel(
    val title: String,
    val tagline: String,
    val sections: List<InfoSectionUiModel>,
    val linksTitle: String,
    val links: List<InfoLinkUiModel>,
    /** Shown when a link could not be opened; null otherwise. */
    val linkError: String?,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        data class OpenLink(val link: SocialLink) : Event

        data object DismissLinkError : Event

        /**
         * The hidden admin gesture (D-15): one tap on the Info logo. Five taps, each within 1.5 s of the
         * previous one, open the admin login (musician) or leave admin mode (admin). Info draws nothing for it.
         */
        data object LogoTapped : Event
    }
}

internal data class InfoSectionUiModel(val title: String, val body: String) : UiModel

/** [openLabel] is the accessibility action label for the row ("Abrir Instagram"). */
internal data class InfoLinkUiModel(
    val link: SocialLink,
    val label: String,
    val destination: String,
    val openLabel: String,
) : UiModel
