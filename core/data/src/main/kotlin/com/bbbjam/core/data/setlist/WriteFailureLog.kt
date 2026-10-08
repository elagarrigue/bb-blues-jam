package com.bbbjam.core.data.setlist

/** Local sink for safe mutation failure diagnostics; callers must never include credentials/data. */
internal fun interface WriteFailureLog {
    fun write(line: String)
}
