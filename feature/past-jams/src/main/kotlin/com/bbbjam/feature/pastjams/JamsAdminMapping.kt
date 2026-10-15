package com.bbbjam.feature.pastjams

import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist

/**
 * The admin's section of the Jams tab from the **raw** snapshot (D-26 admin view): the upcoming jam,
 * then the held-back ones, BORRADOR included. Every card gets the status actions and the edit and delete
 * actions; only the upcoming card gets the publication (only it has an app-built setlist).
 */
internal fun JamsSnapshot.toJamsAdmin(admin: JamsAdminState): JamsAdminUiModel {
    val future = (listOfNotNull(upcoming) + heldBack).sortedBy { it.date }
    // The jam musicians see as the next one (D-26): the first that is not a BORRADOR.
    val musiciansNext = future.firstOrNull { it.status != JamStatus.DRAFT }
    return JamsAdminUiModel(
        upcomingHeading = PastJamsCopy.UPCOMING_HEADING,
        pastHeading = PastJamsCopy.PAST_HEADING,
        noFuture = if (future.isEmpty()) PastJamsCopy.NO_FUTURE else null,
        future = future.mapIndexed { index, jam ->
            jam.toFutureCard(admin, future.first(), musiciansNext, index == 0)
        },
        newJam = toNewJam(admin.newJam, admin.today),
        editForm = toEditForm(admin, future),
        deleteConfirm = admin.toDeleteConfirm(future),
    )
}

private fun Jam.toFutureCard(admin: JamsAdminState, upcoming: Jam, musiciansNext: Jam?, isUpcoming: Boolean) =
    FutureJamUiModel(
        date = date,
        dateLabel = jamDateLabel(date, startTime),
        venue = venue,
        note = statusNote(status),
        songCount = songCountLabel(setlist),
        heldBack = if (isUpcoming) null else PastJamsCopy.heldBack(upcoming.date),
        musicianNote = if (!isUpcoming && date == musiciansNext?.date && upcoming.status == JamStatus.DRAFT) {
            PastJamsCopy.visibleBehindDraft(upcoming.date)
        } else {
            null
        },
        controls = JamControlsUiModel(
            status = admin.jamStatusUiModel(this, isUpcoming),
            publish = if (!isUpcoming || status == JamStatus.PUBLISHED) null else admin.publishUiModel(this),
        ),
        manage = admin.manageUiModel(this),
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
