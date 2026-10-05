package com.bbbjam.feature.info

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.bbbjam.core.ui.link.ExternalLinkOpener
import com.bbbjam.core.ui.presenter.EventHandler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

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

    private val loggedOut = AdminEntryUiModel.LoggedOut("Entrar como admin")
    private val loggedIn = AdminEntryUiModel.LoggedIn("Modo admin activo", "Salir del modo admin")

    private fun presenter(opener: ExternalLinkOpener) = InfoPresenter(opener, session)

    private fun flow(opener: ExternalLinkOpener, params: InfoPresenter.Params = InfoPresenter.Params()) =
        moleculeFlow(RecompositionMode.Immediate) { presenter(opener).present(params) }

    // Written out from the approved copy tables (docs/specs/info-screen.md, and A5 of
    // admin-passphrase-login for the admin line), not from InfoCopy, so a copy change fails here.
    private fun expected(linkError: String? = null, adminEntry: AdminEntryUiModel = loggedOut) = InfoUiModel(
        title = "Bahía Blanca Blues",
        tagline = "Comunidad de amantes del blues",
        sections = listOf(
            InfoSectionUiModel(
                "Quién organiza",
                "La jam la organiza Bahía Blanca Blues, una comunidad de amantes del blues que también arma " +
                    "festivales, conciertos y programas de radio.",
            ),
            InfoSectionUiModel(
                "La jam",
                "Una jam de blues en Bahía Blanca, una vez por mes. El lugar puede cambiar de una jam a otra: " +
                    "lo ves junto a la fecha de cada una.",
            ),
            InfoSectionUiModel(
                "Hideaway",
                "Hideaway es el programa de radio de Bahía Blanca Blues. Sale los martes de 20 a 22.",
            ),
            InfoSectionUiModel(
                "Cómo sumarte",
                "Si tocás, venite a la jam y anotate ahí: la organización te suma a un tema. " +
                    "No necesitás cuenta ni registrarte en la app.",
            ),
        ),
        linksTitle = "Redes",
        links = listOf(
            InfoLinkUiModel(SocialLink.INSTAGRAM, "Instagram", "@bahiablancablues", "Abrir Instagram"),
            InfoLinkUiModel(SocialLink.YOUTUBE, "YouTube", "youtube.com", "Abrir YouTube"),
            InfoLinkUiModel(SocialLink.LINKTREE, "Linktree", "linktr.ee/bahiablancablues", "Abrir Linktree"),
        ),
        linkError = linkError,
        adminEntry = adminEntry,
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
            assertEquals(expected(linkError = "No se pudo abrir el enlace."), awaitItem())

            first.events(InfoUiModel.Event.DismissLinkError)
            assertEquals(expected(linkError = null), awaitItem())
        }
        assertEquals(listOf("https://www.youtube.com/channel/UCayS6srPr0FQ2FF4XoEZ-8w"), opener.opened)
    }

    @Test
    fun `the admin entry opens the login through the current callback and changes nothing else`() = runTest {
        val opener = FakeLinkOpener(result = true)
        val calls = mutableListOf<String>()
        var params by mutableStateOf(InfoPresenter.Params { calls += "first" })
        moleculeFlow(RecompositionMode.Immediate) { presenter(opener).present(params) }.test {
            val first = awaitItem()
            assertEquals(expected(), first)

            params = InfoPresenter.Params { calls += "second" }
            awaitItem()
            first.events(InfoUiModel.Event.AdminEntryTapped)
            expectNoEvents()
        }
        assertEquals(listOf("second"), calls)
        assertEquals(emptyList<String>(), opener.opened)
        assertEquals(0, session.logOuts)
    }

    @Test
    fun `the admin line follows the session, logged out to logged in and back on LogOut`() = runTest {
        flow(FakeLinkOpener(true)).test {
            val first = awaitItem()
            assertEquals(loggedOut, first.adminEntry)

            session.isAdmin.value = true
            val admin = awaitItem()
            assertEquals(expected(adminEntry = loggedIn), admin)

            admin.events(InfoUiModel.Event.LogOut)
            assertEquals(expected(adminEntry = loggedOut), awaitItem())
        }
        assertEquals(1, session.logOuts)
        assertEquals(emptyList<String>(), session.logIns)
    }

    @Test
    fun `an admin device opens Info in admin mode`() = runTest {
        val admin = FakeAdminSession(initiallyAdmin = true)
        val opener = FakeLinkOpener(true)
        moleculeFlow(RecompositionMode.Immediate) {
            InfoPresenter(opener, admin).present(InfoPresenter.Params())
        }.test {
            // collectAsState starts from false; the stored state arrives with the flow's first value.
            var model = awaitItem()
            if (model.adminEntry == loggedOut) model = awaitItem()
            assertEquals(expected(adminEntry = loggedIn), model)
        }
    }
}
