package com.bbbjam.core.data.admin

/**
 * What an admin write did (`apps-script-write-auth`). Every mutation repository returns one of
 * these to its presenter; the presenter decides the copy. No outcome changes the stored passphrase
 * or admin mode.
 */
sealed interface WriteOutcome {
    /** The server ran the write. */
    data object Done : WriteOutcome

    /**
     * The server refused the stored passphrase (`invalid_passphrase`, or `passphrase_not_set`), or
     * no passphrase is stored on this device, in which case nothing was sent. The device stays in
     * admin mode and keeps the stored passphrase (user decision W3): the controls stay drawn and
     * every write fails until the admin taps "Salir del modo admin" on Info and logs in again with
     * the current passphrase.
     */
    data object AccessRefused : WriteOutcome

    /** The request did not complete: no network, a timeout, a dropped connection. */
    data object Offline : WriteOutcome

    /**
     * The write could not be attempted now: too many failed guesses (`rate_limited`), another write
     * in progress (`busy`), a deployment without the action, no URL configured, an HTML page or any
     * answer that is not the contract. Trying again later may work.
     */
    data object Unavailable : WriteOutcome

    /** The server refused the write for a reason of its own, such as a validation error. */
    data class Rejected(val code: String) : WriteOutcome
}
