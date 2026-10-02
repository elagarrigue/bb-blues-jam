package com.bbbjam.core.data.jams

import com.bbbjam.core.data.remote.JamDto
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SetlistProblem
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

/** The jams a `jams` response maps to, in response order, and what was left out or worth reporting. */
internal data class MappedJams(
    val jams: List<MappedJam>,
    val rejected: List<RejectedJam>,
    val issues: List<SetlistIssue>,
)

/**
 * One kept jam. Its [JamSong][com.bbbjam.core.model.JamSong] titles and artists are the tab's
 * copies; the cache resolves them against the catalog when it is read. [slotColumns] gives, for
 * each position of an available setlist, the 0-based slot column (`Guitarra 1` = 0 … `Teclados`
 * = 6) of each lineup slot, in lineup order: the cache keeps it as the slot's write identity.
 */
internal data class MappedJam(val jam: Jam, val slotColumns: Map<Int, List<Int>>)

/**
 * Turns the `jams` route's rows into [Jam]s under the jam Mapper rules of `docs/sheet-schema.md`.
 * Pure: no I/O and no clock, so splitting upcoming from past is the repository's job.
 *
 * Every cell is trimmed again and empty means null; the endpoint already trims, but the mapper does
 * not rely on it. Values match exactly. A row with any issue is rejected and the rest are kept (the
 * four cells are required, user approval P8). After the per-row checks, every row whose date parses
 * and is shared with another row is rejected, valid or not (P2). Setlists are [SetlistMapper]'s.
 */
internal object JamsMapper {
    private val DATE = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
    private val TIME = Regex("([01][0-9]|2[0-3]):([0-5][0-9])")
    private val STATUSES = mapOf("BORRADOR" to JamStatus.DRAFT, "PUBLICADA" to JamStatus.PUBLISHED)

    /** `setlistError` codes of `docs/apps-script-api.md`; any other code reads as [SetlistProblem.UNKNOWN]. */
    private val PROBLEMS = mapOf(
        "missing_tab" to SetlistProblem.MISSING_TAB,
        "missing_header" to SetlistProblem.INVALID_TAB,
        "duplicate_header" to SetlistProblem.INVALID_TAB,
    )

    fun map(rows: List<JamDto>): MappedJams {
        val checked = rows.mapIndexed { position, row -> checkRow(position + 1, row) }
        val dateCounts = checked.mapNotNull { it.date }.groupingBy { it }.eachCount()
        val jams = mutableListOf<MappedJam>()
        val rejected = mutableListOf<RejectedJam>()
        val issues = mutableListOf<SetlistIssue>()
        checked.forEach { row ->
            val shared = row.date != null && dateCounts.getValue(row.date) > 1
            val rowIssues = if (shared) row.issues + JamIssue.DuplicateDate else row.issues
            if (rowIssues.isEmpty()) {
                val setlist = mapSetlist(row)
                jams += MappedJam(
                    Jam(
                        date = checkNotNull(row.date),
                        startTime = checkNotNull(row.startTime),
                        venue = checkNotNull(row.venue),
                        status = checkNotNull(row.status),
                        setlist = setlist.setlist,
                    ),
                    setlist.slotColumns,
                )
                issues += setlist.issues
            } else {
                rejected += RejectedJam(row.index, row.rawDate, rowIssues)
            }
        }
        return MappedJams(jams, rejected, issues)
    }

    /** One `Jams` row after the per-row checks; a field is null exactly when it recorded an issue. */
    private class CheckedRow(
        val index: Int,
        val date: LocalDate?,
        val startTime: LocalTime?,
        val venue: String?,
        val status: JamStatus?,
        val issues: List<JamIssue>,
        val dto: JamDto,
    ) {
        val rawDate: String?
            get() = dto.date.trimmedCell()
    }

    private fun checkRow(index: Int, row: JamDto): CheckedRow {
        val issues = mutableListOf<JamIssue>()
        val rawDate = row.date.trimmedCell()
        val date = rawDate?.let(::parseDate)
        when {
            rawDate == null -> issues += JamIssue.MissingDate
            date == null -> issues += JamIssue.InvalidDate
        }
        val rawTime = row.startTime.trimmedCell()
        val startTime = rawTime?.let(::parseTime)
        when {
            rawTime == null -> issues += JamIssue.MissingStartTime
            startTime == null -> issues += JamIssue.InvalidStartTime
        }
        val venue = row.venue.trimmedCell()
        if (venue == null) issues += JamIssue.MissingVenue
        val rawStatus = row.status.trimmedCell()
        val status = rawStatus?.let(STATUSES::get)
        when {
            rawStatus == null -> issues += JamIssue.MissingStatus
            status == null -> issues += JamIssue.InvalidStatus
        }
        return CheckedRow(index, date, startTime, venue, status, issues, row)
    }

    /**
     * A draft is always [Setlist.Withheld]; a setlist or error sent with it breaks the contract and
     * is ignored (fail closed). A published jam's `setlistError` makes it [Setlist.Unavailable]; with
     * neither a setlist nor an error it is unavailable for an unknown reason.
     */
    private fun mapSetlist(row: CheckedRow): MappedSetlist {
        val date = checkNotNull(row.date)
        val error = row.dto.setlistError
        val rows = row.dto.setlist
        return when {
            row.status == JamStatus.DRAFT -> MappedSetlist(
                Setlist.Withheld,
                issues = listOfNotNull(
                    SetlistIssue.DraftSetlistIgnored(date).takeIf { rows != null || error != null },
                ),
            )

            error != null -> MappedSetlist(
                Setlist.Unavailable(PROBLEMS[error.code] ?: SetlistProblem.UNKNOWN),
                issues = listOf(SetlistIssue.SetlistError(date, error.code)),
            )

            rows == null -> MappedSetlist(
                Setlist.Unavailable(SetlistProblem.UNKNOWN),
                issues = listOf(SetlistIssue.SetlistMissing(date)),
            )

            else -> SetlistMapper.map(date, rows)
        }
    }

    /** `YYYY-MM-DD` and a real calendar date (`2026-02-30` is not). */
    private fun parseDate(text: String): LocalDate? = if (DATE.matches(text)) {
        try {
            LocalDate.parse(text)
        } catch (ignored: DateTimeParseException) {
            null
        }
    } else {
        null
    }

    /** `HH:MM`, 24 hours, zero-padded (`9:00` and `24:00` are not times). */
    private fun parseTime(text: String): LocalTime? =
        TIME.matchEntire(text)?.let { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
}

/** The trimmed cell, or null when it is null or empty after trimming. */
internal fun String?.trimmedCell(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
