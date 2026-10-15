package com.bbbjam.feature.assistant

import com.bbbjam.core.data.assistant.ProposedSong
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import java.time.LocalDate

/**
 * What loading a proposal into the upcoming jam would do, decided from the jams snapshot
 * (`assistant-apply-proposal`, A3 and A4). Pure: no clock, no I/O.
 */
sealed interface ApplyPlan {
    /** The snapshot is not read yet: nothing is drawn, so a blocked note never flashes. */
    data object Unknown : ApplyPlan

    /** Nothing can be added, for [reason]. */
    data class Blocked(val reason: Reason) : ApplyPlan {
        enum class Reason { NO_UPCOMING, UNREADABLE, ALL_PRESENT }
    }

    /**
     * Ready to append [toAdd] after the [existingCount] songs already listed on [jamDate], in
     * proposal order; [skipped] are proposal songs whose id is already in the list.
     */
    data class Ready(
        val jamDate: LocalDate,
        val status: JamStatus,
        val toAdd: List<ProposedSong>,
        val skipped: List<ProposedSong>,
        val existingCount: Int,
    ) : ApplyPlan
}

/**
 * The plan for [songs] against the upcoming jam of [snapshot]. The admin's list is `jam.setlist`: a
 * draft read without the admin read is Withheld and an unreadable one Unavailable, both
 * [ApplyPlan.Blocked.Reason.UNREADABLE]. Duplicates are matched by [ProposedSong.songId].
 */
fun applyPlan(songs: List<ProposedSong>, snapshot: JamsSnapshot?): ApplyPlan =
    if (snapshot == null) ApplyPlan.Unknown else planFor(songs, snapshot.upcoming)

/**
 * The plan for [songs] against the jam of [date], looked up in `upcoming` then `heldBack` (C6: the retry of a
 * create run targets the jam that run created, which may be held back). A date with no such jam is
 * [ApplyPlan.Blocked.Reason.NO_UPCOMING].
 */
fun applyPlanFor(songs: List<ProposedSong>, snapshot: JamsSnapshot?, date: LocalDate): ApplyPlan =
    if (snapshot == null) {
        ApplyPlan.Unknown
    } else {
        planFor(songs, (listOfNotNull(snapshot.upcoming) + snapshot.heldBack).firstOrNull { it.date == date })
    }

private fun planFor(songs: List<ProposedSong>, jam: Jam?): ApplyPlan {
    val listed = jam?.setlist as? Setlist.Available
    return when {
        jam == null -> ApplyPlan.Blocked(ApplyPlan.Blocked.Reason.NO_UPCOMING)

        listed == null -> ApplyPlan.Blocked(ApplyPlan.Blocked.Reason.UNREADABLE)

        else -> {
            val present = listed.songs.mapTo(mutableSetOf()) { it.songId }
            val (skipped, toAdd) = songs.partition { it.songId in present }
            if (toAdd.isEmpty()) {
                ApplyPlan.Blocked(ApplyPlan.Blocked.Reason.ALL_PRESENT)
            } else {
                ApplyPlan.Ready(jam.date, jam.status, toAdd, skipped, listed.songs.size)
            }
        }
    }
}
