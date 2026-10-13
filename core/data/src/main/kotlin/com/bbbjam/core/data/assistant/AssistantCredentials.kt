package com.bbbjam.core.data.assistant

/**
 * The DevExpert Inference key, built into the app from the git-ignored `local.properties` (D-22: the
 * risk of a key in the APK is accepted). Blank means not configured. [toString] never prints the key,
 * so the credentials can be logged safely.
 */
class AssistantCredentials private constructor(internal val key: String) {

    /** True when a non-blank key was given. */
    val isConfigured: Boolean
        get() = key.isNotEmpty()

    override fun toString(): String =
        if (isConfigured) "AssistantCredentials(configured)" else "AssistantCredentials(not configured)"

    companion object {
        /** The credentials for [raw], trimmed; not configured when it is blank. */
        fun of(raw: String): AssistantCredentials = AssistantCredentials(raw.trim())
    }
}
