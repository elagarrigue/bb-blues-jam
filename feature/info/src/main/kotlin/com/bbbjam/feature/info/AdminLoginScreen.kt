package com.bbbjam.feature.info

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.data.admin.LoginOutcome
import com.bbbjam.core.ui.nav.BackButton
import com.bbbjam.core.ui.theme.BluesJamTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.compose.koinInject

/**
 * The admin login (design prompt screen 7): a back button, the title, one passphrase field with a
 * Mostrar/Ocultar toggle, the error under it, and "Entrar". It draws [AdminLoginUiModel] and
 * forwards events; the presenter decides. `imePadding` keeps the button above the keyboard.
 */
@Composable
fun AdminLoginScreen(
    onBack: () -> Unit,
    onLoggedIn: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    presenter: AdminLoginPresenter = koinInject(),
) {
    AdminLoginContent(
        model = presenter.present(AdminLoginPresenter.Params(onBack = onBack, onLoggedIn = onLoggedIn)),
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

@Composable
private fun AdminLoginContent(model: AdminLoginUiModel, modifier: Modifier, contentPadding: PaddingValues) {
    val spacing = BluesJamTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BluesJamTheme.colors.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        BackButton(model.back)
        Text(
            text = model.title,
            style = BluesJamTheme.typography.h1,
            color = BluesJamTheme.colors.text,
            modifier = Modifier.semantics { heading() },
        )
        PassphraseField(model)
        SubmitButton(model)
    }
}

@Composable
private fun PassphraseField(model: AdminLoginUiModel) {
    val style = AdminLoginDefaults.field()
    OutlinedTextField(
        value = model.passphrase,
        onValueChange = { model.events(AdminLoginUiModel.Event.PassphraseChanged(it)) },
        modifier = Modifier.fillMaxWidth(),
        readOnly = model.isVerifying,
        textStyle = BluesJamTheme.typography.body,
        label = { Text(text = model.fieldLabel) },
        trailingIcon = { VisibilityToggle(model) },
        supportingText = model.error?.let { error ->
            {
                Text(text = error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        },
        isError = model.error != null,
        visualTransformation = if (model.masked) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { model.events(AdminLoginUiModel.Event.Submit) }),
        singleLine = true,
        shape = BluesJamTheme.shapes.md,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = style.text,
            unfocusedTextColor = style.text,
            errorTextColor = style.text,
            focusedContainerColor = style.container,
            unfocusedContainerColor = style.container,
            errorContainerColor = style.container,
            cursorColor = style.cursor,
            errorCursorColor = style.error,
            focusedBorderColor = style.focusedBorder,
            unfocusedBorderColor = style.unfocusedBorder,
            errorBorderColor = style.error,
            focusedLabelColor = style.label,
            unfocusedLabelColor = style.label,
            errorLabelColor = style.error,
            focusedTrailingIconColor = style.label,
            unfocusedTrailingIconColor = style.label,
            errorTrailingIconColor = style.label,
            errorSupportingTextColor = style.error,
        ),
    )
}

@Composable
private fun VisibilityToggle(model: AdminLoginUiModel) {
    val target = LocalMinimumInteractiveComponentSize.current
    Box(
        modifier = Modifier
            .sizeIn(minWidth = target, minHeight = target)
            .clip(BluesJamTheme.shapes.md)
            .clickable(role = Role.Button) { model.events(AdminLoginUiModel.Event.ToggleVisibility) }
            .padding(horizontal = BluesJamTheme.spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = model.visibilityToggleLabel,
            style = BluesJamTheme.typography.caption,
            color = AdminLoginDefaults.toggle(),
        )
    }
}

@Composable
private fun SubmitButton(model: AdminLoginUiModel) {
    val shape = BluesJamTheme.shapes.md
    val style = AdminLoginDefaults.submitButton(enabled = model.submitEnabled)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .clip(shape)
            .background(style.fill, shape)
            .clickable(enabled = model.submitEnabled, role = Role.Button) {
                model.events(AdminLoginUiModel.Event.Submit)
            }
            .padding(horizontal = BluesJamTheme.spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = model.submitLabel, style = BluesJamTheme.typography.body, color = style.content)
    }
}

@Preview
@Composable
private fun AdminLoginScreenPreview() {
    val session = object : AdminSession {
        override fun observeIsAdmin(): Flow<Boolean> = flowOf(false)

        override suspend fun logIn(passphrase: String): LoginOutcome = LoginOutcome.WrongPassphrase

        override suspend fun logOut() = Unit
    }
    BluesJamTheme { AdminLoginScreen(onBack = {}, onLoggedIn = {}, presenter = AdminLoginPresenter(session)) }
}
