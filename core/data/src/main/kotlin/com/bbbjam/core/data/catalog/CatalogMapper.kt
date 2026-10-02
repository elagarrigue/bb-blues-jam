package com.bbbjam.core.data.catalog

import com.bbbjam.core.data.remote.SongDto
import com.bbbjam.core.model.Difficulty
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Song
import com.bbbjam.core.model.SongId
import com.bbbjam.core.model.Tempo

/** The songs a catalog response maps to, in response order, and what was left out. */
internal data class MappedCatalog(
    val songs: List<Song>,
    val rejected: List<RejectedSong>,
    val dropped: List<DroppedFields>,
)

/**
 * Turns the `catalog` route's songs into [Song]s under the catalog Mapper rules of
 * `docs/sheet-schema.md`. Pure: no I/O, no clock.
 *
 * Every cell is trimmed again and empty means null; the endpoint already trims, but the mapper does
 * not rely on it. Enum values match exactly, with no case or accent folding. A row with an issue in
 * a required field is rejected and the rest are kept; an issue in tempo, difficulty or
 * `songsterrId` drops only that field (Q2). After the per-row checks, every valid row sharing an id
 * is rejected (Q1).
 */
internal object CatalogMapper {
    private val TEMPOS = mapOf("lento" to Tempo.SLOW, "medio" to Tempo.MEDIUM, "rápido" to Tempo.FAST)
    private val DIFFICULTIES = mapOf(
        "fácil" to Difficulty.EASY,
        "media" to Difficulty.MEDIUM,
        "difícil" to Difficulty.HARD,
    )
    private val DIGITS = Regex("[0-9]+")

    fun map(rows: List<SongDto>): MappedCatalog {
        val checked = rows.mapIndexed { position, row -> checkRow(position + 1, row) }
        val idCounts = checked.mapNotNull { it.song?.id }.groupingBy { it }.eachCount()
        val songs = mutableListOf<Song>()
        val rejected = mutableListOf<RejectedSong>()
        val dropped = mutableListOf<DroppedFields>()
        checked.forEach { row ->
            val song = row.song
            when {
                song == null -> rejected += RejectedSong(row.index, row.id, row.issues)

                idCounts.getValue(song.id) > 1 ->
                    rejected +=
                        RejectedSong(row.index, row.id, row.issues + SongIssue.DuplicateId)

                else -> {
                    songs += song
                    if (row.issues.isNotEmpty()) dropped += DroppedFields(row.index, song.id.value, row.issues)
                }
            }
        }
        return MappedCatalog(songs, rejected, dropped)
    }

    /** One row after the per-row checks: the song when no issue rejects it, plus every issue found. */
    private class CheckedRow(val index: Int, val id: String?, val song: Song?, val issues: List<SongIssue>)

    private fun checkRow(index: Int, row: SongDto): CheckedRow {
        val issues = mutableListOf<SongIssue>()
        val rawId = row.id.cell()
        val id = rawId?.let(SongId::parseOrNull)
        when {
            rawId == null -> issues += SongIssue.MissingId
            id == null -> issues += SongIssue.InvalidId
        }
        val title = row.title.cell()
        if (title == null) issues += SongIssue.MissingTitle
        val artist = row.artist.cell()
        if (artist == null) issues += SongIssue.MissingArtist
        val rawKey = row.defaultKey.cell()
        val key = rawKey?.let(Key::parseOrNull)
        when {
            rawKey == null -> issues += SongIssue.MissingDefaultKey
            key == null -> issues += SongIssue.InvalidDefaultKey
        }
        val tempo = optionalField(row.tempo.cell(), TEMPOS::get, SongIssue.InvalidTempo, issues)
        val difficulty = optionalField(row.difficulty.cell(), DIFFICULTIES::get, SongIssue.InvalidDifficulty, issues)
        val songsterrId =
            optionalField(row.songsterrId.cell(), ::parseSongsterrId, SongIssue.InvalidSongsterrId, issues)
        // A required field is null exactly when it recorded a rejecting issue.
        val song = if (issues.none { it.rejectsSong }) {
            Song(
                id = checkNotNull(id),
                title = checkNotNull(title),
                artist = checkNotNull(artist),
                defaultKey = checkNotNull(key),
                tempo = tempo,
                tags = parseTags(row.tags.cell()),
                difficulty = difficulty,
                songsterrId = songsterrId,
            )
        } else {
            null
        }
        return CheckedRow(index, rawId, song, issues)
    }

    /** The parsed value of an optional cell; null when empty, or when unparsable, recording [issue]. */
    private fun <T : Any> optionalField(
        cell: String?,
        parse: (String) -> T?,
        issue: SongIssue,
        issues: MutableList<SongIssue>,
    ): T? {
        if (cell == null) return null
        return parse(cell) ?: run {
            issues += issue
            null
        }
    }

    /** Decimal digits only (`"12.5"` and `"1e3"` are not ids) that fit a [Long]. */
    private fun parseSongsterrId(text: String): Long? = if (DIGITS.matches(text)) text.toLongOrNull() else null

    /** Split on `,`, trim each tag, drop empty tags, keep the order (duplicates kept). */
    private fun parseTags(cell: String?): List<String> =
        cell?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    private fun String?.cell(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
