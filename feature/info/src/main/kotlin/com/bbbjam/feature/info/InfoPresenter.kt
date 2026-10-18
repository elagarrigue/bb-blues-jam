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
import com.bbbjam.core.ui.text.UiText
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
        var linkError by remember { mutableStateOf<UiText?>(null) }
        val counter = remember { LogoTapCounter() }
        val isAdmin by remember { adminSession.observeIsAdmin() }.collectAsState(initial = false)

        return InfoUiModel(
            title = UiText.res(R.string.info_title),
            tagline = UiText.res(R.string.info_tagline),
            sections = SECTIONS,
            linksTitle = UiText.res(R.string.info_links_title),
            links = LINKS,
            linkError = linkError,
            events = EventHandler { event ->
                when (event) {
                    is InfoUiModel.Event.OpenLink ->
                        linkError = if (linkOpener.open(event.link.url)) null else UiText.res(R.string.info_link_error)

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
            section(R.string.info_organizer_title, R.string.info_organizer_body),
            section(R.string.info_jam_title, R.string.info_jam_body),
            section(R.string.info_hideaway_title, R.string.info_hideaway_body),
            section(R.string.info_join_title, R.string.info_join_body),
        )

        val LINKS = listOf(
            link(
                SocialLink.INSTAGRAM,
                R.string.info_instagram_label,
                "@bahiablancablues",
                R.string.info_instagram_open,
            ),
            link(SocialLink.YOUTUBE, R.string.info_youtube_label, "youtube.com", R.string.info_youtube_open),
            link(
                SocialLink.LINKTREE,
                R.string.info_linktree_label,
                "linktr.ee/bahiablancablues",
                R.string.info_linktree_open,
            ),
        )

        fun section(title: Int, body: Int) = InfoSectionUiModel(UiText.res(title), UiText.res(body))

        fun link(link: SocialLink, label: Int, destination: String, open: Int) =
            InfoLinkUiModel(link, UiText.res(label), destination, UiText.res(open))
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
