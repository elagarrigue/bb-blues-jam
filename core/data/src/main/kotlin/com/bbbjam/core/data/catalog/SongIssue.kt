package com.bbbjam.core.data.catalog

/**
 * A problem the mapper found in one `Catalogo` row (`docs/sheet-schema.md`, Mapper rules).
 *
 * An issue in a required field ([rejectsSong]) drops the whole song. An issue in a field the MVP
 * does not use yet (tempo, difficulty, `songsterrId`; D-20) only drops that field to null and the
 * song is kept (user approval Q2, 1 October 2026). Every issue is reported either way.
 */
enum class SongIssue(val rejectsSong: Boolean) {
    MissingId(rejectsSong = true),
    InvalidId(rejectsSong = true),
    MissingTitle(rejectsSong = true),
    MissingArtist(rejectsSong = true),
    MissingDefaultKey(rejectsSong = true),
    InvalidDefaultKey(rejectsSong = true),
    InvalidTempo(rejectsSong = false),
    InvalidDifficulty(rejectsSong = false),
    InvalidSongsterrId(rejectsSong = false),

    /** The id is shared with another valid row; every copy is rejected (user approval Q1). */
    DuplicateId(rejectsSong = true),
}

/**
 * A row that did not become a [com.bbbjam.core.model.Song]. [index] is 1-based in the response's
 * `songs`, not the Sheet row, because the script skips blank rows. [id] is the trimmed cell, valid or
 * not. [issues] holds every issue of the row, in column order, those that did not reject it included.
 */
data class RejectedSong(val index: Int, val id: String?, val issues: List<SongIssue>)

/** A kept song whose unusable optional fields were dropped to null (user approval Q2). */
data class DroppedFields(val index: Int, val id: String, val issues: List<SongIssue>)
