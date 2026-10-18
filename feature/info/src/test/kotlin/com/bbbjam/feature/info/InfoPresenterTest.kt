package com.bbbjam.feature.info

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.bbbjam.core.data.testing.FakeAdminSession
import com.bbbjam.core.ui.link.ExternalLinkOpener
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.text.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InfoPresenterTest {
    /** Records every URL it is asked to open and answers with [result]. */
    private class FakeLinkOpener(private val result: Boolean) : ExternalLinkOpener {
        val opened = mutableListOf<String>()

        override fun open(url: String): Boolean {
            opened += url
            return result
        }
    }

    private val session = FakeAdminSession()

    private fun presenter(opener: ExternalLinkOpener) = InfoPresenter(opener, session)

    private fun flow(opener: ExternalLinkOpener, params: InfoPresenter.Params = InfoPresenter.Params()) =
        moleculeFlow(RecompositionMode.Immediate) { presenter(opener).present(params) }

    // Written out from the approved copy ids (docs/specs/info-screen.md), so a wrong id fails here.
    private fun expected(linkError: UiText? = null) = InfoUiModel(
        title = UiText.res(R.string.info_title),
        tagline = UiText.res(R.string.info_tagline),
        sections = listOf(
            InfoSectionUiModel(UiText.res(R.string.info_organizer_title), UiText.res(R.string.info_organizer_body)),
            InfoSectionUiModel(UiText.res(R.string.info_jam_title), UiText.res(R.string.info_jam_body)),
            InfoSectionUiModel(UiText.res(R.string.info_hideaway_title), UiText.res(R.string.info_hideaway_body)),
            InfoSectionUiModel(UiText.res(R.string.info_join_title), UiText.res(R.string.info_join_body)),
        ),
        linksTitle = UiText.res(R.string.info_links_title),
        links = listOf(
            InfoLinkUiModel(
                SocialLink.INSTAGRAM,
                UiText.res(R.string.info_instagram_label),
                "@bahiablancablues",
                UiText.res(R.string.info_instagram_open),
            ),
            InfoLinkUiModel(
                SocialLink.YOUTUBE,
                UiText.res(R.string.info_youtube_label),
                "youtube.com",
                UiText.res(R.string.info_youtube_open),
            ),
            InfoLinkUiModel(
                SocialLink.LINKTREE,
                UiText.res(R.string.info_linktree_label),
                "linktr.ee/bahiablancablues",
                UiText.res(R.string.info_linktree_open),
            ),
        ),
        linkError = linkError,
        events = EventHandler {},
    )

    @Test
    fun `first model holds exactly the approved copy`() = runTest {
        flow(FakeLinkOpener(true)).test {
            assertEquals(expected(), awaitItem())
        }
    }

    @Test
    fun `each link row opens exactly its url`() = runTest {
        val opener = FakeLinkOpener(result = true)
        flow(opener).test {
            val model = awaitItem()
            model.links.forEach { model.events(InfoUiModel.Event.OpenLink(it.link)) }
            expectNoEvents()
        }
        assertEquals(
            listOf(
                "https://www.instagram.com/bahiablancablues/",
                "https://www.youtube.com/channel/UCayS6srPr0FQ2FF4XoEZ-8w",
                "https://linktr.ee/bahiablancablues",
            ),
            opener.opened,
        )
    }

    @Test
    fun `a link that cannot be opened shows the error until it is dismissed`() = runTest {
        val opener = FakeLinkOpener(result = false)
        flow(opener).test {
            val first = awaitItem()
            assertEquals(null, first.linkError)

            first.events(InfoUiModel.Event.OpenLink(SocialLink.YOUTUBE))
            assertEquals(expected(linkError = UiText.res(R.string.info_link_error)), awaitItem())

            first.events(InfoUiModel.Event.DismissLinkError)
            assertEquals(expected(linkError = null), awaitItem())
        }
        assertEquals(listOf("https://www.youtube.com/channel/UCayS6srPr0FQ2FF4XoEZ-8w"), opener.opened)
    }

    private val tap = InfoUiModel.Event.LogoTapped

    private fun tapTimes(model: InfoUiModel, times: Int) = repeat(times) { model.events(tap) }

    @Test
    fun `five quick taps open the login through the current callback and nothing else`() = runTest {
        val opener = FakeLinkOpener(result = true)
        val calls = mutableListOf<String>()
        var params by mutableStateOf(InfoPresenter.Params { calls += "first" })
        moleculeFlow(RecompositionMode.Immediate) { presenter(opener).present(params) }.test {
            val first = awaitItem()
            assertEquals(expected(), first)

            params = InfoPresenter.Params { calls += "second" }
            awaitItem()
            repeat(5) {
                first.events(tap)
                advanceTimeBy(1_400)
            }
            expectNoEvents()
        }
        assertEquals(listOf("second"), calls)
        assertEquals(emptyList<String>(), opener.opened)
        assertEquals(0, session.logOuts)
    }

    @Test
    fun `an admin leaves with five taps, never opening the login`() = runTest {
        val admin = FakeAdminSession(isAdmin = true)
        val calls = mutableListOf<String>()
        moleculeFlow(RecompositionMode.Immediate) {
            InfoPresenter(FakeLinkOpener(true), admin).present(InfoPresenter.Params { calls += "login" })
        }.test {
            val model = awaitItem()
            assertEquals(expected(), model)
            advanceUntilIdle()
            tapTimes(model, 5)
            assertEquals(1, admin.logOuts)
            assertEquals(emptyList<String>(), calls)
            assertEquals(false, admin.isAdmin.value)
        }
    }

    @Test
    fun `four taps do nothing`() = runTest {
        val calls = mutableListOf<String>()
        flow(FakeLinkOpener(true), InfoPresenter.Params { calls += "login" }).test {
            val model = awaitItem()
            tapTimes(model, 4)
            advanceTimeBy(5_000)
            expectNoEvents()
        }
        assertEquals(emptyList<String>(), calls)
        assertEquals(0, session.logOuts)
    }

    @Test
    fun `a slow gap resets the count and the late tap starts a new sequence`() = runTest {
        val calls = mutableListOf<String>()
        flow(FakeLinkOpener(true), InfoPresenter.Params { calls += "login" }).test {
            val model = awaitItem()
            tapTimes(model, 2)
            advanceTimeBy(1_600)
            tapTimes(model, 3)
            assertEquals(emptyList<String>(), calls)
            tapTimes(model, 2)
            assertEquals(listOf("login"), calls)
            expectNoEvents()
        }
    }

    @Test
    fun `after an action the count restarts`() = runTest {
        val calls = mutableListOf<String>()
        flow(FakeLinkOpener(true), InfoPresenter.Params { calls += "login" }).test {
            val model = awaitItem()
            tapTimes(model, 5)
            assertEquals(1, calls.size)
            tapTimes(model, 4)
            assertEquals(1, calls.size)
            model.events(tap)
            assertEquals(2, calls.size)
        }
    }

    @Test
    fun `events from a stale model act on the current admin state`() = runTest {
        val calls = mutableListOf<String>()
        flow(FakeLinkOpener(true), InfoPresenter.Params { calls += "login" }).test {
            val stale = awaitItem()
            session.isAdmin.value = true
            advanceUntilIdle()
            tapTimes(stale, 5)
            assertEquals(1, session.logOuts)
            assertEquals(emptyList<String>(), calls)
        }
    }

    @Test
    fun `taps emit no new model`() = runTest {
        flow(FakeLinkOpener(true)).test {
            val model = awaitItem()
            tapTimes(model, 4)
            expectNoEvents()
        }
    }

    @Test
    fun `an admin device shows the same Info`() = runTest {
        val admin = FakeAdminSession(isAdmin = true)
        val opener = FakeLinkOpener(true)
        moleculeFlow(RecompositionMode.Immediate) {
            InfoPresenter(opener, admin).present(InfoPresenter.Params())
        }.test {
            // Info looks the same in both states: no admin text anywhere.
            assertEquals(expected(), awaitItem())
        }
    }
}
