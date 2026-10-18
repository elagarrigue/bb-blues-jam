package com.bbbjam.core.assistant

/** Whether the assistant's gateway can be used right now, from [SetlistAssistant.checkStatus]. */
sealed interface AssistantStatus {
    /** The gateway answered the status read and accepted the key. */
    data object Available : AssistantStatus

    /** The build has no key; no request was made. */
    data object NotConfigured : AssistantStatus

    /** The gateway could not be reached (no network, a connection error). */
    data object Offline : AssistantStatus

    /** The gateway answered with a server error (HTTP 5xx or any other non-2xx) or did not answer in time. */
    data object ServiceDown : AssistantStatus

    /** The gateway refused the key (HTTP 401 or 403). */
    data object Unauthorized : AssistantStatus
}
