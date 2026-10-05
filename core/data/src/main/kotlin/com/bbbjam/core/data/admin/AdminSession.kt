package com.bbbjam.core.data.admin

import kotlinx.coroutines.flow.Flow

/**
 * The admin mode of this device (D-11, D-15). Every presenter that draws admin controls reads
 * [observeIsAdmin]; only the login and logout change it.
 *
 * The flag only decides which controls are drawn. It authorizes nothing: Apps Script checks the
 * passphrase on every write (`apps-script-write-auth`).
 */
interface AdminSession {
    /** True while a passphrase the server accepted is stored on this device. Emits on every change. */
    fun observeIsAdmin(): Flow<Boolean>

    /**
     * Asks Apps Script whether [passphrase] (trimmed) is the admin passphrase, and stores it only
     * when the server says it is. A blank passphrase is [LoginOutcome.WrongPassphrase] with no
     * request.
     */
    suspend fun logIn(passphrase: String): LoginOutcome

    /** Forgets the stored passphrase. No request is made. */
    suspend fun logOut()
}
