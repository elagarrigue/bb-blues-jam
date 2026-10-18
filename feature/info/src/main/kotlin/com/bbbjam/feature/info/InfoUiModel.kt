package com.bbbjam.feature.info

import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.text.UiText

/** Everything the Info screen draws, as plain values: the copy, the links, the link error. */
internal data class InfoUiModel(
    val title: UiText,
    val tagline: UiText,
    val sections: List<InfoSectionUiModel>,
    val linksTitle: UiText,
    val links: List<InfoLinkUiModel>,
    /** Shown when a link could not be opened; null otherwise. */
    val linkError: UiText?,
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

internal data class InfoSectionUiModel(val title: UiText, val body: UiText) : UiModel

/** [openLabel] is the accessibility action label for the row ("Abrir Instagram"). */
internal data class InfoLinkUiModel(
    val link: SocialLink,
    val label: UiText,
    /** A handle or address, not copy. */
    val destination: String,
    val openLabel: UiText,
) : UiModel
