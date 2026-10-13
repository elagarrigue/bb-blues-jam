package com.bbbjam.core.data.assistant

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
