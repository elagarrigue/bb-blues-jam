package com.bbbjam.feature.info

import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.data.admin.LoginOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * An [AdminSession] for presenter tests. Each [logIn] waits for [answer] to be completed, so a test
 * can observe the Verifying state, and records what it was asked; success flips [isAdmin] as the
 * real session does.
 */
internal class FakeAdminSession(initiallyAdmin: Boolean = false) : AdminSession {
    val isAdmin = MutableStateFlow(initiallyAdmin)
    val logIns = mutableListOf<String>()
    var logOuts = 0
        private set
    var answer = CompletableDeferred<LoginOutcome>()

    override fun observeIsAdmin(): Flow<Boolean> = isAdmin

    override suspend fun logIn(passphrase: String): LoginOutcome {
        logIns += passphrase
        val outcome = answer.await()
        if (outcome == LoginOutcome.Success) isAdmin.value = true
        return outcome
    }

    override suspend fun logOut() {
        logOuts++
        isAdmin.value = false
    }
}
