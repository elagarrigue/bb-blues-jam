package com.bbbjam.core.data.remote

/**
 * The envelope error codes the client maps to an outcome (`docs/apps-script-api.md`, Errors). Any
 * other code reaches the caller as it is or as a generic failure.
 */
internal object ServiceCodes {
    const val INVALID_PASSPHRASE = "invalid_passphrase"
    const val PASSPHRASE_NOT_SET = "passphrase_not_set"
    const val RATE_LIMITED = "rate_limited"
    const val BUSY = "busy"
    const val UNKNOWN_ACTION = "unknown_action"
}
