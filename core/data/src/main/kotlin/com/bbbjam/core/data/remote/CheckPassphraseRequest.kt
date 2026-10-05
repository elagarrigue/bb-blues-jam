package com.bbbjam.core.data.remote

import kotlinx.serialization.Serializable

/**
 * The `checkPassphrase` POST body (`docs/apps-script-api.md`). Encoded with kotlinx-serialization,
 * never by string concatenation, so quotes and backslashes in a passphrase are escaped.
 */
@Serializable
internal data class CheckPassphraseRequest(val action: String, val passphrase: String) {
    /** Never prints the passphrase. */
    override fun toString(): String = "CheckPassphraseRequest(action=$action)"

    companion object {
        const val ACTION = "checkPassphrase"

        fun encode(passphrase: String): String =
            AppsScriptEnvelope.json.encodeToString(serializer(), CheckPassphraseRequest(ACTION, passphrase))
    }
}
