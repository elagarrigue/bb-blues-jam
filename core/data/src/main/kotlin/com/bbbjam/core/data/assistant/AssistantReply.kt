package com.bbbjam.core.data.assistant

import com.bbbjam.core.model.Key
import com.bbbjam.core.model.SongId

/** What one request to the assistant produced. Nothing here is a Koog, Ktor or serialization type. */
sealed interface AssistantReply {
    /** No `devexpert.key` was built in: no request was made (D-22). */
    data object NotConfigured : AssistantReply

    /**
     * A catalog-validated proposal (D-14). [songs] are in the model's order and only catalog songs, with
     * the catalog's title, artist and key; [rejectedSongIds] lists, in order, every id the model gave
     * that was not a catalog song, malformed, or a repeat. [questions] may be non-empty with [songs] empty.
     */
    data class Proposal(
        val songs: List<ProposedSong>,
        val message: String,
        val questions: List<String>,
        val rejectedSongIds: List<String>,
    ) : AssistantReply

    /** The request did not produce a proposal. */
    data class Failed(val failure: AssistantFailure) : AssistantReply
}

/** One catalog song in a proposal. [title], [artist] and [key] come from the catalog; [reason] from the model. */
data class ProposedSong(val songId: SongId, val title: String, val artist: String, val key: Key, val reason: String)

/** Why a request produced no proposal. */
sealed interface AssistantFailure {
    /** The gateway could not be reached. */
    data object Offline : AssistantFailure

    /** The gateway refused the key (HTTP 401 or 403). */
    data object Unauthorized : AssistantFailure

    /** Any other non-2xx answer. */
    data class Unavailable(val status: Int) : AssistantFailure

    /** The answer was not a valid proposal; nothing partial is returned. */
    data object InvalidResponse : AssistantFailure

    /** The model kept calling tools past the iteration cap. */
    data object TooManySteps : AssistantFailure

    /** The catalog cache is empty, so there is nothing to propose from. */
    data object CatalogEmpty : AssistantFailure
}
