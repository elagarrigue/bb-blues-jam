package com.bbbjam.navigation

import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * The app's navigation routes, as strings (`song-detail-screen`, R1). They are built and parsed only
 * here, so features never see a route or the navigation library (D-03).
 *
 * No destination declares a deeplink and the manifest has no intent filter (`bottom-navigation`,
 * user decision D1, 5 October 2026): the deeplink scheme is deferred to `action-contract-registry`.
 * D-13 is met by repository functions registered in the `:app` action registry; an exported URI
 * entry point needs its own validation design.
 */
internal object AppRoutes {
    /**
     * The outer host's tabs shell (`TabsShell`): an inner host with the three tab routes below and
     * the bottom bar. The song detail, the past jam detail and the admin login are beside it in the
     * outer host, full screen over the bar.
     */
    const val TABS = "tabs"

    /** The inner host's start tab, Próxima jam. */
    const val NEXT_JAM = "next-jam"

    /** The Anteriores tab. */
    const val PAST_JAMS = "past-jams"

    /** The Info tab. */
    const val INFO = "info"

    /** The admin-only Asistente tab (`assistant-admin-tab`); registered always, drawn only for the admin. */
    const val ASSISTANT = "assistant"

    /**
     * The admin login (`admin-passphrase-login`, A7): full screen in the outer host, opened from
     * Info. No argument: the passphrase never enters a route or a saved Bundle.
     */
    const val ADMIN_LOGIN = "admin-login"

    const val JAM_DATE = "jamDate"
    const val POSITION = "position"
    const val SONG_ID = "songId"

    /**
     * The song detail pattern, `song/{jamDate}/{songId}/{position}`. Since
     * `song-detail-replaces-row-expansion` (P2) the song is named by id, so a move or a removal
     * confirmed while the detail is open never switches it to another song; [POSITION] only picks
     * between rows a hand-edited tab gives the same id.
     */
    const val SONG_DETAIL = "song/{$JAM_DATE}/{$SONG_ID}/{$POSITION}"

    /** `song/2026-10-31/crossroads/2`: the ISO date, the song id (`[a-z0-9-]`, path-safe), the position. */
    fun songDetail(jamDate: LocalDate, songId: SongId, position: Int): String =
        "song/$jamDate/${songId.value}/$position"

    /** The past jam detail pattern, `pastJam/{jamDate}` (`past-jam-detail`). */
    const val PAST_JAM_DETAIL = "pastJam/{$JAM_DATE}"

    /** `pastJam/2026-07-25`: `LocalDate.toString()` is ISO-8601, never locale-dependent. */
    fun pastJamDetail(jamDate: LocalDate): String = "pastJam/$jamDate"

    /**
     * The admin's catalog picker (`admin-add-song-to-setlist`), `addSong/{jamDate}`: full screen in
     * the outer host, opened from Próxima jam's "Agregar tema".
     */
    const val ADD_SONG = "addSong/{$JAM_DATE}"

    /** `addSong/2026-10-31`: `LocalDate.toString()` is ISO-8601, never locale-dependent. */
    fun addSong(jamDate: LocalDate): String = "addSong/$jamDate"

    /** The picker's jam date, or null when it is missing or not ISO. */
    fun parseAddSong(jamDate: String?): LocalDate? = jamDate?.toIsoDateOrNull()

    const val INSTRUMENT = "instrument"
    const val ORDINAL = "ordinal"

    /**
     * The admin's key picker (`admin-set-key`), `setKey/{jamDate}/{songId}`: full screen in the outer
     * host, opened from the song detail's "Cambiar tonalidad". The song is named by id, never by
     * position: a removal renumbers positions (R1).
     */
    const val SET_KEY = "setKey/{$JAM_DATE}/{$SONG_ID}"

    /** `setKey/2026-10-31/crossroads`: the ISO date, then the song id (`[a-z0-9-]`, path-safe). */
    fun setKey(jamDate: LocalDate, songId: SongId): String = "setKey/$jamDate/${songId.value}"

    /** The picker's arguments, or null when the date is missing or not ISO, or the id is missing or invalid. */
    fun parseSetKey(jamDate: String?, songId: String?): SetKeyArgs? {
        val id = songId?.let { SongId.parseOrNull(it) } ?: return null
        return jamDate?.toIsoDateOrNull()?.let { SetKeyArgs(it, id) }
    }

    /** The fixed open slot assignment screen, named by date/song id/instrument ordinal. */
    const val ASSIGN_SLOT = "assignSlot/{$JAM_DATE}/{$SONG_ID}/{$INSTRUMENT}/{$ORDINAL}"

    fun assignSlot(jamDate: LocalDate, songId: SongId, instrument: Instrument, position: SlotPosition) =
        "assignSlot/$jamDate/${songId.value}/${instrument.name.lowercase(Locale.ROOT)}/${position.value}"

    fun parseAssignSlot(jamDate: String?, songId: String?, instrument: String?, ordinal: Int?): AssignSlotArgs? {
        val date = jamDate?.toIsoDateOrNull()
        val id = songId?.let(SongId::parseOrNull)
        val type = instrument?.let { value ->
            Instrument.entries.firstOrNull { it.name.lowercase(Locale.ROOT) == value }
        }
        val position = ordinal?.takeIf { it >= 1 }?.let(::SlotPosition)
        return date?.let { parsedDate ->
            id?.let { parsedId ->
                type?.let { parsedInstrument ->
                    position?.let { parsedPosition ->
                        AssignSlotArgs(parsedDate, parsedId, parsedInstrument, parsedPosition)
                    }
                }
            }
        }
    }

    /** The past jam detail's date, or null when it is missing or not ISO. */
    fun parsePastJamDetail(jamDate: String?): LocalDate? = jamDate?.toIsoDateOrNull()

    /**
     * The song detail's arguments, or null when the date is missing or not ISO, the id is missing or
     * invalid, or the position is missing or below 1 (setlist positions start at 1).
     */
    fun parseSongDetail(jamDate: String?, songId: String?, position: Int?): SongDetailArgs? {
        val id = songId?.let { SongId.parseOrNull(it) }
        val date = jamDate?.toIsoDateOrNull()
        val validPosition = position?.takeIf { it >= 1 }
        return if (id == null || date == null || validPosition == null) {
            null
        } else {
            SongDetailArgs(date, id, validPosition)
        }
    }

    private fun String.toIsoDateOrNull(): LocalDate? = try {
        LocalDate.parse(this)
    } catch (_: DateTimeParseException) {
        null
    }
}

/** A song of a jam, as the song detail route names it: by id, [position] only for duplicate ids. */
internal data class SongDetailArgs(val jamDate: LocalDate, val songId: SongId, val position: Int)

/** A song of a jam, as the key picker route names it. */
internal data class SetKeyArgs(val jamDate: LocalDate, val songId: SongId)

internal data class AssignSlotArgs(
    val jamDate: LocalDate,
    val songId: SongId,
    val instrument: Instrument,
    val position: SlotPosition,
)
