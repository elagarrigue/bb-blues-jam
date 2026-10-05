package com.bbbjam.feature.info

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.ui.link.ExternalLinkOpener
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.Presenter
import kotlinx.coroutines.launch

/**
 * Presents the Info screen. The content is static; the state is the link error and the admin line,
 * which follows [AdminSession.observeIsAdmin] (D-15): "Entrar como admin" opens the login through
 * [Params.onOpenAdminLogin] (read through `rememberUpdatedState`), and "Salir del modo admin" calls
 * [AdminSession.logOut] (D-13: the mutation belongs to the repository, not to the screen). The flag
 * only decides what is drawn; it authorizes nothing (D-11).
 */
class InfoPresenter(private val linkOpener: ExternalLinkOpener, private val adminSession: AdminSession) :
    Presenter<InfoUiModel, InfoPresenter.Params> {

    /** [onOpenAdminLogin] opens the admin login (bound by `:app`). */
    data class Params(val onOpenAdminLogin: () -> Unit = {})

    @Composable
    override fun present(params: Params): InfoUiModel {
        val scope = rememberCoroutineScope()
        val currentOnOpenAdminLogin by rememberUpdatedState(params.onOpenAdminLogin)
        var linkError by remember { mutableStateOf<String?>(null) }
        val isAdmin by remember { adminSession.observeIsAdmin() }.collectAsState(initial = false)

        return InfoUiModel(
            title = InfoCopy.TITLE,
            tagline = InfoCopy.TAGLINE,
            sections = SECTIONS,
            linksTitle = InfoCopy.LINKS_TITLE,
            links = LINKS,
            linkError = linkError,
            adminEntry = if (isAdmin) ADMIN_LOGGED_IN else ADMIN_LOGGED_OUT,
            events = EventHandler { event ->
                when (event) {
                    is InfoUiModel.Event.OpenLink ->
                        linkError = if (linkOpener.open(event.link.url)) null else InfoCopy.LINK_ERROR

                    InfoUiModel.Event.DismissLinkError -> linkError = null

                    InfoUiModel.Event.AdminEntryTapped -> currentOnOpenAdminLogin()

                    InfoUiModel.Event.LogOut -> scope.launch { adminSession.logOut() }
                }
            },
        )
    }

    private companion object {
        val ADMIN_LOGGED_OUT = AdminEntryUiModel.LoggedOut(InfoCopy.ADMIN_ENTRY)
        val ADMIN_LOGGED_IN = AdminEntryUiModel.LoggedIn(InfoCopy.ADMIN_ACTIVE, InfoCopy.ADMIN_LOG_OUT)

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
