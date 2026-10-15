package com.bbbjam.feature.pastjams

import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.ui.status.jamStatusBadge

/**
 * The admin's section of the Jams tab from the **raw** snapshot (D-26 admin view): the upcoming jam,
 * then the held-back ones, BORRADOR included. Only the upcoming jam gets controls: the server's
 * `setJamStatus` accepts no other jam, so a held-back card has the badge and a line.
 */
internal fun JamsSnapshot.toJamsAdmin(admin: JamsAdminState): JamsAdminUiModel {
    val future = (listOfNotNull(upcoming) + heldBack).sortedBy { it.date }
    return JamsAdminUiModel(
        upcomingHeading = PastJamsCopy.UPCOMING_HEADING,
        pastHeading = PastJamsCopy.PAST_HEADING,
        noFuture = if (future.isEmpty()) PastJamsCopy.NO_FUTURE else null,
        future = future.mapIndexed { index, jam -> jam.toFutureCard(admin, isUpcoming = index == 0) },
        newJam = toNewJam(admin.newJam, admin.today),
    )
}

private fun Jam.toFutureCard(admin: JamsAdminState, isUpcoming: Boolean) = FutureJamUiModel(
    date = date,
    dateLabel = jamDateLabel(date, startTime),
    venue = venue,
    badge = jamStatusBadge(status),
    note = statusNote(status),
    songCount = songCountLabel(setlist),
    heldBack = if (isUpcoming) null else PastJamsCopy.HELD_BACK,
    controls = if (isUpcoming) {
        JamControlsUiModel(
            status = admin.jamStatusUiModel(this),
            publish = if (status == JamStatus.PUBLISHED) null else admin.publishUiModel(this),
        )
    } else {
        null
    },
)

private fun statusNote(status: JamStatus): String = when (status) {
    JamStatus.DRAFT -> PastJamsCopy.DRAFT_NOTE
    JamStatus.CREATED -> PastJamsCopy.CREATED_NOTE
    JamStatus.PUBLISHED -> PastJamsCopy.PUBLISHED_NOTE
}

/** "12 temas", "1 tema", "Sin temas todavía", or the unreadable line; a withheld list says nothing. */
private fun songCountLabel(setlist: Setlist): String? = when (setlist) {
    is Setlist.Available -> if (setlist.songs.isEmpty()) {
        PastJamsCopy.NO_SONGS_YET
    } else {
        PastJamsCopy.songCount(setlist.songs.size)
    }

    is Setlist.Unavailable -> PastJamsCopy.FUTURE_UNAVAILABLE

    Setlist.Withheld -> null
}
