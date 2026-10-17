package com.bbbjam.feature.pastjams.edit

import com.bbbjam.core.data.jams.JamEditPlan
import com.bbbjam.core.data.jams.JamFormInput
import com.bbbjam.core.data.jams.JamFormValues
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.jams.VenueProblem
import com.bbbjam.core.data.jams.jamEditPlan
import com.bbbjam.core.data.jams.normalizeVenue
import com.bbbjam.core.data.jams.parseFormDate
import com.bbbjam.core.data.jams.parseFormTime
import com.bbbjam.core.data.jams.venueProblem
import com.bbbjam.core.model.Jam
import java.time.LocalDate

/*
 * The "Editar la jam" dialog's rules (`admin-edit-delete-jam` C4), pure so a JVM test can check them. Parsing
 * is the "Nueva jam" form's; the date rule is `:core:data`'s `jamEditPlan` (an edit never changes which jam is
 * the upcoming one).
 */

/** Why a date cannot be saved: not a date, before today, taken, or it would change which jam is next. */
internal enum class JamEditDateProblem { FORMAT, PAST, EXISTS, LATER, EARLIER }

/**
 * The verdict on [JamFormInput] for [jam]. [values] is set only when every field is usable, the plan is Ready
 * and something differs from the jam, so `Guardar` is enabled exactly then. [otherDate] names the jam in the
 * way of a `LATER` or `EARLIER` date.
 */
internal data class JamEditCheck(
    val dateProblem: JamEditDateProblem?,
    val otherDate: LocalDate?,
    val timeInvalid: Boolean,
    val venueProblem: VenueProblem?,
    val dateChanged: Boolean,
    val values: JamFormValues?,
) {
    val canSave: Boolean
        get() = values != null
}

/** The check of [input] for [jam] against the raw [snapshot] on [today]. */
internal fun checkJamEdit(jam: Jam, input: JamFormInput, snapshot: JamsSnapshot?, today: LocalDate): JamEditCheck {
    val date = parseFormDate(input.date)
    val time = parseFormTime(input.time)
    val venue = normalizeVenue(input.venue)
    val plan = date?.let { jamEditPlan(jam.date, it, snapshot, today) }
    val blocked = plan as? JamEditPlan.Blocked
    val ready = plan as? JamEditPlan.Ready
    val dateProblem = editDateProblem(date, blocked)
    val usable = ready != null && date != null && time != null && venue != null
    val differs = date != jam.date || time != jam.startTime || venue != (normalizeVenue(jam.venue) ?: jam.venue)
    val values = if (usable &&
        differs
    ) {
        JamFormValues(checkNotNull(date), checkNotNull(time), checkNotNull(venue))
    } else {
        null
    }
    return JamEditCheck(
        dateProblem = dateProblem,
        otherDate = blocked?.takeIf {
            dateProblem == JamEditDateProblem.LATER ||
                dateProblem == JamEditDateProblem.EARLIER
        }
            ?.otherDate,
        timeInvalid = time == null,
        venueProblem = venueProblem(input.venue),
        dateChanged = ready?.dateChanged == true,
        values = values,
    )
}

private fun editDateProblem(date: LocalDate?, blocked: JamEditPlan.Blocked?): JamEditDateProblem? = when {
    date == null -> JamEditDateProblem.FORMAT

    else -> when (blocked?.reason) {
        JamEditPlan.Blocked.Reason.PAST -> JamEditDateProblem.PAST
        JamEditPlan.Blocked.Reason.EXISTS -> JamEditDateProblem.EXISTS
        JamEditPlan.Blocked.Reason.LATER -> JamEditDateProblem.LATER
        JamEditPlan.Blocked.Reason.EARLIER -> JamEditDateProblem.EARLIER
        JamEditPlan.Blocked.Reason.GONE, null -> null
    }
}
