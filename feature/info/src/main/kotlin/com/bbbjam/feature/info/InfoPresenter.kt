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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Presents the Info screen. The content is static; the state is the link error. The admin entry and
 * exit are a hidden gesture (D-15): five [InfoUiModel.Event.LogoTapped], each within [LOGO_TAP_GAP_MILLIS]
 * of the previous one, open the login through [Params.onOpenAdminLogin] (read through
 * `rememberUpdatedState`) when the device is not in admin mode, or call [AdminSession.logOut] when it is
 * (D-13: the mutation belongs to the repository, not to the screen). The flag, read when the fifth tap
 * lands, only decides what happens locally; it authorizes nothing (D-11). The tap count is not
 * snapshot state, so taps never emit a new model.
 */
internal class InfoPresenter(private val linkOpener: ExternalLinkOpener, private val adminSession: AdminSession) :
    Presenter<InfoUiModel, InfoPresenter.Params> {

    /** [onOpenAdminLogin] opens the admin login (bound by `:app`). */
    data class Params(val onOpenAdminLogin: () -> Unit = {})

    @Composable
    override fun present(params: Params): InfoUiModel {
        val scope = rememberCoroutineScope()
        val currentOnOpenAdminLogin by rememberUpdatedState(params.onOpenAdminLogin)
        var linkError by remember { mutableStateOf<String?>(null) }
        val counter = remember { LogoTapCounter() }
        val isAdmin by remember { adminSession.observeIsAdmin() }.collectAsState(initial = false)

        return InfoUiModel(
            title = InfoCopy.TITLE,
            tagline = InfoCopy.TAGLINE,
            sections = SECTIONS,
            linksTitle = InfoCopy.LINKS_TITLE,
            links = LINKS,
            linkError = linkError,
            events = EventHandler { event ->
                when (event) {
                    is InfoUiModel.Event.OpenLink ->
                        linkError = if (linkOpener.open(event.link.url)) null else InfoCopy.LINK_ERROR

                    InfoUiModel.Event.DismissLinkError -> linkError = null

                    InfoUiModel.Event.LogoTapped -> counter.tap(scope) {
                        if (isAdmin) scope.launch { adminSession.logOut() } else currentOnOpenAdminLogin()
                    }
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

private const val LOGO_TAPS_REQUIRED = 5
private const val LOGO_TAP_GAP_MILLIS = 1_500L

/** Counts consecutive logo taps; a gap longer than [LOGO_TAP_GAP_MILLIS] resets it to 0. */
private class LogoTapCounter {
    private var count = 0
    private var reset: Job? = null

    fun tap(scope: CoroutineScope, onComplete: () -> Unit) {
        reset?.cancel()
        count++
        if (count >= LOGO_TAPS_REQUIRED) {
            count = 0
            onComplete()
        } else {
            reset = scope.launch {
                delay(LOGO_TAP_GAP_MILLIS)
                count = 0
            }
        }
    }
}
