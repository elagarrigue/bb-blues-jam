package com.bbbjam.debug

import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.data.admin.LoginOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Debug-only decorator (debug-admin-session): [observeIsAdmin] emits true while a forced state is
 * on, otherwise the [real] value. The forced state starts on, so a debug build with
 * `bluesjam.debugAdmin=true` opens in admin mode without a login.
 *
 * It never stores, sends or knows the passphrase, and it authorizes nothing: the flag only decides
 * which controls are drawn, and Apps Script still checks every write. [logOut] turns the forced
 * state off and calls the real logout, so the logout flow can be checked on a device (forced admin
 * returns on the next process start). [logIn] delegates unchanged.
 */
internal class DebugAdminSession(private val real: AdminSession) : AdminSession {
    private val forced = MutableStateFlow(true)

    override fun observeIsAdmin(): Flow<Boolean> =
        combine(forced, real.observeIsAdmin()) { isForced, isRealAdmin -> isForced || isRealAdmin }
            .distinctUntilChanged()

    override suspend fun logIn(passphrase: String): LoginOutcome = real.logIn(passphrase)

    override suspend fun logOut() {
        forced.value = false
        real.logOut()
    }
}
