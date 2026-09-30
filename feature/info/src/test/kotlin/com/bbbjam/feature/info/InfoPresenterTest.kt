package com.bbbjam.feature.info

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

    // Written out from the approved copy table in docs/specs/info-screen.md, not from InfoCopy, so
    // any change to the shipped copy fails this test.
    private fun expected(linkError: String? = null, adminNotice: String? = null) = InfoUiModel(
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
        adminEntry = AdminEntryUiModel("Entrar como admin", adminNotice),
        events = EventHandler {},
    )

    @Test
    fun `first model holds exactly the approved copy`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { InfoPresenter(FakeLinkOpener(true)).present(Unit) }.test {
            assertEquals(expected(), awaitItem())
        }
    }

    @Test
    fun `each link row opens exactly its url`() = runTest {
        val opener = FakeLinkOpener(result = true)
        moleculeFlow(RecompositionMode.Immediate) { InfoPresenter(opener).present(Unit) }.test {
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
        moleculeFlow(RecompositionMode.Immediate) { InfoPresenter(opener).present(Unit) }.test {
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
    fun `tapping the admin entry shows the not-enabled notice and changes nothing else`() = runTest {
        val opener = FakeLinkOpener(result = true)
        moleculeFlow(RecompositionMode.Immediate) { InfoPresenter(opener).present(Unit) }.test {
            val first = awaitItem()
            assertEquals(null, first.adminEntry.notice)

            first.events(InfoUiModel.Event.AdminEntryTapped)
            assertEquals(expected(adminNotice = "El ingreso de admin todavía no está habilitado."), awaitItem())
        }
        assertEquals(emptyList<String>(), opener.opened)
    }
}
