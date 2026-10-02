package com.bbbjam.core.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The Apps Script web app's `/exec` URL (`docs/apps-script-api.md`, Transport). `:app` builds it
 * from `BuildConfig.APPS_SCRIPT_URL`, which comes from the git-ignored `local.properties`. A blank or
 * non-`https` URL means not configured: every read then fails with [DataFailure.NotConfigured]
 * without a request.
 *
 * [toString] never prints the URL, so an endpoint can be logged safely.
 */
class AppsScriptEndpoint private constructor(internal val url: String?) {

    /** True when a usable `https` URL was given. */
    val isConfigured: Boolean
        get() = url != null

    override fun toString(): String =
        if (isConfigured) "AppsScriptEndpoint(configured)" else "AppsScriptEndpoint(not configured)"

    companion object {
        private const val HTTPS = "https"

        /** The endpoint for [raw], trimmed; not configured when it is blank or not an `https` URL. */
        fun of(raw: String): AppsScriptEndpoint {
            val parsed = raw.trim().toHttpUrlOrNull()
            return AppsScriptEndpoint(parsed?.takeIf { it.scheme == HTTPS }?.toString())
        }
    }
}
