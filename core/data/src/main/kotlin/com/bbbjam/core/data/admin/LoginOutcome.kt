package com.bbbjam.core.data.admin

/** What [AdminSession.logIn] did. Only [Success] stores anything. */
sealed interface LoginOutcome {
    /** The server accepted the passphrase; it is stored and admin mode is on. */
    data object Success : LoginOutcome

    /** The server rejected the passphrase (`invalid_passphrase`), or it was blank. */
    data object WrongPassphrase : LoginOutcome

    /** The request did not complete: no network, a timeout, a dropped connection. */
    data object Offline : LoginOutcome

    /**
     * The passphrase could not be checked: no URL configured, a deployment without the POST
     * action (an HTML page), no passphrase set in the Sheet, any other answer, or a local storage
     * failure.
     */
    data object Unavailable : LoginOutcome
}
