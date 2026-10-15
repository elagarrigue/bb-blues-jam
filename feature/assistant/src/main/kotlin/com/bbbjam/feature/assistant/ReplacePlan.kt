package com.bbbjam.feature.assistant

import com.bbbjam.core.data.assistant.ProposedSong
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SongId
import java.time.LocalDate

/**
 * What replacing the upcoming jam's list with a proposal would do (`assistant-proposal-targets`, R1 and
 * R5): a client-side diff of existing actions, so kept songs keep their row, key and musicians. Pure.
 */
sealed interface ReplacePlan {
    /** The snapshot is not read yet: nothing is drawn. */
    data object Unknown : ReplacePlan

    /** The list cannot be replaced, for [reason]. */
    data class Blocked(val reason: Reason) : ReplacePlan {
        enum class Reason { NO_UPCOMING, UNREADABLE, REPEATED_IDS, ALREADY_EQUAL }
    }

    /**
     * [toAdd] are proposal songs not listed (proposal order), [toRemove] listed songs not in the proposal
     * (list order), [kept] the rest. [lostAssignments] counts the filled slots and extras of [toRemove].
     * [target] is the proposal's order (and [proposed] its songs); [current] the ids as listed now.
     */
    data class Ready(
        val jamDate: LocalDate,
        val status: JamStatus,
        val toAdd: List<ProposedSong>,
        val toRemove: List<JamSong>,
        val kept: List<JamSong>,
        val lostAssignments: Int,
        val existingCount: Int,
        val target: List<SongId>,
        val current: List<SongId>,
        val proposed: List<ProposedSong>,
    ) : ReplacePlan
}

/** One reorder call: move [songId] to the absolute 1-based [toPosition]. */
data class SongMove(val songId: SongId, val toPosition: Int)

/** The plan to make the upcoming jam of [snapshot] list exactly [songs], in order. */
fun replacePlan(songs: List<ProposedSong>, snapshot: JamsSnapshot?): ReplacePlan {
    val jam = snapshot?.upcoming
    val listed = jam?.setlist as? Setlist.Available
    return when {
        snapshot == null -> ReplacePlan.Unknown

        jam == null -> ReplacePlan.Blocked(ReplacePlan.Blocked.Reason.NO_UPCOMING)

        listed == null -> ReplacePlan.Blocked(ReplacePlan.Blocked.Reason.UNREADABLE)

        else -> {
            val proposed = songs.distinctBy { it.songId }
            val target = proposed.map { it.songId }
            val current = listed.songs.map { it.songId }
            when {
                current.size != current.toSet().size ->
                    ReplacePlan.Blocked(ReplacePlan.Blocked.Reason.REPEATED_IDS)

                current == target -> ReplacePlan.Blocked(ReplacePlan.Blocked.Reason.ALREADY_EQUAL)

                else -> {
                    val wanted = target.toSet()
                    val (kept, toRemove) = listed.songs.partition { it.songId in wanted }
                    val toAdd = proposed.filter { it.songId !in current.toSet() }
                    val lost = toRemove.sumOf { it.assignments() }
                    ReplacePlan.Ready(
                        jam.date, jam.status, toAdd, toRemove, kept, lost, current.size, target, current, proposed,
                    )
                }
            }
        }
    }
}

/**
 * The moves that turn [current] into [target] (R2): for each index of [target], when the song there is
 * not already in place, move it to that 1-based position, applying the move to a copy as the server's
 * `moveSetlistRow_` does. At most `target.size - 1`. Songs of [current] outside [target] end up after it.
 */
fun reorderMoves(current: List<SongId>, target: List<SongId>): List<SongMove> {
    val simulated = current.toMutableList()
    val moves = mutableListOf<SongMove>()
    for ((index, id) in target.withIndex()) {
        if (simulated.getOrNull(index) == id) continue
        simulated.remove(id)
        simulated.add(index.coerceAtMost(simulated.size), id)
        moves += SongMove(id, index + 1)
    }
    return moves
}

/** The musicians a song would lose: its filled slots plus its extra participants (R6). */
private fun JamSong.assignments() = lineup.slots.count { it.isFilled } + extraParticipants.size
