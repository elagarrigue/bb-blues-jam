package com.bbbjam.feature.pastjams.admin

import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.ui.text.SpanishDateNames
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.pastjams.R
import com.bbbjam.feature.pastjams.create.toNewJam
import com.bbbjam.feature.pastjams.edit.manageUiModel
import com.bbbjam.feature.pastjams.edit.toDeleteConfirm
import com.bbbjam.feature.pastjams.edit.toEditForm
import com.bbbjam.feature.pastjams.jamDateLabel
import com.bbbjam.feature.pastjams.status.jamStatusUiModel
import com.bbbjam.feature.pastjams.status.publishUiModel

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
        upcomingHeading = UiText.res(R.string.past_jams_upcoming_heading),
        pastHeading = UiText.res(R.string.past_jams_past_heading),
        noFuture = if (future.isEmpty()) UiText.res(R.string.past_jams_no_future) else null,
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
        heldBack = if (isUpcoming) {
            null
        } else {
            UiText.res(
                R.string.past_jams_held_back,
                SpanishDateNames.sentence(upcoming.date),
            )
        },
        musicianNote = if (!isUpcoming && date == musiciansNext?.date && upcoming.status == JamStatus.DRAFT) {
            UiText.res(R.string.past_jams_visible_behind_draft, SpanishDateNames.sentence(upcoming.date))
        } else {
            null
        },
        controls = JamControlsUiModel(
            status = admin.jamStatusUiModel(this, isUpcoming),
            publish = if (!isUpcoming || status == JamStatus.PUBLISHED) null else admin.publishUiModel(this),
        ),
        manage = admin.manageUiModel(this),
    )

private fun statusNote(status: JamStatus): UiText = UiText.res(
    when (status) {
        JamStatus.DRAFT -> R.string.past_jams_draft_note
        JamStatus.CREATED -> R.string.past_jams_created_note
        JamStatus.PUBLISHED -> R.string.past_jams_published_note
    },
)

/** "12 temas", "1 tema", "Sin temas todavía", or the unreadable line; a withheld list says nothing. */
private fun songCountLabel(setlist: Setlist): UiText? = when (setlist) {
    is Setlist.Available -> if (setlist.songs.isEmpty()) {
        UiText.res(R.string.past_jams_no_songs_yet)
    } else {
        UiText.plural(R.plurals.past_jams_song_count, setlist.songs.size)
    }

    is Setlist.Unavailable -> UiText.res(R.string.past_jams_future_unavailable)

    Setlist.Withheld -> null
}
