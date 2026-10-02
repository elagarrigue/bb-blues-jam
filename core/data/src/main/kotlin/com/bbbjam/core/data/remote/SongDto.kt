package com.bbbjam.core.data.remote

import kotlinx.serialization.Serializable

/**
 * One song of `GET ?resource=catalog`, exactly as the contract sends it: every key present, each a
 * trimmed string or null (`docs/apps-script-api.md`). No defaults, so a missing key fails the whole
 * response: that is a script bug, not admin data. Interpreting the values is [CatalogMapper]'s job.
 */
@Serializable
internal data class SongDto(
    val id: String?,
    val title: String?,
    val artist: String?,
    val defaultKey: String?,
    val tempo: String?,
    val tags: String?,
    val difficulty: String?,
    val songsterrId: String?,
)
