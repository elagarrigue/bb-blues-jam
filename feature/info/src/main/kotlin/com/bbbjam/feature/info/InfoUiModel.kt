package com.bbbjam.feature.info

import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel

/** Everything the Info screen draws, as plain values: the copy, the links, the link error, the admin line. */
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

        /** Open the admin login (drawn only while logged out). */
        data object AdminEntryTapped : Event

        /** Leave admin mode (drawn only while logged in). No confirmation (A4). */
        data object LogOut : Event
    }
}

data class InfoSectionUiModel(val title: String, val body: String) : UiModel

/** [openLabel] is the accessibility action label for the row ("Abrir Instagram"). */
data class InfoLinkUiModel(val link: SocialLink, val label: String, val destination: String, val openLabel: String) :
    UiModel

/**
 * The discreet admin line at the end of Info (D-15): the entry while logged out, the admin mode and
 * its logout while logged in. It swaps text in place; nothing else on Info moves.
 */
sealed interface AdminEntryUiModel : UiModel {
    /** [label] opens the login ([InfoUiModel.Event.AdminEntryTapped]). */
    data class LoggedOut(val label: String) : AdminEntryUiModel

    /** [status] says admin mode is on; [logoutLabel] leaves it ([InfoUiModel.Event.LogOut]). */
    data class LoggedIn(val status: String, val logoutLabel: String) : AdminEntryUiModel
}
