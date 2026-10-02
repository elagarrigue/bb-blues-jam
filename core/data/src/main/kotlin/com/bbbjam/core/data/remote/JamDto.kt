package com.bbbjam.core.data.remote

import kotlinx.serialization.Serializable

/**
 * One jam of `GET ?resource=jams`, exactly as the contract sends it (`docs/apps-script-api.md`,
 * Jam): every key present, each value a trimmed string or null. No defaults, so a missing key fails
 * the whole response: that is a script bug, not admin data. Interpreting the values is
 * [com.bbbjam.core.data.jams.JamsMapper]'s job.
 */
@Serializable
internal data class JamDto(
    val date: String?,
    val startTime: String?,
    val venue: String?,
    val status: String?,
    val setlist: List<SetlistRowDto>?,
    val setlistError: SetlistErrorDto?,
)

/** One row of a jam tab (`docs/apps-script-api.md`, Setlist row). */
@Serializable
internal data class SetlistRowDto(
    val position: String?,
    val songId: String?,
    val title: String?,
    val artist: String?,
    val key: String?,
    val slots: SlotsDto,
    val extraParticipants: String?,
)

/**
 * The seven slot cells, in the column order of `Lineup.DEFAULT_INSTRUMENTS`. Each is null (open),
 * `"-"` (not part of this song's lineup) or a musician's name.
 */
@Serializable
internal data class SlotsDto(
    val guitar1: String?,
    val guitar2: String?,
    val bass: String?,
    val drums: String?,
    val vocals: String?,
    val harmonica: String?,
    val keyboards: String?,
) {
    /** The cells in column order, matching `Lineup.DEFAULT_INSTRUMENTS` index by index. */
    fun cells(): List<String?> = listOf(guitar1, guitar2, bass, drums, vocals, harmonica, keyboards)
}

/** A per-jam problem the script reported; [message] is English and for logs only. */
@Serializable
internal data class SetlistErrorDto(val code: String, val message: String)
