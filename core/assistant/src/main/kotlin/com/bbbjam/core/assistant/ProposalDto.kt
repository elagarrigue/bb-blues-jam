package com.bbbjam.core.assistant

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

/** The model's final answer. Every field is required (strict schema); validated before anyone sees it. */
@Serializable
@LLMDescription("A proposed setlist for the jam, with a closing message and follow-up questions.")
internal data class ProposalDto(
    @property:LLMDescription("Songs in playing order. Only catalog songs. May be empty when asking questions.")
    val songs: List<ProposedSongDto>,
    @property:LLMDescription("A short closing message for the admin, in Rioplatense Spanish using vos.")
    val message: String,
    @property:LLMDescription("Follow-up questions when the request is ambiguous; empty otherwise.")
    val questions: List<String>,
    @property:LLMDescription(
        "A new jam to create (date, time, venue). Fill it only when the admin asks to create a new jam " +
            "and gave an exact date; otherwise null.",
    )
    val jam: JamProposalDto? = null,
)

/** The jam the model proposes to create. Checked by [JamProposalValidator] before anyone sees it. */
@Serializable
@LLMDescription("A new jam the admin asked to create.")
internal data class JamProposalDto(
    @property:LLMDescription("The jam date as YYYY-MM-DD, exactly as the admin said it.")
    val date: String,
    @property:LLMDescription("The start time as HH:MM, 24 hours. Default: the last jam's time.")
    val time: String,
    @property:LLMDescription("The venue name. Default: the last jam's venue.")
    val venue: String,
)

/** One proposed song. Only [songId] is trusted, and only after it is found in the catalog. */
@Serializable
@LLMDescription("One song of the proposal.")
internal data class ProposedSongDto(
    @property:LLMDescription("The catalog id exactly as the tools return it, for example sweet-little-angel.")
    val songId: String,
    @property:LLMDescription("The song title as listed in the catalog.")
    val title: String,
    @property:LLMDescription("The artist as listed in the catalog.")
    val artist: String,
    @property:LLMDescription("The key as listed in the catalog, for example Bm.")
    val key: String,
    @property:LLMDescription("One short reason in Rioplatense Spanish using vos for placing this song here.")
    val reason: String,
)
