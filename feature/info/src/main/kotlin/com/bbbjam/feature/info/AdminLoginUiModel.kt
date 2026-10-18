package com.bbbjam.feature.info

import com.bbbjam.core.ui.nav.BackUiModel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.text.UiText

/**
 * Everything the admin login draws. [passphrase] is what the field holds, so the screen stays a pure
 * renderer; it is the one secret in any UiModel, so [toString] never prints it, and the phase 2
 * assistant context must leave it out (`docs/risks-and-open-questions.md`).
 */
internal data class AdminLoginUiModel(
    val title: UiText,
    val fieldLabel: UiText,
    val passphrase: String,
    /** True while the field draws dots instead of the text. */
    val masked: Boolean,
    /** `Mostrar` while masked, `Ocultar` while shown. */
    val visibilityToggleLabel: UiText,
    /** `Entrar`, or `Verificando…` while the server is asked. */
    val submitLabel: UiText,
    val submitEnabled: Boolean,
    val isVerifying: Boolean,
    /** Under the field after a failed attempt; null otherwise. Typing clears it. */
    val error: UiText?,
    val back: BackUiModel,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        data class PassphraseChanged(val text: String) : Event {
            override fun toString(): String = "PassphraseChanged(text=<${text.length} chars>)"
        }

        data object ToggleVisibility : Event

        /** Check the passphrase. Ignored while verifying or when the field is blank. */
        data object Submit : Event

        data object Back : Event
    }

    override fun toString(): String = "AdminLoginUiModel(passphrase=<${passphrase.length} chars>, masked=$masked, " +
        "submitLabel=$submitLabel, submitEnabled=$submitEnabled, isVerifying=$isVerifying, error=$error)"
}
