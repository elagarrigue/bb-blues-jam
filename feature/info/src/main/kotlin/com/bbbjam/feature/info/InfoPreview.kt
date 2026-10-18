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
private fun InfoScreenPreview() {
    val session = object : AdminSession {
        override fun observeIsAdmin(): Flow<Boolean> = flowOf(true)

        override suspend fun logIn(passphrase: String): LoginOutcome = LoginOutcome.Success

        override suspend fun logOut() = Unit
    }
    BluesJamTheme { InfoScreen(onOpenAdminLogin = {}, presenter = InfoPresenter({ true }, session)) }
}
