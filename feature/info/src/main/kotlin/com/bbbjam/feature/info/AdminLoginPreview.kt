package com.bbbjam.feature.info

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.data.admin.LoginOutcome
import com.bbbjam.core.ui.theme.BluesJamTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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
