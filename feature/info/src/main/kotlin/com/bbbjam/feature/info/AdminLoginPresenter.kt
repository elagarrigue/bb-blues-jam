package com.bbbjam.feature.info

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.data.admin.LoginOutcome
import com.bbbjam.core.ui.nav.backUiModel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.Presenter
import kotlinx.coroutines.launch

/**
 * Presents the admin login (`admin-passphrase-login`; design prompt screen 7). The check and the
 * storage are [AdminSession.logIn]'s (D-13): this presenter only holds the field and the phase.
 *
 * The passphrase and the masking live in plain `remember`, never `rememberSaveable`, so the secret
 * never enters the saved-state Bundle; a rotation clears the field. Callbacks are read through
 * `rememberUpdatedState`, so an earlier model's handler calls the current one. The state objects
 * are created once, so any earlier handler writes through the same state. A change that touches two
 * of them is one snapshot, so no model ever shows half of it.
 */
class AdminLoginPresenter(private val adminSession: AdminSession) :
    Presenter<AdminLoginUiModel, AdminLoginPresenter.Params> {

    /** [onBack] closes the login; [onLoggedIn] runs once the server accepted the passphrase. */
    data class Params(val onBack: () -> Unit = {}, val onLoggedIn: () -> Unit = {})

    private sealed interface Phase {
        data object Idle : Phase

        data object Verifying : Phase

        data class Failed(val message: String) : Phase
    }

    @Composable
    override fun present(params: Params): AdminLoginUiModel {
        val scope = rememberCoroutineScope()
        val currentOnBack by rememberUpdatedState(params.onBack)
        val currentOnLoggedIn by rememberUpdatedState(params.onLoggedIn)
        var passphrase by remember { mutableStateOf("") }
        var masked by remember { mutableStateOf(true) }
        var phase by remember { mutableStateOf<Phase>(Phase.Idle) }

        val verifying = phase == Phase.Verifying
        return AdminLoginUiModel(
            title = AdminLoginCopy.TITLE,
            fieldLabel = AdminLoginCopy.FIELD_LABEL,
            passphrase = passphrase,
            masked = masked,
            visibilityToggleLabel = if (masked) AdminLoginCopy.SHOW else AdminLoginCopy.HIDE,
            submitLabel = if (verifying) AdminLoginCopy.VERIFYING else AdminLoginCopy.SUBMIT,
            submitEnabled = !verifying && passphrase.isNotBlank(),
            isVerifying = verifying,
            error = (phase as? Phase.Failed)?.message,
            back = backUiModel { currentOnBack() },
            events = EventHandler { event ->
                when (event) {
                    is AdminLoginUiModel.Event.PassphraseChanged -> if (phase != Phase.Verifying) {
                        Snapshot.withMutableSnapshot {
                            passphrase = event.text
                            phase = Phase.Idle
                        }
                    }

                    AdminLoginUiModel.Event.ToggleVisibility -> masked = !masked

                    AdminLoginUiModel.Event.Submit -> if (phase != Phase.Verifying && passphrase.isNotBlank()) {
                        phase = Phase.Verifying
                        val submitted = passphrase
                        scope.launch {
                            when (val outcome = adminSession.logIn(submitted)) {
                                LoginOutcome.Success -> {
                                    // Nothing secret stays in the composition once it is stored.
                                    Snapshot.withMutableSnapshot {
                                        passphrase = ""
                                        phase = Phase.Idle
                                    }
                                    currentOnLoggedIn()
                                }

                                else -> phase = Phase.Failed(outcome.message())
                            }
                        }
                    }

                    AdminLoginUiModel.Event.Back -> currentOnBack()
                }
            },
        )
    }

    private fun LoginOutcome.message(): String = when (this) {
        LoginOutcome.WrongPassphrase -> AdminLoginCopy.WRONG
        LoginOutcome.Offline -> AdminLoginCopy.OFFLINE
        LoginOutcome.Unavailable, LoginOutcome.Success -> AdminLoginCopy.UNAVAILABLE
    }
}
