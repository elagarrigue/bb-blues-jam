package com.bbbjam.feature.info

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bbbjam.core.ui.link.ExternalLinkOpener
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.Presenter

/**
 * Presents the Info screen. The content is static; the state is the link error and whether the
 * admin notice shows. `admin-passphrase-login` replaces [InfoUiModel.Event.AdminEntryTapped]'s
 * handling with opening the login (D-15).
 */
class InfoPresenter(private val linkOpener: ExternalLinkOpener) : Presenter<InfoUiModel, Unit> {
    @Composable
    override fun present(params: Unit): InfoUiModel {
        var linkError by remember { mutableStateOf<String?>(null) }
        var adminNoticeShown by remember { mutableStateOf(false) }

        return InfoUiModel(
            title = InfoCopy.TITLE,
            tagline = InfoCopy.TAGLINE,
            sections = SECTIONS,
            linksTitle = InfoCopy.LINKS_TITLE,
            links = LINKS,
            linkError = linkError,
            adminEntry = AdminEntryUiModel(
                label = InfoCopy.ADMIN_ENTRY,
                notice = if (adminNoticeShown) InfoCopy.ADMIN_NOT_ENABLED else null,
            ),
            events = EventHandler { event ->
                when (event) {
                    is InfoUiModel.Event.OpenLink ->
                        linkError = if (linkOpener.open(event.link.url)) null else InfoCopy.LINK_ERROR

                    InfoUiModel.Event.DismissLinkError -> linkError = null

                    InfoUiModel.Event.AdminEntryTapped -> adminNoticeShown = true
                }
            },
        )
    }

    private companion object {
        val SECTIONS = listOf(
            InfoSectionUiModel(InfoCopy.ORGANIZER_TITLE, InfoCopy.ORGANIZER_BODY),
            InfoSectionUiModel(InfoCopy.JAM_TITLE, InfoCopy.JAM_BODY),
            InfoSectionUiModel(InfoCopy.HIDEAWAY_TITLE, InfoCopy.HIDEAWAY_BODY),
            InfoSectionUiModel(InfoCopy.JOIN_TITLE, InfoCopy.JOIN_BODY),
        )

        val LINKS = listOf(
            InfoLinkUiModel(
                SocialLink.INSTAGRAM,
                InfoCopy.INSTAGRAM_LABEL,
                InfoCopy.INSTAGRAM_DESTINATION,
                InfoCopy.INSTAGRAM_OPEN,
            ),
            InfoLinkUiModel(
                SocialLink.YOUTUBE,
                InfoCopy.YOUTUBE_LABEL,
                InfoCopy.YOUTUBE_DESTINATION,
                InfoCopy.YOUTUBE_OPEN,
            ),
            InfoLinkUiModel(
                SocialLink.LINKTREE,
                InfoCopy.LINKTREE_LABEL,
                InfoCopy.LINKTREE_DESTINATION,
                InfoCopy.LINKTREE_OPEN,
            ),
        )
    }
}
