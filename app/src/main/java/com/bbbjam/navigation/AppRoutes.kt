package com.bbbjam.navigation

import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * The app's navigation routes, as strings (`song-detail-screen`, R1). They are built and parsed only
 * here, so features never see a route or the navigation library (D-03). No deeplink uses them yet.
 */
internal object AppRoutes {
    /** The temporary two-tab host (Próxima jam and Info), until `bottom-navigation`. */
    const val TABS = "tabs"

    const val JAM_DATE = "jamDate"
    const val POSITION = "position"

    /** The song detail pattern, `song/{jamDate}/{position}`. */
    const val SONG_DETAIL = "song/{$JAM_DATE}/{$POSITION}"

    /** `song/2026-10-31/2`: `LocalDate.toString()` is ISO-8601, never locale-dependent. */
    fun songDetail(jamDate: LocalDate, position: Int): String = "song/$jamDate/$position"

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
