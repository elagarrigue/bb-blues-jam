package com.bbbjam.core.data.jams

import java.time.LocalDate

/**
 * What editing a future jam's date would do, decided from the jams snapshot (`admin-edit-delete-jam` E9,
 * D-28). Pure: the caller passes today's date. An edit never changes which jam is the upcoming one; the
 * Sheet stays available for that rare case.
 */
sealed interface JamEditPlan {
    /** The snapshot is not read yet: nothing is drawn. */
    data object Unknown : JamEditPlan

    /** The date cannot be used, for [reason]; [otherDate] names the jam in the way, when there is one. */
    data class Blocked(val reason: Reason, val otherDate: LocalDate? = null) : JamEditPlan {
        enum class Reason {
            /** The jam is not the upcoming jam or a held-back one any more. */
            GONE,

            /** The new date is before today. */
            PAST,

            /** Another jam (upcoming, held back or past) already has the new date. */
            EXISTS,

            /** The upcoming jam would move past the first held-back jam, which would become the next one. */
            LATER,

            /** A held-back jam would move before the upcoming jam, which it would put on hold. */
            EARLIER,
        }
    }

    /** The edit can be made; [dateChanged] is false when the date stays. */
    data class Ready(val dateChanged: Boolean) : JamEditPlan
}

/**
 * The plan for moving the future jam on [jamDate] to [newDate] against [snapshot] (the raw view, drafts
 * included) on [today]. Held-back jams may be reordered among themselves.
 */
fun jamEditPlan(jamDate: LocalDate, newDate: LocalDate, snapshot: JamsSnapshot?, today: LocalDate): JamEditPlan {
    if (snapshot == null) return JamEditPlan.Unknown
    val upcoming = snapshot.upcoming
    val isUpcoming = upcoming?.date == jamDate
    val isHeldBack = snapshot.heldBack.any { it.date == jamDate }
    val firstHeldBack = snapshot.heldBack.minOfOrNull { it.date }
    val taken = (listOfNotNull(upcoming) + snapshot.heldBack + snapshot.past).any { it.date == newDate }
    return when {
        !isUpcoming && !isHeldBack -> JamEditPlan.Blocked(JamEditPlan.Blocked.Reason.GONE)

        newDate == jamDate -> JamEditPlan.Ready(dateChanged = false)

        newDate.isBefore(today) -> JamEditPlan.Blocked(JamEditPlan.Blocked.Reason.PAST)

        taken -> JamEditPlan.Blocked(JamEditPlan.Blocked.Reason.EXISTS)

        isUpcoming && firstHeldBack != null && newDate.isAfter(firstHeldBack) ->
            JamEditPlan.Blocked(JamEditPlan.Blocked.Reason.LATER, firstHeldBack)

        isHeldBack && upcoming != null && newDate.isBefore(upcoming.date) ->
            JamEditPlan.Blocked(JamEditPlan.Blocked.Reason.EARLIER, upcoming.date)

        else -> JamEditPlan.Ready(dateChanged = true)
    }
}
