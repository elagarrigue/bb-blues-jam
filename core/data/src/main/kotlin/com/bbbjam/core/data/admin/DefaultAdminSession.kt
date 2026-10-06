package com.bbbjam.core.data.admin

import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.remote.AppsScriptEnvelope
import com.bbbjam.core.data.remote.AppsScriptPostTransport
import com.bbbjam.core.data.remote.CheckPassphraseRequest
import com.bbbjam.core.data.remote.Decoded
import com.bbbjam.core.data.remote.ServiceCodes
import com.bbbjam.core.data.remote.TransportResult
import java.io.IOException
import kotlinx.coroutines.flow.Flow

/**
 * [AdminSession] over the `checkPassphrase` POST action and [AdminCredentialStore]. The passphrase is
 * stored only after the server answered `ok`; it is never logged, and it travels only in the POST
 * body. No re-check on start: a rotated passphrase is caught by the first write.
 */
internal class DefaultAdminSession(
    private val transport: AppsScriptPostTransport,
    private val store: AdminCredentialStore,
) : AdminSession {

    override fun observeIsAdmin(): Flow<Boolean> = store.observeIsAdmin()

    override suspend fun logIn(passphrase: String): LoginOutcome {
        val trimmed = passphrase.trim()
        val outcome = if (trimmed.isEmpty()) LoginOutcome.WrongPassphrase else check(trimmed)
        return if (outcome == LoginOutcome.Success) save(trimmed) else outcome
    }

    /** What the server says about [passphrase]; nothing is stored here. */
    private suspend fun check(passphrase: String): LoginOutcome =
        when (val result = transport.post(CheckPassphraseRequest.encode(passphrase))) {
            is TransportResult.Failed -> result.failure.toOutcome()

            is TransportResult.Body -> when (val decoded = AppsScriptEnvelope.decodeOk(result.text)) {
                is Decoded.Ok -> LoginOutcome.Success
                is Decoded.Failed -> decoded.failure.toOutcome()
            }
        }

    /** Stores the accepted passphrase; a file that cannot be written leaves admin mode off. */
    private suspend fun save(passphrase: String): LoginOutcome = try {
        store.save(passphrase)
        LoginOutcome.Success
    } catch (ignored: IOException) {
        LoginOutcome.Unavailable
    }

    /** A file that cannot be written keeps admin mode on, visibly; the user can tap again. */
    override suspend fun logOut() {
        try {
            store.clear()
        } catch (ignored: IOException) {
            // Nothing changed, and Info still shows admin mode.
        }
    }

    private fun DataFailure.toOutcome(): LoginOutcome = when {
        this == DataFailure.Offline -> LoginOutcome.Offline
        this is DataFailure.Service && code == ServiceCodes.INVALID_PASSPHRASE -> LoginOutcome.WrongPassphrase
        else -> LoginOutcome.Unavailable
    }
}
