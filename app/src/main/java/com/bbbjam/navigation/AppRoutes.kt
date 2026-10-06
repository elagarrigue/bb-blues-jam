package com.bbbjam.navigation

import com.bbbjam.core.model.SongId
import java.time.LocalDate
import java.time.format.DateTimeParseException

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

    /**
     * The admin login (`admin-passphrase-login`, A7): full screen in the outer host, opened from
     * Info. No argument: the passphrase never enters a route or a saved Bundle.
     */
    const val ADMIN_LOGIN = "admin-login"

    const val JAM_DATE = "jamDate"
    const val POSITION = "position"

    /** The song detail pattern, `song/{jamDate}/{position}`. */
    const val SONG_DETAIL = "song/{$JAM_DATE}/{$POSITION}"

    /** `song/2026-10-31/2`: `LocalDate.toString()` is ISO-8601, never locale-dependent. */
    fun songDetail(jamDate: LocalDate, position: Int): String = "song/$jamDate/$position"

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

    const val SONG_ID = "songId"

    /**
     * The admin's key picker (`admin-set-key`), `setKey/{jamDate}/{songId}`: full screen in the outer
     * host, opened from an expanded row's "Cambiar tonalidad". The song is named by id, never by
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

    /** The past jam detail's date, or null when it is missing or not ISO. */
    fun parsePastJamDetail(jamDate: String?): LocalDate? = jamDate?.toIsoDateOrNull()

    /**
     * The song detail's arguments, or null when the date is missing or not ISO, or the position is
     * missing or below 1 (setlist positions start at 1).
     */
    fun parseSongDetail(jamDate: String?, position: Int?): SongDetailArgs? {
        if (position == null || position < 1) return null
        return jamDate?.toIsoDateOrNull()?.let { SongDetailArgs(it, position) }
    }

    private fun String.toIsoDateOrNull(): LocalDate? = try {
        LocalDate.parse(this)
    } catch (_: DateTimeParseException) {
        null
    }
}

/** A song of a jam, as the song detail route names it. */
internal data class SongDetailArgs(val jamDate: LocalDate, val position: Int)

/** A song of a jam, as the key picker route names it. */
internal data class SetKeyArgs(val jamDate: LocalDate, val songId: SongId)
