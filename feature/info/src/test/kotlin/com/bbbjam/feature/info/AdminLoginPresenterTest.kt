package com.bbbjam.feature.info

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.bbbjam.core.data.admin.LoginOutcome
import com.bbbjam.core.ui.nav.BackUiModel
import com.bbbjam.core.ui.presenter.EventHandler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminLoginPresenterTest {
    private val session = FakeAdminSession()

    // Written out from the approved copy table (A5), not from AdminLoginCopy, so a copy change fails here.
    private fun expected(
        passphrase: String = "",
        masked: Boolean = true,
        verifying: Boolean = false,
        error: String? = null,
    ) = AdminLoginUiModel(
        title = "Entrar como admin",
        fieldLabel = "Frase de acceso",
        passphrase = passphrase,
        masked = masked,
        visibilityToggleLabel = if (masked) "Mostrar" else "Ocultar",
        submitLabel = if (verifying) "Verificando…" else "Entrar",
        submitEnabled = !verifying && passphrase.isNotBlank(),
        isVerifying = verifying,
        error = error,
        back = BackUiModel("Volver", EventHandler {}),
        events = EventHandler {},
    )

    private fun flow(params: AdminLoginPresenter.Params = AdminLoginPresenter.Params()) =
        moleculeFlow(RecompositionMode.Immediate) { AdminLoginPresenter(session).present(params) }

    @Test
    fun `the first model is an empty masked field with Entrar disabled`() = runTest {
        flow().test {
            assertEquals(expected(), awaitItem())
        }
    }

    @Test
    fun `a blank field keeps Entrar disabled and Submit sends nothing`() = runTest {
        flow().test {
            val first = awaitItem()
            first.events(AdminLoginUiModel.Event.PassphraseChanged("   "))
            val blank = awaitItem()
            assertEquals(expected(passphrase = "   "), blank)
            assertFalse(blank.submitEnabled)

            blank.events(AdminLoginUiModel.Event.Submit)
            expectNoEvents()
        }
        assertEquals(emptyList<String>(), session.logIns)
    }

    @Test
    fun `a wrong passphrase goes Verifying, then the error with the text kept, and typing clears it`() = runTest {
        flow().test {
            val first = awaitItem()
            first.events(AdminLoginUiModel.Event.PassphraseChanged(WRONG))
            assertEquals(expected(passphrase = WRONG), awaitItem())

            first.events(AdminLoginUiModel.Event.Submit)
            assertEquals(expected(passphrase = WRONG, verifying = true), awaitItem())

            session.answer.complete(LoginOutcome.WrongPassphrase)
            assertEquals(expected(passphrase = WRONG, error = "La frase de acceso no es correcta."), awaitItem())

            first.events(AdminLoginUiModel.Event.PassphraseChanged("$WRONG!"))
            assertEquals(expected(passphrase = "$WRONG!"), awaitItem())
        }
        assertEquals(listOf(WRONG), session.logIns)
        assertFalse(session.isAdmin.value)
    }

    @Test
    fun `offline and unavailable show their copy`() = runTest {
        flow().test {
            val first = awaitItem()
            first.events(AdminLoginUiModel.Event.PassphraseChanged(WRONG))
            awaitItem()

            first.events(AdminLoginUiModel.Event.Submit)
            awaitItem()
            session.answer.complete(LoginOutcome.Offline)
            assertEquals(
                expected(passphrase = WRONG, error = "No hay conexión. Para entrar como admin necesitás internet."),
                awaitItem(),
            )

            session.answer = CompletableDeferred()
            first.events(AdminLoginUiModel.Event.Submit)
            assertEquals(expected(passphrase = WRONG, verifying = true), awaitItem())
            session.answer.complete(LoginOutcome.Unavailable)
            assertEquals(
                expected(
                    passphrase = WRONG,
                    error = "No se pudo verificar la frase de acceso. Probá de nuevo en un rato.",
                ),
                awaitItem(),
            )
        }
        assertEquals(listOf(WRONG, WRONG), session.logIns)
    }

    @Test
    fun `success calls onLoggedIn once, a double Submit sends one login, and the field is cleared`() = runTest {
        var loggedIn = 0
        flow(AdminLoginPresenter.Params(onLoggedIn = { loggedIn++ })).test {
            val first = awaitItem()
            first.events(AdminLoginUiModel.Event.PassphraseChanged(" $TEST_VALUE "))
            awaitItem()

            first.events(AdminLoginUiModel.Event.Submit)
            first.events(AdminLoginUiModel.Event.Submit)
            val verifying = awaitItem()
            assertTrue(verifying.isVerifying)
            assertEquals("Verificando…", verifying.submitLabel)
            assertFalse(verifying.submitEnabled)

            // Typing while the server is asked changes nothing.
            verifying.events(AdminLoginUiModel.Event.PassphraseChanged("other"))
            expectNoEvents()
            assertEquals(0, loggedIn)

            session.answer.complete(LoginOutcome.Success)
            assertEquals(expected(), awaitItem())
        }
        assertEquals(1, loggedIn)
        assertEquals(listOf(" $TEST_VALUE "), session.logIns)
        assertTrue(session.isAdmin.value)
    }

    @Test
    fun `ToggleVisibility flips the masking and the toggle label`() = runTest {
        flow().test {
            val first = awaitItem()
            first.events(AdminLoginUiModel.Event.ToggleVisibility)
            assertEquals(expected(masked = false), awaitItem())
            first.events(AdminLoginUiModel.Event.ToggleVisibility)
            assertEquals(expected(masked = true), awaitItem())
        }
    }

    @Test
    fun `Back and the back control call the current onBack, not the first one`() = runTest {
        val calls = mutableListOf<String>()
        var params by mutableStateOf(AdminLoginPresenter.Params(onBack = { calls += "first" }))
        moleculeFlow(RecompositionMode.Immediate) { AdminLoginPresenter(session).present(params) }.test {
            val first = awaitItem()
            params = AdminLoginPresenter.Params(onBack = { calls += "second" })
            awaitItem()

            first.events(AdminLoginUiModel.Event.Back)
            first.back.events(BackUiModel.Event.Back)
        }
        assertEquals(listOf("second", "second"), calls)
        assertEquals(emptyList<String>(), session.logIns)
    }

    @Test
    fun `toString never prints the passphrase`() = runTest {
        flow().test {
            val first = awaitItem()
            first.events(AdminLoginUiModel.Event.PassphraseChanged(TEST_VALUE))
            val typed = awaitItem()
            assertFalse(typed.toString().contains(TEST_VALUE))
            assertFalse(AdminLoginUiModel.Event.PassphraseChanged(TEST_VALUE).toString().contains(TEST_VALUE))
        }
    }

    private companion object {
        const val WRONG = "definitely-wrong"
        const val TEST_VALUE = "not-a-real-passphrase"
    }
}
