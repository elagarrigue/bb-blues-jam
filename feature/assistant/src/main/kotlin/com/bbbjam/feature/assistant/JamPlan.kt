package com.bbbjam.feature.assistant

import com.bbbjam.core.data.assistant.ProposedJam
import com.bbbjam.core.data.jams.JamsSnapshot
import java.time.LocalDate

/**
 * What creating a proposed jam would do, decided from the jams snapshot (`assistant-create-jam`, D2).
 * Pure: the caller passes today's date.
 */
sealed interface JamPlan {
    /** The snapshot is not read yet: nothing is drawn. */
    data object Unknown : JamPlan

    /** The jam cannot be created from the app, for [reason]. */
    data class Blocked(val reason: Reason, val upcomingDate: LocalDate? = null) : JamPlan {
        enum class Reason {
            /** The date is before today. */
            PAST,

            /** A jam (upcoming or past) already has the date. */
            EXISTS,

            /** The date is before the upcoming jam: it would put that jam on hold (orchestrator amendment). */
            EARLIER,
        }
    }

    /** The jam can be created; [heldBack] says whether it waits behind the upcoming jam. */
    data class Ready(val heldBack: JamHeldBack) : JamPlan
}

/** Whether a jam about to be created is the next one or waits behind the upcoming jam. */
sealed interface JamHeldBack {
    /** There is no upcoming jam: the new one becomes it. */
    data object None : JamHeldBack

    /** The upcoming jam on [upcomingDate] stays next; the new jam is held back until it passes. */
    data class NewWaits(val upcomingDate: LocalDate) : JamHeldBack
}

/**
 * The plan for [jam] against [snapshot] on [today]. A date earlier than the upcoming jam is blocked: it
 * would put the existing jam on hold, and the Sheet stays available for that rare case. A later date is
 * allowed and waits behind the upcoming jam.
 */
fun jamPlan(jam: ProposedJam, snapshot: JamsSnapshot?, today: LocalDate): JamPlan {
    val upcoming = snapshot?.upcoming
    return when {
        snapshot == null -> JamPlan.Unknown

        jam.date.isBefore(today) -> JamPlan.Blocked(JamPlan.Blocked.Reason.PAST)

        upcoming?.date == jam.date || snapshot.past.any { it.date == jam.date } ->
            JamPlan.Blocked(JamPlan.Blocked.Reason.EXISTS)

        upcoming != null && jam.date.isBefore(upcoming.date) ->
            JamPlan.Blocked(JamPlan.Blocked.Reason.EARLIER, upcoming.date)

        upcoming != null -> JamPlan.Ready(JamHeldBack.NewWaits(upcoming.date))

        else -> JamPlan.Ready(JamHeldBack.None)
    }
}
